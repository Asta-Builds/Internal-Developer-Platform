package com.idp.service;

import com.idp.domain.ServiceEntity;
import com.idp.dto.CopilotChatRequestDto;
import com.idp.dto.CopilotChatResponseDto;
import com.idp.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The RAG-flavoured copilot intent routing.
 */
@ExtendWith(MockitoExtension.class)
class CopilotServiceTest {

    @Mock private ServiceRepository serviceRepository;

    private CopilotService service;

    @BeforeEach
    void setUp() {
        service = new CopilotService(serviceRepository);
    }

    private CopilotChatResponseDto chat(String query) {
        return service.processQuery(CopilotChatRequestDto.builder().query(query).build());
    }

    @Test
    @DisplayName("payment intents answer with the payment gateway facts")
    void paymentIntent() {
        CopilotChatResponseDto response = chat("how do I integrate the payment API?");

        assertThat(response.getAnswer()).contains("srv-payment");
        assertThat(response.getSources()).contains("ServiceCatalog: srv-payment");
        assertThat(response.getConfidenceScore()).isEqualTo(0.95);
        verify(serviceRepository, never()).findAll();
    }

    @Test
    @DisplayName("scaffolding intents describe the available templates")
    void scaffoldIntent() {
        CopilotChatResponseDto response = chat("créer un nouveau microservice");

        assertThat(response.getAnswer()).contains("SPRING_BOOT", "ANGULAR", "GO", "PYTHON");
        assertThat(response.getSuggestedActions()).isNotEmpty();
    }

    @Test
    @DisplayName("go-stack intents answer with the notification service")
    void goStackIntent() {
        CopilotChatResponseDto response = chat("which services use Go?");

        assertThat(response.getAnswer()).contains("srv-notification");
    }

    @Test
    @DisplayName("feature flag intents describe the canary engine")
    void flagIntent() {
        CopilotChatResponseDto response = chat("how does the canary rollout work?");

        assertThat(response.getAnswer()).contains("NEW_PAYMENT_FLOW_V2");
        assertThat(response.getSources()).contains("Canary Evaluation Engine");
    }

    @Test
    @DisplayName("unmatched intents fall back to the catalogue count")
    void fallbackIntent() {
        ServiceEntity serviceRow = ServiceEntity.builder().id("srv-1").name("x").build();
        when(serviceRepository.findAll()).thenReturn(List.of(serviceRow, serviceRow));

        CopilotChatResponseDto response = chat("what is the weather?");

        assertThat(response.getAnswer()).contains("2 microservices");
        assertThat(response.getSources()).contains("IDP Knowledge Vector Store (RAG)");
    }

    @Test
    @DisplayName("null queries are treated as unmatched")
    void nullQueryFallsBack() {
        when(serviceRepository.findAll()).thenReturn(List.of());

        assertThat(chat(null).getAnswer()).isNotBlank();
    }

    @Test
    @DisplayName("suggested questions are stable")
    void suggestedQuestions() {
        assertThat(service.getSuggestedQuestions()).hasSize(4);
    }
}
