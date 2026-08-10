package com.idp.web;

import com.idp.domain.*;
import com.idp.repository.AbacPolicyRepository;
import com.idp.repository.RolePermissionRepository;
import com.idp.repository.UserRepository;
import com.idp.security.*;
import com.idp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Administration of the platform's own authorization model.
 *
 * <p>Everything here edits platform tables. None of it touches Keycloak, which holds
 * no permission data to edit.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminRbacController {

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AbacPolicyRepository abacPolicyRepository;
    private final PolicyDecisionService policyDecisionService;
    private final ResourceAttributeResolver resourceAttributeResolver;
    private final AuditService auditService;

    // ---------------------------------------------------------------- users

    @GetMapping("/users")
    @PreAuthorize("hasPermission(null, 'ADMIN', 'READ')")
    public ResponseEntity<List<UserEntity>> listUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasPermission(#id, 'ADMIN', 'MANAGE')")
    @Transactional
    public ResponseEntity<UserEntity> updateUserRole(@PathVariable String id,
                                                     @RequestBody Map<String, String> payload) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + id));

        Role previous = user.getRole();
        Role updated = parseRole(payload.get("role"));
        user.setRole(updated);
        if (payload.containsKey("team")) {
            user.setTeam(payload.get("team"));
        }
        userRepository.save(user);
        policyDecisionService.invalidatePolicyCache();

        auditService.logAction(CurrentUser.require().toActorId(), "USER_ROLE_CHANGED", id,
                "Role changed from %s to %s".formatted(previous, updated));
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasPermission(#id, 'ADMIN', 'MANAGE')")
    @Transactional
    public ResponseEntity<UserEntity> updateUserStatus(@PathVariable String id,
                                                       @RequestBody Map<String, Boolean> payload) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + id));

        boolean active = Boolean.TRUE.equals(payload.getOrDefault("active", Boolean.TRUE));
        user.setActive(active);
        userRepository.save(user);

        auditService.logAction(CurrentUser.require().toActorId(),
                active ? "USER_ACTIVATED" : "USER_DEACTIVATED", id,
                "Local account access " + (active ? "restored" : "revoked"));
        return ResponseEntity.ok(user);
    }

    // ------------------------------------------------------- RBAC matrix

    @GetMapping("/roles")
    @PreAuthorize("hasPermission(null, 'ADMIN', 'READ')")
    public ResponseEntity<Map<String, Object>> listRoles() {
        Map<String, Object> response = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            response.put(role.name(), Map.of(
                    "rank", role.getRank(),
                    "permissions", rolePermissionRepository.findByRole(role)));
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/roles/{role}/permissions")
    @PreAuthorize("hasPermission(null, 'ADMIN', 'READ')")
    public ResponseEntity<List<RolePermissionEntity>> listRolePermissions(@PathVariable String role) {
        return ResponseEntity.ok(rolePermissionRepository.findByRole(parseRole(role)));
    }

    @PostMapping("/roles/{role}/permissions")
    @PreAuthorize("hasPermission(null, 'ADMIN', 'MANAGE')")
    @Transactional
    public ResponseEntity<RolePermissionEntity> grantPermission(@PathVariable String role,
                                                                @RequestBody Map<String, String> payload) {
        Role target = parseRole(role);
        ResourceType resourceType = parseEnum(ResourceType.class, payload.get("resourceType"), "resourceType");
        PermissionAction action = parseEnum(PermissionAction.class, payload.get("action"), "action");

        if (rolePermissionRepository.existsByRoleAndResourceTypeAndAction(target, resourceType, action)) {
            throw new IllegalArgumentException(
                    "Role %s already holds %s on %s".formatted(target, action, resourceType));
        }

        RolePermissionEntity granted = rolePermissionRepository.save(RolePermissionEntity.builder()
                .role(target)
                .resourceType(resourceType)
                .action(action)
                .description(payload.get("description"))
                .build());

        policyDecisionService.invalidatePolicyCache();
        auditService.logAction(CurrentUser.require().toActorId(), "RBAC_PERMISSION_GRANTED", granted.getId(),
                "Granted %s on %s to %s".formatted(action, resourceType, target));
        return ResponseEntity.ok(granted);
    }

    @DeleteMapping("/roles/{role}/permissions")
    @PreAuthorize("hasPermission(null, 'ADMIN', 'MANAGE')")
    @Transactional
    public ResponseEntity<Void> revokePermission(@PathVariable String role,
                                                 @RequestParam String resourceType,
                                                 @RequestParam String action) {
        Role target = parseRole(role);
        ResourceType type = parseEnum(ResourceType.class, resourceType, "resourceType");
        PermissionAction permissionAction = parseEnum(PermissionAction.class, action, "action");

        rolePermissionRepository.deleteByRoleAndResourceTypeAndAction(target, type, permissionAction);
        policyDecisionService.invalidatePolicyCache();

        auditService.logAction(CurrentUser.require().toActorId(), "RBAC_PERMISSION_REVOKED",
                target.name(), "Revoked %s on %s from %s".formatted(permissionAction, type, target));
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------ ABAC policies

    @GetMapping("/policies")
    @PreAuthorize("hasPermission(null, 'ADMIN', 'READ')")
    public ResponseEntity<List<AbacPolicyEntity>> listPolicies() {
        return ResponseEntity.ok(abacPolicyRepository.findAllByOrderByPriorityAsc());
    }

    @PostMapping("/policies")
    @PreAuthorize("hasPermission(null, 'ADMIN', 'MANAGE')")
    @Transactional
    public ResponseEntity<AbacPolicyEntity> createPolicy(@RequestBody AbacPolicyEntity policy) {
        policy.setId(null);
        AbacPolicyEntity saved = abacPolicyRepository.save(policy);
        policyDecisionService.invalidatePolicyCache();

        auditService.logAction(CurrentUser.require().toActorId(), "ABAC_POLICY_CREATED", saved.getId(),
                "Created policy '%s' (%s %s on %s)".formatted(
                        saved.getName(), saved.getEffect(), saved.getAction(), saved.getResourceType()));
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/policies/{id}")
    @PreAuthorize("hasPermission(#id, 'ADMIN', 'MANAGE')")
    @Transactional
    public ResponseEntity<AbacPolicyEntity> togglePolicy(@PathVariable String id,
                                                         @RequestBody Map<String, Object> payload) {
        AbacPolicyEntity policy = abacPolicyRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Policy not found: " + id));

        if (payload.get("enabled") instanceof Boolean enabled) {
            policy.setEnabled(enabled);
        }
        if (payload.get("priority") instanceof Number priority) {
            policy.setPriority(priority.intValue());
        }
        abacPolicyRepository.save(policy);
        policyDecisionService.invalidatePolicyCache();

        auditService.logAction(CurrentUser.require().toActorId(), "ABAC_POLICY_UPDATED", id,
                "Policy '%s' enabled=%s priority=%s".formatted(
                        policy.getName(), policy.isEnabled(), policy.getPriority()));
        return ResponseEntity.ok(policy);
    }

    @DeleteMapping("/policies/{id}")
    @PreAuthorize("hasPermission(#id, 'ADMIN', 'MANAGE')")
    @Transactional
    public ResponseEntity<Void> deletePolicy(@PathVariable String id) {
        AbacPolicyEntity policy = abacPolicyRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Policy not found: " + id));
        abacPolicyRepository.delete(policy);
        policyDecisionService.invalidatePolicyCache();

        auditService.logAction(CurrentUser.require().toActorId(), "ABAC_POLICY_DELETED", id,
                "Deleted policy '" + policy.getName() + "'");
        return ResponseEntity.noContent().build();
    }

    /**
     * Dry-runs a decision for a <em>stored</em> user against real resource attributes.
     *
     * <p>Unlike the endpoint this replaces, the caller cannot assert their own role:
     * the subject is looked up by id and the resource attributes are read from the
     * database. Evaluation is side-effect free, so a simulation never writes an audit
     * denial.
     */
    @PostMapping("/policies/simulate")
    @PreAuthorize("hasPermission(null, 'ADMIN', 'MANAGE')")
    public ResponseEntity<Map<String, Object>> simulate(@RequestBody Map<String, Object> payload) {
        String userId = String.valueOf(payload.get("userId"));
        UserEntity subject = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        ResourceType resourceType = parseEnum(ResourceType.class,
                String.valueOf(payload.get("resourceType")), "resourceType");
        PermissionAction action = parseEnum(PermissionAction.class,
                String.valueOf(payload.get("action")), "action");

        String resourceId = payload.get("resourceId") != null
                ? String.valueOf(payload.get("resourceId")) : null;
        Integer rollout = payload.get("rolloutPercent") instanceof Number n ? n.intValue() : null;

        AccessContext context = resourceAttributeResolver.resolve(resourceType, resourceId, rollout);
        AuthenticatedUser simulated = AuthenticatedUser.from(subject, null);
        AccessDecision decision = policyDecisionService.evaluate(simulated, resourceType, action, context);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("granted", decision.isGranted());
        response.put("stage", decision.getStage());
        response.put("reason", decision.getReason());
        response.put("policyId", decision.getPolicyId());
        response.put("subject", Map.of(
                "id", subject.getId(),
                "username", subject.getUsername(),
                "role", subject.getRole(),
                "team", subject.getTeam() != null ? subject.getTeam() : ""));
        response.put("resource", Map.of(
                "type", resourceType,
                "id", resourceId != null ? resourceId : "",
                "ownerTeam", context.getOwnerTeam() != null ? context.getOwnerTeam() : "",
                "environment", context.getEnvironment() != null ? context.getEnvironment() : "",
                "criticality", context.getCriticality() != null ? context.getCriticality() : ""));
        response.put("action", action);
        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------ helpers

    private Role parseRole(String value) {
        return parseEnum(Role.class, value, "role");
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown %s '%s'. Expected one of %s"
                    .formatted(field, value, java.util.Arrays.toString(type.getEnumConstants())));
        }
    }
}
