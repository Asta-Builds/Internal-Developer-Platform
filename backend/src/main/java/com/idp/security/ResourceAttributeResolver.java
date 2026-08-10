package com.idp.security;

import com.idp.domain.FeatureFlagEntity;
import com.idp.domain.ResourceType;
import com.idp.domain.ServiceEntity;
import com.idp.repository.FeatureFlagRepository;
import com.idp.repository.ServiceRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Loads the resource-side attributes an ABAC decision needs, straight from the
 * database. Deliberately server-side: the caller supplies only an identifier, never
 * the attributes that decide their own access.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceAttributeResolver {

    private final ServiceRepository serviceRepository;
    private final FeatureFlagRepository featureFlagRepository;

    public AccessContext resolve(ResourceType type, String resourceId, Integer requestedRolloutPercent) {
        AccessContext.AccessContextBuilder builder = AccessContext.builder()
                .resourceId(resourceId)
                .requestedRolloutPercent(requestedRolloutPercent)
                .clientIp(currentClientIp());

        if (resourceId == null) {
            return builder.build();
        }

        switch (type) {
            case SERVICE, OBSERVABILITY, COPILOT -> serviceRepository.findById(resourceId).ifPresent(service ->
                    builder.ownerTeam(service.getOwnerTeam())
                            .environment(service.getEnvironment())
                            .criticality(service.getCriticality()));

            case FEATURE_FLAG -> resolveFlag(resourceId).ifPresent(flag -> {
                builder.ownerTeam(flag.getTargetTeam());
                // A flag inherits the environment and criticality of the service it gates.
                if (flag.getServiceId() != null) {
                    serviceRepository.findById(flag.getServiceId()).ifPresent(service ->
                            builder.environment(service.getEnvironment())
                                    .criticality(service.getCriticality()));
                }
            });

            default -> { /* No resource-side attributes for this family. */ }
        }

        return builder.build();
    }

    /** Feature flags are addressed by id in some routes and by business key in others. */
    private Optional<FeatureFlagEntity> resolveFlag(String idOrKey) {
        Optional<FeatureFlagEntity> byId = featureFlagRepository.findById(idOrKey);
        return byId.isPresent() ? byId : featureFlagRepository.findByKey(idOrKey);
    }

    /** Owning team of a service, used when filtering collections by tenancy. */
    public Optional<ServiceEntity> findService(String serviceId) {
        return serviceId == null ? Optional.empty() : serviceRepository.findById(serviceId);
    }

    private String currentClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Left-most entry is the originating client.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
