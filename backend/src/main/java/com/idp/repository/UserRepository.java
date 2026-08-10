package com.idp.repository;

import com.idp.domain.Role;
import com.idp.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByUsername(String username);

    /** Primary lookup during token reconciliation; stable across username changes. */
    Optional<UserEntity> findByKeycloakSubject(String keycloakSubject);

    List<UserEntity> findByTeam(String team);

    List<UserEntity> findByRole(Role role);
}
