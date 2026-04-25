package com.featuretoggle.server.repository;

import com.featuretoggle.server.entity.FlagRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlagRuleRepository extends JpaRepository<FlagRuleEntity, Long> {
    
    List<FlagRuleEntity> findByFlagIdOrderByPriorityAsc(Long flagId);
    
    void deleteByFlagId(Long flagId);
}
