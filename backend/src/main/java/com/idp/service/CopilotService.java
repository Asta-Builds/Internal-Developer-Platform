package com.idp.service;

import com.idp.domain.ServiceEntity;
import com.idp.dto.CopilotChatRequestDto;
import com.idp.dto.CopilotChatResponseDto;
import com.idp.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CopilotService {

    private final ServiceRepository serviceRepository;

    public CopilotChatResponseDto processQuery(CopilotChatRequestDto request) {
        String q = request.getQuery() != null ? request.getQuery().toLowerCase() : "";

        String answer;
        List<String> sources = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        double confidence = 0.95;

        if (q.contains("paiement") || q.contains("payment") || q.contains("charge") || q.contains("stripe")) {
            answer = "Le service **Payment Gateway Service** (`srv-payment`) gère le traitement des cartes de crédit, remboursements et la compensation. Il expose l'API `POST /api/v1/payments/charge` et possède la Feature Flag `NEW_PAYMENT_FLOW_V2` (Canary Rollout 50%-75%).\n\nPropriétaire: **Équipe Paiement** | Tech Stack: **SPRING_BOOT**";
            sources.addAll(List.of("ServiceCatalog: srv-payment", "Swagger API Spec: /api/v1/payments/charge", "FeatureFlag: NEW_PAYMENT_FLOW_V2"));
            actions.addAll(List.of("Consulter la spec OpenAPI /api/v1/payments/charge", "Ajuster le canary rollout de NEW_PAYMENT_FLOW_V2"));
        } else if (q.contains("scaffold") || q.contains("créer") || q.contains("nouveau") || q.contains("template")) {
            answer = "Pour générer un nouveau microservice via le **Scaffolder MVP**, vous pouvez soumettre une requête `POST /api/scaffold` avec les templates disponibles:\n- **SPRING_BOOT** (Java 17, Maven, JPA)\n- **ANGULAR** (Angular 17 Standalone, Signals)\n- **GO** (Gin Gonic, Dockerfile)\n- **PYTHON** (FastAPI, Pydantic)\n\nLe Scaffolder provisionne le dépôt Git, le squelette CI/CD et l'enregistre automatiquement dans le catalogue IDP.";
            sources.addAll(List.of("IDP Scaffolder Docs", "Template Registry: SPRING_BOOT / ANGULAR / GO"));
            actions.addAll(List.of("Lancer un scaffolding Spring Boot", "Voir les builds CI/CD récents"));
        } else if (q.contains("go") || q.contains("golang") || q.contains("notification")) {
            answer = "Le microservice **Notification Dispatcher** (`srv-notification`) est développé en **GO**. Il prend en charge les envois SMS, Email et Push, et dépend de la Feature Flag `WHATSAPP_NOTIF_PROVIDER`.";
            sources.addAll(List.of("ServiceCatalog: srv-notification", "Tech Stack Filter: GO"));
            actions.addAll(List.of("Voir les dépendances de srv-notification", "Consulter l'endpoint /api/v1/notifications/send"));
        } else if (q.contains("flag") || q.contains("canary") || q.contains("rollout")) {
            answer = "Le moteur de **Feature Flags & Canary Release** évalue les règles par utilisateur avec l'algorithme de hachage `(userId + '_' + key).hashCode() % 100`. Trois flags sont actuellement configurés:\n1. `NEW_PAYMENT_FLOW_V2` (75% Rollout)\n2. `ELASTICSEARCH_SEARCH_V3` (0% Rollout)\n3. `WHATSAPP_NOTIF_PROVIDER` (10% Rollout)";
            sources.addAll(List.of("FeatureFlagRepository", "Canary Evaluation Engine"));
            actions.addAll(List.of("Modifier le pourcentage de rollout", "Tester la résolution pour un user_id"));
        } else {
            List<ServiceEntity> allServices = serviceRepository.findAll();
            answer = String.format("IDP Copilot a analysé le catalogue d'entreprise (%d microservices enregistrés). Vous pouvez poser des questions sur les contrats d'API, l'état de santé Prometheus, les templates de scaffolding ou l'évaluation des Feature Flags.", allServices.size());
            sources.add("IDP Knowledge Vector Store (RAG)");
            actions.addAll(List.of("Comment intégrer l'API de Paiement ?", "Quels sont les templates de scaffolding disponibles ?"));
        }

        return CopilotChatResponseDto.builder()
                .answer(answer)
                .sources(sources)
                .confidenceScore(confidence)
                .suggestedActions(actions)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public List<String> getSuggestedQuestions() {
        return List.of(
                "Comment intégrer l'API de Paiement ?",
                "Quels sont les templates de scaffolding disponibles ?",
                "Comment fonctionne le Canary Rollout des Feature Flags ?",
                "Quels microservices utilisent la stack GO ?"
        );
    }
}
