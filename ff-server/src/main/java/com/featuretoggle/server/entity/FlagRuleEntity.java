package com.featuretoggle.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Flag Rule entity storing JSON conditions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "flag_rule")
public class FlagRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flag_id", nullable = false)
    private Long flagId;

    @Column(nullable = false)
    private Integer priority;

    @Column(columnDefinition = "JSON", nullable = false)
    private String conditions;

    @Column(name = "rule_default_enabled", nullable = false)
    private Boolean ruleDefaultEnabled;

    @Column(length = 512)
    private String description;
}
