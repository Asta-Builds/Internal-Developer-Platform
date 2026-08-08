package com.idp.repository;

import com.idp.domain.ScaffoldJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScaffoldJobRepository extends JpaRepository<ScaffoldJobEntity, String> {
    Optional<ScaffoldJobEntity> findByProjectId(String projectId);
}
