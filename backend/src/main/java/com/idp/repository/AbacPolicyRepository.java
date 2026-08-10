package com.idp.repository;

import com.idp.domain.AbacPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AbacPolicyRepository extends JpaRepository<AbacPolicyEntity, String> {

    List<AbacPolicyEntity> findByEnabledTrueOrderByPriorityAsc();

    List<AbacPolicyEntity> findAllByOrderByPriorityAsc();
}
