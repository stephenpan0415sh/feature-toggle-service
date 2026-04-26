package com.featuretoggle.server.controller;

import com.featuretoggle.common.dto.BatchEvaluationRequest;
import com.featuretoggle.common.model.EvaluationDetail;
import com.featuretoggle.server.service.AuditLogService;
import com.featuretoggle.server.service.EvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ClientApiController
 */
@ExtendWith(MockitoExtension.class)
class ClientApiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EvaluationService evaluationService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ClientApiController clientApiController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(clientApiController).build();
    }

    @Test
    void evaluateFlag_shouldReturnSuccess() throws Exception {
        // Given
        EvaluationDetail detail = new EvaluationDetail(
            "test-flag", true, "true",
            EvaluationDetail.EvaluationReason.DEFAULT,
            null, "trace-123", "prod", null, "v1.0",
            System.currentTimeMillis(), null, null
        );
        when(evaluationService.evaluateFlag(any(), any(), any(), any())).thenReturn(detail);

        // When & Then
        mockMvc.perform(get("/api/client/evaluate/test-flag")
                .param("appKey", "test-app")
                .param("userId", "user123")
                .param("region", "cn-east"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.flagKey").value("test-flag"));

        verify(evaluationService, times(1)).evaluateFlag(eq("test-app"), eq("test-flag"), any(), any());
    }

    @Test
    void batchEvaluate_shouldReturnSuccess() throws Exception {
        // Given
        BatchEvaluationRequest request = new BatchEvaluationRequest(
            "test-app", "secret", "user123", Map.of("region", "cn-east"), List.of("flag1", "flag2")
        );

        EvaluationDetail detail = new EvaluationDetail(
            "flag1", true, "true",
            EvaluationDetail.EvaluationReason.DEFAULT,
            null, "trace-123", "prod", null, "v1.0",
            System.currentTimeMillis(), null, null
        );
        when(evaluationService.batchEvaluate(any(), any(), any(), any()))
            .thenReturn(Map.of("flag1", detail, "flag2", detail));

        // When & Then
        mockMvc.perform(post("/api/client/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isMap());

        verify(evaluationService, times(1)).batchEvaluate(any(), any(), any(), any());
    }

    @Test
    void batchEvaluate_shouldReturnError_whenMissingFields() throws Exception {
        // Given - Missing userId
        BatchEvaluationRequest request = new BatchEvaluationRequest(
            "test-app", "secret", null, null, List.of("flag1")
        );

        // When & Then
        mockMvc.perform(post("/api/client/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Missing required fields: appKey, userId"));

        verify(evaluationService, never()).batchEvaluate(any(), any(), any(), any());
    }

    @Test
    void batchEvaluate_shouldReturnError_whenEmptyFlagKeys() throws Exception {
        // Given
        BatchEvaluationRequest request = new BatchEvaluationRequest(
            "test-app", "secret", "user123", null, List.of()
        );

        // When & Then
        mockMvc.perform(post("/api/client/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("flagKeys cannot be empty"));
    }

    @Test
    void getConfigs_shouldReturnSuccess() throws Exception {
        // Given
        when(evaluationService.getFlagVersions(any(), any())).thenReturn(Map.of("flag1", 1L));
        when(evaluationService.getAllFlagsIncremental(any(), any(), any()))
            .thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/client/configs")
                .param("appKey", "test-app"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getConfigs_shouldReturnNotModified_whenVersionSame() throws Exception {
        // Given
        when(evaluationService.getFlagVersions(any(), any())).thenReturn(Map.of("flag1", 100L));

        // When & Then
        mockMvc.perform(get("/api/client/configs")
                .param("appKey", "test-app")
                .param("lastKnownVersion", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hasChanges").value(false));

        verify(evaluationService, never()).getAllFlagsIncremental(any(), any(), any());
    }
}
