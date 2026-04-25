package com.featuretoggle.server.repository;

import com.featuretoggle.server.entity.App;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppRepository extends JpaRepository<App, Long> {
    
    Optional<App> findByAppKey(String appKey);
    
    boolean existsByAppKey(String appKey);
}
