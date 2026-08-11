package com.idp.security;

import com.idp.domain.FeatureFlagEntity;
import com.idp.domain.ResourceType;
import com.idp.domain.ServiceEntity;
import com.idp.repository.FeatureFlagRepository;
import com.idp.repository.ServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Resolves resource-side attributes straight from the database — never from the
 * caller, which is what keeps ABAC decisions trustworthy.
 */
@ExtendWith(MockitoExtension.class)
class ResourceAttributeResolverTest {

    @Mock private ServiceRepository serviceRepository;
    @Mock private FeatureFlagRepository featureFlagRepository;

    private ResourceAttributeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ResourceAttributeResolver(serviceRepository, featureFlagRepository);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("returns a bare context when no resource id is supplied")
    void bareContextForNullId() {
        AccessContext context = resolver.resolve(ResourceType.SERVICE, null, null);

        assertThat(context.getResourceId()).isNull();
        assertThat(context.getOwnerTeam()).isNull();
        assertThat(context.getEnvironment()).isNull();
        assertThat(context.getCriticality()).isNull();
        assertThat(context.getRequestedRolloutPercent()).isNull();
    }

    @Test
    @DisplayName("loads owning team, environment and criticality from the service row")
    void resolvesServiceAttributes() {
        ServiceEntity service = ServiceEntity.builder()
                .id("srv-payment")
                .ownerTeam("Equipe Paiement")
                .environment("PROD")
                .criticality("TIER_1")
                .build();
        when(serviceRepository.findById("srv-payment")).thenReturn(Optional.of(service));

        AccessContext context = resolver.resolve(ResourceType.SERVICE, "srv-payment", 75);

        assertThat(context.getOwnerTeam()).isEqualTo("Equipe Paiement");
        assertThat(context.getEnvironment()).isEqualTo("PROD");
        assertThat(context.getCriticality()).isEqualTo("TIER_1");
        assertThat(context.getRequestedRolloutPercent()).isEqualTo(75);
    }

    @Test
    @DisplayName("resolves a feature flag by id and inherits its service attributes")
    void resolvesFlagById() {
        FeatureFlagEntity flag = FeatureFlagEntity.builder()
                .id("ff-1").key("PAY_V2").targetTeam("Equipe Paiement").serviceId("srv-payment").build();
        ServiceEntity service = ServiceEntity.builder()
                .id("srv-payment").environment("STAGING").criticality("STANDARD").build();

        when(featureFlagRepository.findById("ff-1")).thenReturn(Optional.of(flag));
        when(serviceRepository.findById("srv-payment")).thenReturn(Optional.of(service));

        AccessContext context = resolver.resolve(ResourceType.FEATURE_FLAG, "ff-1", 50);

        assertThat(context.getOwnerTeam()).isEqualTo("Equipe Paiement");
        assertThat(context.getEnvironment()).isEqualTo("STAGING");
        assertThat(context.getCriticality()).isEqualTo("STANDARD");
    }

    @Test
    @DisplayName("resolves a feature flag by business key when no row matches the id")
    void resolvesFlagByKey() {
        FeatureFlagEntity flag = FeatureFlagEntity.builder()
                .id("ff-2").key("MAIL_V3").targetTeam("Equipe Notifications").build();

        when(featureFlagRepository.findById("MAIL_V3")).thenReturn(Optional.empty());
        when(featureFlagRepository.findByKey("MAIL_V3")).thenReturn(Optional.of(flag));

        AccessContext context = resolver.resolve(ResourceType.FEATURE_FLAG, "MAIL_V3", null);

        assertThat(context.getOwnerTeam()).isEqualTo("Equipe Notifications");
    }

    @Test
    @DisplayName("keeps the left-most X-Forwarded-For entry as the client address")
    void resolvesClientIpFromForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        AccessContext context = resolver.resolve(ResourceType.SERVICE, "srv-payment", null);

        assertThat(context.getClientIp()).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("falls back to the socket address without a forwarded header")
    void fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        AccessContext context = resolver.resolve(ResourceType.SERVICE, "srv-payment", null);

        assertThat(context.getClientIp()).isEqualTo("10.0.0.9");
    }

    @Test
    @DisplayName("findService is empty for null ids")
    void findServiceHandlesNull() {
        assertThat(resolver.findService(null)).isEmpty();
    }
}
