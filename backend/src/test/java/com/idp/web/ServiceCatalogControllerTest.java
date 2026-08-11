package com.idp.web;

import com.idp.domain.ServiceEntity;
import com.idp.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Catalog listing, filtering and statistics.
 */
@ExtendWith(MockitoExtension.class)
class ServiceCatalogControllerTest {

    @Mock private ServiceRepository serviceRepository;

    private ServiceCatalogController controller;

    private final ServiceEntity payment = ServiceEntity.builder()
            .id("srv-payment").name("Payment Gateway Service")
            .description("Handles credit cards")
            .ownerTeam("Equipe Paiement").techStack("SPRING_BOOT").build();
    private final ServiceEntity catalog = ServiceEntity.builder()
            .id("srv-catalog").name("Product Catalog")
            .description("Search index")
            .ownerTeam("Equipe Catalogue").techStack("GO").build();

    @BeforeEach
    void setUp() {
        controller = new ServiceCatalogController(serviceRepository);
    }

    @Test
    @DisplayName("lists every service without filters")
    void listsAll() {
        when(serviceRepository.findAll()).thenReturn(List.of(payment, catalog));

        var body = controller.getServices(null, null, null).getBody();

        assertThat(body).containsExactly(payment, catalog);
    }

    @Test
    @DisplayName("filters by tech stack, team and search term")
    void filters() {
        when(serviceRepository.findAll()).thenReturn(List.of(payment, catalog));

        assertThat(controller.getServices(null, "GO", null).getBody()).containsExactly(catalog);
        assertThat(controller.getServices(null, "ALL", "Equipe Paiement").getBody()).containsExactly(payment);
        assertThat(controller.getServices("credit", null, null).getBody()).containsExactly(payment);
    }

    @Test
    @DisplayName("resolves a service by id or 404")
    void findById() {
        when(serviceRepository.findById("srv-payment")).thenReturn(Optional.of(payment));

        assertThat(controller.getServiceById("srv-payment").getBody()).isEqualTo(payment);
        assertThat(controller.getServiceById("srv-nope").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("lists distinct owner teams sorted")
    void ownerTeams() {
        when(serviceRepository.findAll()).thenReturn(List.of(payment, catalog));

        assertThat(controller.getOwnerTeams().getBody())
                .containsExactly("Equipe Catalogue", "Equipe Paiement");
    }

    @Test
    @DisplayName("aggregates catalogue statistics")
    void stats() {
        payment.setExposedApis(List.of(com.idp.domain.ApiEndpointEntity.builder().id("a1").build()));
        payment.setDependencies(List.of(com.idp.domain.DependencyEntity.builder().id("d1").build()));
        when(serviceRepository.findAll()).thenReturn(List.of(payment, catalog));

        var stats = controller.getCatalogStats().getBody();

        assertThat(stats.get("totalServices")).isEqualTo(2);
        assertThat(stats.get("totalApis")).isEqualTo(1L);
        assertThat(stats.get("totalDependencies")).isEqualTo(1L);
        assertThat(stats.get("totalOwnerTeams")).isEqualTo(2L);
        assertThat(stats.get("techStacks")).isInstanceOf(java.util.Map.class);
    }
}
