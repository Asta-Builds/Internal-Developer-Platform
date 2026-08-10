package com.idp.repository;

import com.idp.domain.PermissionAction;
import com.idp.domain.ResourceType;
import com.idp.domain.Role;
import com.idp.domain.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, String> {

    List<RolePermissionEntity> findByRole(Role role);

    boolean existsByRoleAndResourceTypeAndAction(Role role, ResourceType resourceType, PermissionAction action);

    void deleteByRoleAndResourceTypeAndAction(Role role, ResourceType resourceType, PermissionAction action);
}
