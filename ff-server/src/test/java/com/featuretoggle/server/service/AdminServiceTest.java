package com.featuretoggle.server.service;

import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.common.model.Rule;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminService
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    
    @Mock
    private FeatureFlagRepository flagRepository;
    
    @Mock
    private FlagRuleRepository ruleRepository;
    
    @Mock
    private AppRepository appRepository;
    
    @Mock
    private EvaluationService evaluationService;
    
    @Mock
    private FlagCacheService flagCacheService;
    
    @Mock
    private StringRedisTemplate redisTemplate;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private AdminService adminService;
    
    private App testApp;
    private FeatureFlagEntity testFlagEntity;
    
    @BeforeEach
    void setUp() throws Exception {
        testApp = App.builder()
            .id(1L)
            .appKey("test-app")
            .name("Test App")
            .build();
        
        testFlagEntity = FeatureFlagEntity.builder()
            .id(1L)
            .appId(1L)
            .flagKey("test_flag")
            .name("Test Flag")
            .environment("prod")
            .status(1)
            .defaultValue("false")
            .version(1L)
            .build();
    }
    
    @Test
    void shouldCreateFlagSuccessfully() throws Exception {
        // Given
        FeatureFlag flagRequest = FeatureFlag.builder()
            .flagKey("new_flag")
            .name("New Flag")
            .defaultValue("false")
            .rules(List.of(
                Rule.builder()
                    .priority(1)
                    .actionValue("true")
                    .build()
            ))
            .build();
        
        when(objectMapper.writeValueAsString(any(Rule.class))).thenReturn("{}");
        when(appRepository.findByAppKey("test-app")).thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironmentAndFlagKey(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(flagRepository.save(any(FeatureFlagEntity.class))).thenReturn(testFlagEntity);
        when(ruleRepository.findByFlagIdOrderByPriorityAsc(any())).thenReturn(List.of());
        
        // When
        FeatureFlag result = adminService.createFlag("test-app", "prod", flagRequest);
        
        // Then
        assertNotNull(result);
        assertEquals("test_flag", result.getFlagKey());
        verify(flagRepository, times(1)).save(any(FeatureFlagEntity.class));
        verify(evaluationService, times(1)).publishChange(any(), any(), any(), any());
    }
    
    @Test
    void shouldThrowExceptionWhenFlagAlreadyExists() {
        // Given
        FeatureFlag flagRequest = FeatureFlag.builder()
            .flagKey("existing_flag")
            .name("Existing Flag")
            .defaultValue("false")
            .build();
        
        when(appRepository.findByAppKey("test-app")).thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironmentAndFlagKey(any(), any(), any()))
            .thenReturn(Optional.of(testFlagEntity));
        
        // When & Then
        assertThrows(RuntimeException.class, () -> {
            adminService.createFlag("test-app", "prod", flagRequest);
        });
    }
    
    @Test
    void shouldUpdateFlagSuccessfully() throws Exception {
        // Given
        FeatureFlag flagRequest = FeatureFlag.builder()
            .name("Updated Flag")
            .defaultValue("true")
            .status(1)
            .build();
        
        when(appRepository.findByAppKey("test-app")).thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironmentAndFlagKey(any(), any(), any()))
            .thenReturn(Optional.of(testFlagEntity));
        when(flagRepository.save(any(FeatureFlagEntity.class))).thenReturn(testFlagEntity);
        when(ruleRepository.findByFlagIdOrderByPriorityAsc(any())).thenReturn(List.of());
        
        // When
        FeatureFlag result = adminService.updateFlag("test-app", "prod", "test_flag", flagRequest);
        
        // Then
        assertNotNull(result);
        verify(flagRepository, times(1)).save(any(FeatureFlagEntity.class));
        verify(ruleRepository, times(1)).deleteByFlagId(any());
        verify(evaluationService, times(1)).publishChange(any(), any(), any(), any());
    }
    
    @Test
    void shouldDeleteFlagSuccessfully() {
        // Given
        when(appRepository.findByAppKey("test-app")).thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironmentAndFlagKey(any(), any(), any()))
            .thenReturn(Optional.of(testFlagEntity));
        
        // When
        adminService.deleteFlag("test-app", "prod", "test_flag");
        
        // Then
        verify(ruleRepository, times(1)).deleteByFlagId(any());
        verify(flagRepository, times(1)).delete(any(FeatureFlagEntity.class));
        verify(evaluationService, times(1)).publishDelete(any(), any(), any(), any());
    }
    
    @Test
    void shouldGetFlagSuccessfully() {
        // Given
        when(appRepository.findByAppKey("test-app")).thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironmentAndFlagKey(any(), any(), any()))
            .thenReturn(Optional.of(testFlagEntity));
        when(ruleRepository.findByFlagIdOrderByPriorityAsc(any())).thenReturn(List.of());
        
        // When
        FeatureFlag result = adminService.getFlag("test-app", "prod", "test_flag");
        
        // Then
        assertNotNull(result);
        assertEquals("test_flag", result.getFlagKey());
    }
    
    @Test
    void shouldListFlagsSuccessfully() {
        // Given
        when(appRepository.findByAppKey("test-app")).thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironment(any(), any()))
            .thenReturn(List.of(testFlagEntity));
        when(ruleRepository.findByFlagIdOrderByPriorityAsc(any())).thenReturn(List.of());
        
        // When
        List<FeatureFlag> result = adminService.listFlags("test-app", "prod");
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test_flag", result.get(0).getFlagKey());
    }
    
    @Test
    void shouldReturnCachedFlagWhenCacheHit() {
        // Given
        FeatureFlag cachedFlag = FeatureFlag.builder()
            .flagKey("test_flag")
            .name("Test Flag")
            .defaultValue("false")
            .build();
        
        when(flagCacheService.getFromCache("test-app", "test_flag", "prod"))
            .thenReturn(cachedFlag);
        when(flagCacheService.hasFlagInCache("test-app", "test_flag", "prod"))
            .thenReturn(true);
        
        // When
        FeatureFlag result = adminService.getFlag("test-app", "prod", "test_flag");
        
        // Then
        assertNotNull(result);
        assertEquals("test_flag", result.getFlagKey());
        verify(flagRepository, never()).findByAppIdAndEnvironmentAndFlagKey(any(), any(), any());
    }
    
    @Test
    void shouldCacheNullValueWhenFlagNotFound() {
        // Given
        when(flagCacheService.getFromCache("test-app", "nonexistent", "prod"))
            .thenReturn(null);
        when(flagCacheService.hasFlagInCache("test-app", "nonexistent", "prod"))
            .thenReturn(false);
        when(appRepository.findByAppKey("test-app")).thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironmentAndFlagKey(any(), any(), eq("nonexistent")))
            .thenReturn(Optional.empty());
        
        // When
        FeatureFlag result = adminService.getFlag("test-app", "prod", "nonexistent");
        
        // Then
        assertNull(result);
        verify(flagCacheService, times(1)).saveNullToCache("test-app", "nonexistent", "prod");
    }
    
    @Test
    void shouldRemoveNullCacheWhenCreatingFlag() {
        // Given
        FeatureFlag flagRequest = FeatureFlag.builder()
            .flagKey("new_flag")
            .name("New Flag")
            .defaultValue("false")
            .build();
        
        when(appRepository.findByAppKey("test-app")).thenReturn(Optional.of(testApp));
        when(flagRepository.findByAppIdAndEnvironmentAndFlagKey(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(flagRepository.save(any(FeatureFlagEntity.class))).thenReturn(testFlagEntity);
        when(ruleRepository.findByFlagIdOrderByPriorityAsc(any())).thenReturn(List.of());
        
        // When
        FeatureFlag result = adminService.createFlag("test-app", "prod", flagRequest);
        
        // Then
        assertNotNull(result);
        verify(redisTemplate, times(1)).delete("flag:test-app:prod:new_flag");
    }
    
    @Test
    void shouldReturnCachedFlagsForListWhenCacheHit() {
        // Given
        List<FeatureFlag> cachedFlags = List.of(
            FeatureFlag.builder()
                .flagKey("flag1")
                .name("Flag 1")
                .build(),
            FeatureFlag.builder()
                .flagKey("flag2")
                .name("Flag 2")
                .build()
        );
        
        when(flagCacheService.getAppFlagsFromCache("test-app", "prod"))
            .thenReturn(cachedFlags);
        
        // When
        List<FeatureFlag> result = adminService.listFlags("test-app", "prod");
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(flagRepository, never()).findByAppIdAndEnvironment(any(), any());
    }
}
