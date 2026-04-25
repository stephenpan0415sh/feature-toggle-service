package com.featuretoggle.server.repository;

import com.featuretoggle.server.entity.FeatureFlagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlagEntity, Long> {
    
    Optional<FeatureFlagEntity> findByAppIdAndEnvironmentAndFlagKey(
        Long appId, String environment, String flagKey);
    
    List<FeatureFlagEntity> findByAppIdAndEnvironment(Long appId, String environment);
    
    @Query("SELECT f.flagKey, f.version FROM FeatureFlagEntity f WHERE f.appId = :appId AND f.environment = :environment")
    List<Object[]> findFlagVersions(@Param("appId") Long appId, @Param("environment") String environment);
    
    boolean existsByAppIdAndEnvironmentAndFlagKey(Long appId, String environment, String flagKey);
}
