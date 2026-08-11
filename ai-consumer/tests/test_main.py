"""Unit tests for the IDP AI fraud consumer & inference engine.

Covers the pure scoring rules in ``evaluate_transaction_risk`` plus the HTTP
surface exposed by the FastAPI app.
"""
import json

import pytest

from main import (
    FraudEvaluationResult,
    TransactionPayload,
    app,
    evaluate_transaction_risk,
)


def base_payload(**overrides) -> dict:
    payload = {
        "transactionId": "txn-001",
        "customerId": "cust-1",
        "amount": 100.0,
        "currency": "EUR",
        "merchantCategory": "RETAIL",
        "serviceId": "srv-payment",
    }
    payload.update(overrides)
    return payload


class TestRiskScoring:
    def test_low_amount_stays_low_risk(self):
        result = evaluate_transaction_risk(base_payload(amount=100.0))

        assert result["riskScore"] == 0.05
        assert result["riskLevel"] == "LOW"
        assert result["blocked"] is False
        assert result["triggeredRules"] == []
        assert result["transactionId"] == "txn-001"
        assert result["modelVersion"] == "v2.1-ensemble-gradient-boost"

    def test_amount_above_1500_triggers_elevated_ticket(self):
        result = evaluate_transaction_risk(base_payload(amount=2000.0))

        assert result["riskLevel"] == "MEDIUM"
        assert result["blocked"] is False
        assert "RULE_ELEVATED_TICKET_SIZE" in result["triggeredRules"]

    def test_amount_above_5000_triggers_high_ticket_rule(self):
        result = evaluate_transaction_risk(base_payload(amount=9000.0))

        assert "RULE_AMOUNT_EXCEEDS_5000_EUR" in result["triggeredRules"]
        assert result["riskScore"] >= 0.5
        assert result["blocked"] is True

    def test_high_risk_merchant_categories(self):
        for merchant in ("GAMBLING", "CRYPTO_EXCHANGE", "OFFSHORE_REMITTANCE"):
            result = evaluate_transaction_risk(base_payload(merchantCategory=merchant))

            assert f"RULE_HIGH_RISK_MERCHANT_{merchant}" in result["triggeredRules"]

    def test_exotic_currency_adds_risk(self):
        result = evaluate_transaction_risk(base_payload(currency="XBT"))

        assert "RULE_EXOTIC_CURRENCY_XBT" in result["triggeredRules"]
        assert result["riskLevel"] in ("LOW", "MEDIUM")

    def test_supported_currencies_add_no_risk(self):
        for currency in ("EUR", "USD", "GBP"):
            result = evaluate_transaction_risk(base_payload(currency=currency))

            assert "RULE_EXOTIC_CURRENCY" not in "".join(result["triggeredRules"])

    def test_combined_rules_clamp_risk_to_1_0(self):
        result = evaluate_transaction_risk(
            base_payload(amount=99999.0, merchantCategory="CRYPTO_EXCHANGE", currency="XBT")
        )

        assert result["riskScore"] <= 1.0
        assert result["riskLevel"] == "CRITICAL"
        assert result["blocked"] is True

    def test_critical_threshold_blocks(self):
        result = evaluate_transaction_risk(base_payload(amount=8000.0, merchantCategory="GAMBLING"))

        assert result["riskLevel"] == "CRITICAL"
        assert result["blocked"] is True

    def test_defaults_for_missing_fields(self):
        result = evaluate_transaction_risk({"transactionId": "txn-min"})

        assert result["riskScore"] == 0.05
        assert result["riskLevel"] == "LOW"
        assert result["inferenceLatencyMs"] >= 1
        assert result["processedAt"] > 0


class TestHttpSurface:
    def test_health_endpoint(self):
        from fastapi.testclient import TestClient

        with TestClient(app) as client:
            response = client.get("/health")

        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "UP"
        assert body["service"] == "ai-fraud-consumer"
        assert "queue" in body

    def test_direct_evaluation_endpoint(self):
        from fastapi.testclient import TestClient

        with TestClient(app) as client:
            response = client.post(
                "/api/v1/fraud/evaluate",
                json=base_payload(amount=9000.0, merchantCategory="GAMBLING"),
            )

        assert response.status_code == 200
        body = response.json()
        assert body["blocked"] is True
        assert body["riskLevel"] == "CRITICAL"

    def test_direct_evaluation_rejects_invalid_payload(self):
        from fastapi.testclient import TestClient

        with TestClient(app) as client:
            response = client.post("/api/v1/fraud/evaluate", json={"amount": "not-a-number"})

        assert response.status_code == 422

    def test_metrics_endpoint(self):
        from fastapi.testclient import TestClient

        with TestClient(app) as client:
            response = client.get("/metrics")

        assert response.status_code == 200
        assert "idp_ai_fraud_inferences_total" in response.text


class TestModels:
    def test_transaction_payload_defaults(self):
        payload = TransactionPayload(transactionId="txn-1", amount=42.0)

        assert payload.customerId == "cust-anonymous"
        assert payload.currency == "EUR"
        assert payload.serviceId == "srv-payment"
        assert payload.merchantCategory == "RETAIL"

    def test_fraud_result_serialises_to_json(self):
        result = FraudEvaluationResult(
            transactionId="txn-1",
            riskScore=0.9,
            riskLevel="CRITICAL",
            blocked=True,
            triggeredRules=["RULE_A"],
            inferenceLatencyMs=3,
            processedAt=1234567890,
        )

        assert json.loads(result.model_dump_json())["blocked"] is True
