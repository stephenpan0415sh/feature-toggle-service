package com.featuretoggle.server.service;

import com.featuretoggle.common.model.FeatureFlag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FlagCacheService
 */
@ExtendWith(MockitoExtension.class)
class FlagCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private FlagCacheService flagCacheService;

    private FeatureFlag testFlag;

    @BeforeEach
    void setUp() {
        testFlag = FeatureFlag.builder()
            .id(1L)
            .flagKey("test-flag")
            .name("Test Flag")
            .environment("prod")
            .status(1)
            .defaultValue("false")
            .version(5L)
            .build();
    }

    @Test
    void getFromCache_shouldReturnFlag_whenCacheHit() throws Exception {
        // Given
        String cacheKey = "flag:test-app:prod:test-flag";
        String flagJson = "{\"flagKey\":\"test-flag\"}";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(flagJson);
        when(objectMapper.readValue(flagJson, FeatureFlag.class)).thenReturn(testFlag);

        // When
        FeatureFlag result = flagCacheService.getFromCache("test-app", "test-flag", "prod");

        // Then
        assertNotNull(result);
        assertEquals("test-flag", result.getFlagKey());
        verify(valueOperations).get(cacheKey);
    }

    @Test
    void getFromCache_shouldReturnNull_whenCacheMiss() {
        // Given
        String cacheKey = "flag:test-app:prod:test-flag";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);

        // When
        FeatureFlag result = flagCacheService.getFromCache("test-app", "test-flag", "prod");

        // Then
        assertNull(result);
    }

    @Test
    void saveToCache_shouldSaveFlagAndVersion() throws Exception {
        // Given
        String flagJson = "{\"flagKey\":\"test-flag\"}";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(testFlag)).thenReturn(flagJson);

        // When
        flagCacheService.saveToCache("test-app", testFlag, "prod");

        // Then
        verify(valueOperations).set(
            eq("flag:test-app:prod:test-flag"),
            eq(flagJson),
            eq(24L),
            eq(TimeUnit.HOURS)
        );
        verify(valueOperations).set(
            eq("flag:version:test-app:prod:test-flag"),
            eq("5"),
            eq(48L),
            eq(TimeUnit.HOURS)
        );
    }

    @Test
    void invalidateCache_shouldDeleteFlagAndIncrementVersion() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        // When
        flagCacheService.invalidateCache("test-app", "test-flag", "prod");

        // Then
        verify(redisTemplate).delete("flag:test-app:prod:test-flag");
        verify(redisTemplate).delete("flag:version:test-app:prod:test-flag");
        verify(valueOperations).increment("app:version:test-app:prod");
    }

    @Test
    void getGlobalVersion_shouldReturnVersion_whenExists() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("app:version:test-app:prod")).thenReturn("100");

        // When
        Long result = flagCacheService.getGlobalVersion("test-app", "prod");

        // Then
        assertEquals(100L, result);
    }

    @Test
    void getGlobalVersion_shouldReturnZero_whenNotExists() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("app:version:test-app:prod")).thenReturn(null);

        // When
        Long result = flagCacheService.getGlobalVersion("test-app", "prod");

        // Then
        assertEquals(0L, result);
    }

    @Test
    void getAppFlagsFromCache_shouldReturnFlags_whenCacheHit() throws Exception {
        // Given
        String cacheKey = "app:flags:test-app:prod";
        String flagsJson = "[]";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(flagsJson);
        when(objectMapper.readValue(eq(flagsJson), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(List.of(testFlag));

        // When
        List<FeatureFlag> result = flagCacheService.getAppFlagsFromCache("test-app", "prod");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void saveAppFlagsToCache_shouldSaveFlagsList() throws Exception {
        // Given
        List<FeatureFlag> flags = List.of(testFlag);
        String flagsJson = "[{}]";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(flags)).thenReturn(flagsJson);

        // When
        flagCacheService.saveAppFlagsToCache("test-app", flags, "prod");

        // Then
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
            eq("app:flags:test-app:prod"),
            jsonCaptor.capture(),
            eq(24L),
            eq(TimeUnit.HOURS)
        );
        assertEquals(flagsJson, jsonCaptor.getValue());
    }

    @Test
    void updateAppFlagsToCache_shouldUpdateExistingFlag() throws Exception {
        // Given
        String cacheKey = "app:flags:test-app:prod";
        String existingJson = "[{\"flagKey\":\"test-flag\"}]";
        String updatedJson = "[{}]";
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(existingJson);
        when(objectMapper.readValue(eq(existingJson), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(new java.util.ArrayList<>(List.of(testFlag))); // Use mutable list
        when(objectMapper.writeValueAsString(anyList())).thenReturn(updatedJson);

        // When
        flagCacheService.updateAppFlagsToCache("test-app", testFlag, "prod");

        // Then
        verify(valueOperations).set(
            eq(cacheKey),
            eq(updatedJson),
            eq(24L),
            eq(TimeUnit.HOURS)
        );
    }

    @Test
    void invalidateAppCache_shouldDeleteAppFlags() {
        // When
        flagCacheService.invalidateAppCache("test-app", "prod");

        // Then
        verify(redisTemplate).delete("app:flags:test-app:prod");
    }

    @Test
    void hasFlagInCache_shouldReturnTrue_whenExists() {
        // Given
        when(redisTemplate.hasKey("flag:test-app:prod:test-flag")).thenReturn(true);

        // When
        boolean result = flagCacheService.hasFlagInCache("test-app", "test-flag", "prod");

        // Then
        assertTrue(result);
    }

    @Test
    void hasFlagInCache_shouldReturnFalse_whenNotExists() {
        // Given
        when(redisTemplate.hasKey("flag:test-app:prod:test-flag")).thenReturn(false);

        // When
        boolean result = flagCacheService.hasFlagInCache("test-app", "test-flag", "prod");

        // Then
        assertFalse(result);
    }

    @Test
    void getCacheStats_shouldReturnStats() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("app:flags:test-app:prod")).thenReturn("[{}]");
        when(valueOperations.get("app:version:test-app:prod")).thenReturn("100");

        // When
        Map<String, Object> stats = flagCacheService.getCacheStats("test-app", "prod");

        // Then
        assertNotNull(stats);
        assertEquals("test-app", stats.get("appKey"));
        assertEquals("prod", stats.get("environment"));
        assertEquals(100L, stats.get("globalVersion"));
    }
}
