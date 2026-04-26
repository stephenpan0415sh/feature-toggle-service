package com.featuretoggle.server.service;

import com.featuretoggle.common.model.EvaluationDetail;
import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.common.model.Rule;
import com.featuretoggle.common.model.UserContext;
import com.featuretoggle.server.entity.App;
import com.featuretoggle.server.entity.FeatureFlagEntity;
import com.featuretoggle.server.entity.FlagRuleEntity;
import com.featuretoggle.server.repository.AppRepository;
import com.featuretoggle.server.repository.FeatureFlagRepository;
import com.featuretoggle.server.repository.FlagRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EvaluationService
 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private FeatureFlagRepository flagRepository;

    @Mock
    private FlagRuleRepository ruleRepository;

    @Mock
    private AppRepository appRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FlagCacheService flagCacheService;

    @Mock
    private com.featuretoggle.server.config.MetricsCollector metricsCollector;

    @InjectMocks
    private EvaluationService evaluationService;

    private App testApp;
    private FeatureFlagEntity testFlagEntity;
    private FeatureFlag testFlag;
    private UserContext testUserContext;

    @BeforeEach
    void setUp() {
        // Setup test app
        testApp = App.builder()
            .id(1L)
            .appKey("test-app")
            .name("Test App")
            .build();

        // Setup test flag entity
        testFlagEntity = FeatureFlagEntity.builder()
            .id(1L)
            .appId(1L)
            .flagKey("test-flag")
            .name("Test Flag")
            .environment("prod")
            .status(1)
            .defaultValue("false")
            .version(1L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // Setup test flag with rules
        Rule rule = Rule.builder()
            .id("rule_1")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .actionValue("true")
            .build();

        testFlag = FeatureFlag.builder()
            .id(1L)
            .appId(1L)
            .flagKey("test-flag")
            .name("Test Flag")
            .environment("prod")
            .status(1)
            .defaultValue("false")
            .version(1L)
            .releaseVersion("v1.2.3")
            .rules(List.of(rule))
            .build();

        // Setup test user context with region
        testUserContext = new UserContext("user123", Map.of("region", "cn-east"));
    }

    @Test
    void evaluateFlag_shouldReturnFromCache_whenCacheHit() throws Exception {
        // Given
        when(flagCacheService.getFromCache("test-app", "test-flag", "prod"))
            .thenReturn(testFlag);
        when(flagCacheService.hasFlagInCache("test-app", "test-flag", "prod"))
            .thenReturn(true);

        // When
        EvaluationDetail result = evaluationService.evaluateFlag(
            "test-app", "test-flag", testUserContext, "prod");

        // Then
        assertNotNull(result);
        assertEquals("test-flag", result.flagKey());
        verify(flagCacheService, times(1)).getFromCache("test-app", "test-flag", "prod");
        verify(flagCacheService, times(1)).hasFlagInCache("test-app", "test-flag", "prod");
        verify(flagCacheService, never()).saveToCache(any(), any(), any());
    }

    @Test
    void evaluateFlag_shouldLoadFromDatabase_whenCacheMiss() throws Exception {
        // Given
        when(flagCacheService.getFromCache("test-app", "test-flag", "prod"))
            .thenReturn(null);
        when(appRepository.findByAppKey("test-app"))
            .thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironmentAndFlagKey(1L, "prod", "test-flag"))
            .thenReturn(Optional.of(testFlagEntity));

        // When
        EvaluationDetail result = evaluationService.evaluateFlag(
            "test-app", "test-flag", testUserContext, "prod");

        // Then
        assertNotNull(result);
        assertEquals("test-flag", result.flagKey());
        verify(flagCacheService, times(1)).saveToCache(eq("test-app"), any(FeatureFlag.class), eq("prod"));
    }

    @Test
    void evaluateFlag_shouldReturnNotFoundDetail_whenFlagNotExists() {
        // Given
        when(flagCacheService.getFromCache("test-app", "nonexistent-flag", "prod"))
            .thenReturn(null);
        when(appRepository.findByAppKey("test-app"))
            .thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironmentAndFlagKey(1L, "prod", "nonexistent-flag"))
            .thenReturn(Optional.empty());

        // When
        EvaluationDetail result = evaluationService.evaluateFlag(
            "test-app", "nonexistent-flag", testUserContext, "prod");

        // Then
        assertNotNull(result);
        assertEquals("nonexistent-flag", result.flagKey());
        assertFalse(result.enabled());
        assertEquals(EvaluationDetail.EvaluationReason.DEFAULT, result.reason());
    }

    @Test
    void batchEvaluate_shouldEvaluateMultipleFlags() throws Exception {
        // Given
        List<String> flagKeys = List.of("flag1", "flag2");
        when(flagCacheService.getFromCache(anyString(), anyString(), anyString()))
            .thenReturn(testFlag);
        // Fallback mocks in case cache miss or implementation loads from DB
        when(appRepository.findByAppKey("test-app"))
            .thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironmentAndFlagKey(anyLong(), anyString(), anyString()))
            .thenReturn(Optional.of(testFlagEntity));
        when(ruleRepository.findByFlagIdOrderByPriorityAsc(anyLong()))
            .thenReturn(List.of());

        // When
        Map<String, EvaluationDetail> results = evaluationService.batchEvaluate(
            "test-app", flagKeys, testUserContext, "prod");

        // Then
        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.containsKey("flag1"));
        assertTrue(results.containsKey("flag2"));
    }

    @Test
    void getAllFlagsIncremental_shouldReturnEmptyList_whenNoChanges() {
        // Given
        when(flagCacheService.getGlobalVersion("test-app", "prod"))
            .thenReturn(100L);

        // When
        List<FeatureFlag> result = evaluationService.getAllFlagsIncremental(
            "test-app", "prod", 100L);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getAllFlagsIncremental_shouldReturnAllFlags_whenFirstSync() throws Exception {
        // Given
        when(flagCacheService.getAppFlagsFromCache("test-app", "prod"))
            .thenReturn(null);
        when(flagCacheService.getGlobalVersion("test-app", "prod"))
            .thenReturn(100L);
        when(appRepository.findByAppKey("test-app"))
            .thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironment(1L, "prod"))
            .thenReturn(List.of(testFlagEntity));
        when(ruleRepository.findByFlagIdOrderByPriorityAsc(1L))
            .thenReturn(List.of());

        // When
        List<FeatureFlag> result = evaluationService.getAllFlagsIncremental(
            "test-app", "prod", null);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(flagCacheService).saveAppFlagsToCache(eq("test-app"), anyList(), eq("prod"));
    }

    @Test
    void getFlagVersions_shouldReturnVersionMap() {
        // Given
        Map<String, Long> expectedVersions = Map.of("test-flag", 1L);
        when(flagCacheService.getAllFlagVersions("test-app", "prod"))
            .thenReturn(expectedVersions);

        // When
        Map<String, Long> result = evaluationService.getFlagVersions("test-app", "prod");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get("test-flag"));
    }

    @Test
    void publishChange_shouldUpdateCacheAndSendMessage_whenFlagExists() {
        // Given
        when(appRepository.findByAppKey("test-app"))
            .thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironmentAndFlagKey(1L, "prod", "test-flag"))
            .thenReturn(Optional.of(testFlagEntity));
        when(ruleRepository.findByFlagIdOrderByPriorityAsc(1L))
            .thenReturn(List.of());

        // When
        evaluationService.publishChange("test-app", "test-flag", "prod", 2L);

        // Then
        verify(flagCacheService).saveToCache(eq("test-app"), any(FeatureFlag.class), eq("prod"));
        verify(flagCacheService).updateAppFlagsToCache(eq("test-app"), any(FeatureFlag.class), eq("prod"));
        verify(redisTemplate).convertAndSend(anyString(), anyString());
    }

    @Test
    void publishDelete_shouldInvalidateCacheAndSendDeleteMessage() {
        // Given
        String appKey = "test-app";
        String flagKey = "deleted-flag";
        String environment = "prod";
        Long version = 5L;

        // When
        evaluationService.publishDelete(appKey, flagKey, environment, version);

        // Then
        // Verify cache is invalidated
        verify(flagCacheService).invalidateCache(appKey, flagKey, environment);
        
        // Verify delete message is sent to Redis
        verify(redisTemplate).convertAndSend(
            eq("feature_flag_changes:" + appKey),
            contains("\"deleted\":true")
        );
    }
}
