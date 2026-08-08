package com.idp.repository;

import com.idp.domain.FeatureFlagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlagEntity, String> {
    Optional<FeatureFlagEntity> findByKey(String key);
    List<FeatureFlagEntity> findByServiceId(String serviceId);
}
