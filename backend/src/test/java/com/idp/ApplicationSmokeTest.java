package com.idp;

import com.idp.rag.RagRetrievalService;
import com.idp.repository.RagChunkRepository;
import com.idp.repository.ServiceRepository;
import com.idp.repository.UserRepository;
import com.idp.security.IdpPermissionEvaluator;
import com.idp.service.ScaffoldingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real application context.
 *
 * <p>Every other test in this suite is a unit test over mocks, which means a wiring
 * mistake — a missing bean, an ambiguous injection, a broken migration, a converter
 * Hibernate cannot resolve — survives a fully green build and only surfaces on a
 * real startup. This test is the one that fails instead.
 *
 * <p>It needs no external services by design, and that is a property of the
 * application rather than of the test: the JWT decoder is built from the JWK set URI
 * so it never calls Keycloak at startup, caching is Caffeine, and the datasource
 * defaults to H2 with the Flyway chain applied over it. Only the AMQP consumer has
 * to be told to stand down, through the toggle it already exposes.
 */
@SpringBootTest(properties = {
        // The @RabbitListener is the one bean that would dial out on startup.
        "idp.rabbitmq.consumer.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        // Exercise the secure-by-default path: unknown principals are refused rather
        // than auto-provisioned, matching production.
        "idp.security.auto-provision-unknown-users=false",
        // Also the regression assertion below — this key used to be read via
        // Long.getLong, which ignores Spring configuration entirely.
        "idp.scaffold.step-delay-ms=0"
})
class ApplicationSmokeTest {

    @Autowired private RagChunkRepository chunkRepository;
    @Autowired private RagRetrievalService retrievalService;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private IdpPermissionEvaluator permissionEvaluator;
    @Autowired private ScaffoldingService scaffoldingService;

    @Test
    @DisplayName("the application context starts with every layer wired")
    void contextStarts() {
        // Security, persistence and authorization all resolve — the three places a
        // wiring break would otherwise reach production.
        assertThat(jwtDecoder).isNotNull();
        assertThat(permissionEvaluator).isNotNull();
        assertThat(serviceRepository.count()).isPositive();
        assertThat(userRepository.count()).isPositive();
    }

    @Test
    @DisplayName("startup indexes the seeded documentation into retrievable chunks")
    void startupIndexesTheCorpus() {
        // DataInitializer runs ingestion on boot. This asserts the whole path works
        // against a real database and a real migration chain: Flyway seeds the
        // documents, the chunker splits them, the embedding model vectorises them,
        // and the FloatVectorConverter round-trips through the column.
        assertThat(chunkRepository.count())
                .as("chunks written by startup ingestion")
                .isPositive();

        assertThat(chunkRepository.findAllEmbeddedWithDocument())
                .allSatisfy(chunk -> {
                    assertThat(chunk.getEmbedding()).isNotEmpty();
                    assertThat(chunk.getDocument()).isNotNull();
                });
    }

    @Test
    @DisplayName("scaffolder step delay is configurable through Spring properties")
    void scaffoldStepDelayBindsFromConfiguration() {
        // Long.getLong resolves JVM system properties, not the environment, so the
        // previous form silently slept the 800ms default no matter what
        // application.yml or IDP_SCAFFOLD_STEP_DELAY_MS said.
        assertThat(ReflectionTestUtils.getField(scaffoldingService, "stepDelayMs"))
                .as("idp.scaffold.step-delay-ms bound from the test property source")
                .isEqualTo(0L);
    }

    @Test
    @DisplayName("the Copilot retrieves from the corpus it indexed at startup")
    void retrievalWorksAgainstTheRealCorpus() {
        List<RagRetrievalService.RetrievedChunk> results =
                retrievalService.retrieve("Comment intégrer l'API de Paiement ?", List.of());

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).documentTitle()).isNotBlank();
        assertThat(results.get(0).score()).isPositive();
    }
}
