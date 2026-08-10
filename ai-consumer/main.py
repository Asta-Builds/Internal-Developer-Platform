import os
import json
import time
import asyncio
import logging
from contextlib import asynccontextmanager
from typing import List, Optional
from pydantic import BaseModel
from fastapi import FastAPI, HTTPException
import aio_pika
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from starlette.responses import Response

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("ai-consumer")

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASSWORD = os.getenv("RABBITMQ_PASSWORD", "guest")
INCOMING_QUEUE = os.getenv("INCOMING_QUEUE", "fraud.analysis.queue")
OUTGOING_QUEUE = os.getenv("OUTGOING_QUEUE", "fraud.result.queue")
EXCHANGE_NAME = os.getenv("EXCHANGE_NAME", "idp.direct.exchange")

# Prometheus Metrics
INFERENCE_COUNT = Counter("idp_ai_fraud_inferences_total", "Total fraud inferences performed", ["risk_level"])
INFERENCE_LATENCY = Histogram("idp_ai_fraud_inference_latency_seconds", "Inference latency in seconds")

class TransactionPayload(BaseModel):
    transactionId: str
    customerId: Optional[str] = "cust-anonymous"
    amount: float
    currency: Optional[str] = "EUR"
    serviceId: Optional[str] = "srv-payment"
    merchantCategory: Optional[str] = "RETAIL"
    ipAddress: Optional[str] = "127.0.0.1"
    deviceFingerprint: Optional[str] = "device-default"
    timestamp: Optional[int] = int(time.time() * 1000)

class FraudEvaluationResult(BaseModel):
    transactionId: str
    riskScore: float
    riskLevel: str
    blocked: bool = False
    triggeredRules: List[str]
    modelVersion: str = "v2.1-ensemble-gradient-boost"
    inferenceLatencyMs: int
    processedAt: int

def evaluate_transaction_risk(data: dict) -> dict:
    start_time = time.time()
    amount = float(data.get("amount", 0.0))
    currency = data.get("currency", "EUR")
    merchant = data.get("merchantCategory", "RETAIL")
    
    risk_score = 0.05
    triggered_rules = []

    # Rule 1: High Transaction Velocity / Amount Threshold
    if amount > 5000:
        risk_score += 0.45
        triggered_rules.append("RULE_AMOUNT_EXCEEDS_5000_EUR")
    elif amount > 1500:
        risk_score += 0.20
        triggered_rules.append("RULE_ELEVATED_TICKET_SIZE")

    # Rule 2: High Risk Merchant Categories
    if merchant in ["GAMBLING", "CRYPTO_EXCHANGE", "OFFSHORE_REMITTANCE"]:
        risk_score += 0.35
        triggered_rules.append(f"RULE_HIGH_RISK_MERCHANT_{merchant}")

    # Rule 3: Cross-border conversion risk
    if currency not in ["EUR", "USD", "GBP"]:
        risk_score += 0.15
        triggered_rules.append(f"RULE_EXOTIC_CURRENCY_{currency}")

    # Bounded risk score
    risk_score = min(1.0, max(0.01, round(risk_score, 3)))
    
    if risk_score >= 0.75:
        risk_level = "CRITICAL"
        blocked = True
    elif risk_score >= 0.50:
        risk_level = "HIGH"
        blocked = True
    elif risk_score >= 0.25:
        risk_level = "MEDIUM"
        blocked = False
    else:
        risk_level = "LOW"
        blocked = False

    latency_ms = int((time.time() - start_time) * 1000)
    INFERENCE_COUNT.labels(risk_level=risk_level).inc()
    INFERENCE_LATENCY.observe(time.time() - start_time)

    return {
        "transactionId": data.get("transactionId", "unknown"),
        "riskScore": risk_score,
        "riskLevel": risk_level,
        "blocked": blocked,
        "triggeredRules": triggered_rules,
        "modelVersion": "v2.1-ensemble-gradient-boost",
        "inferenceLatencyMs": max(1, latency_ms),
        "processedAt": int(time.time() * 1000)
    }

async def rabbitmq_consumer_worker():
    while True:
        try:
            logger.info(f"Connecting to RabbitMQ broker at {RABBITMQ_HOST}:{RABBITMQ_PORT}...")
            connection = await aio_pika.connect_robust(
                host=RABBITMQ_HOST,
                port=RABBITMQ_PORT,
                login=RABBITMQ_USER,
                password=RABBITMQ_PASSWORD
            )

            async with connection:
                channel = await connection.channel()
                await channel.set_qos(prefetch_count=50)

                # Ensure exchange and queues
                exchange = await channel.declare_exchange(EXCHANGE_NAME, aio_pika.ExchangeType.DIRECT, durable=True)
                incoming_queue = await channel.declare_queue(INCOMING_QUEUE, durable=True)
                outgoing_queue = await channel.declare_queue(OUTGOING_QUEUE, durable=True)
                
                await incoming_queue.bind(exchange, routing_key="fraud.analysis.routing.key")
                await outgoing_queue.bind(exchange, routing_key="fraud.result.routing.key")

                logger.info(f"Subscribed to queue '{INCOMING_QUEUE}'. Awaiting incoming transactions...")

                async with incoming_queue.iterator() as queue_iter:
                    async for message in queue_iter:
                        async with message.process():
                            try:
                                payload = json.loads(message.body.decode())
                                logger.info(f"[AI Consumer] Processing txn: {payload.get('transactionId')}")
                                
                                result = evaluate_transaction_risk(payload)
                                
                                # Publish result back
                                await exchange.publish(
                                    aio_pika.Message(
                                        body=json.dumps(result).encode(),
                                        content_type="application/json",
                                        delivery_mode=aio_pika.DeliveryMode.PERSISTENT
                                    ),
                                    routing_key="fraud.result.routing.key"
                                )
                                logger.info(f"[AI Consumer] Result published: Score={result['riskScore']} Level={result['riskLevel']}")
                            except Exception as ex:
                                logger.error(f"Error evaluating message: {ex}")

        except asyncio.CancelledError:
            logger.info("RabbitMQ consumer task cancelled.")
            break
        except Exception as e:
            logger.warning(f"RabbitMQ connection lost ({e}). Retrying in 5 seconds...")
            await asyncio.sleep(5)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Start consumer in background task
    consumer_task = asyncio.create_task(rabbitmq_consumer_worker())
    yield
    consumer_task.cancel()
    try:
        await consumer_task
    except asyncio.CancelledError:
        pass

app = FastAPI(
    title="IDP AI Fraud Consumer & Inference Engine",
    description="High-throughput asynchronous AI Consumer for RabbitMQ transaction streams",
    version="2.1.0",
    lifespan=lifespan
)

@app.get("/health")
def health_check():
    return {
        "status": "UP",
        "service": "ai-fraud-consumer",
        "rabbitmq_host": RABBITMQ_HOST,
        "queue": INCOMING_QUEUE,
        "timestamp": int(time.time() * 1000)
    }

@app.post("/api/v1/fraud/evaluate")
def evaluate_direct(payload: TransactionPayload):
    return evaluate_transaction_risk(payload.model_dump())

@app.get("/metrics")
def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
