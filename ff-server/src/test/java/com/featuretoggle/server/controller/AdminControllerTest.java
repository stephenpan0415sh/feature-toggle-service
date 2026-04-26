package com.featuretoggle.server.controller;

import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.server.service.AdminService;
import com.featuretoggle.server.service.AuditLogService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AdminController
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminService adminService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AdminController adminController;

    private ObjectMapper objectMapper;
    private FeatureFlag testFlag;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
        
        testFlag = FeatureFlag.builder()
            .flagKey("test-flag")
            .name("Test Flag")
            .defaultValue("false")
            .status(1)
            .version(1L)
            .build();
    }

    @Test
    void createFlag_shouldReturnSuccess() throws Exception {
        // Given
        when(adminService.createFlag(any(), any(), any())).thenReturn(testFlag);

        // When & Then
        mockMvc.perform(post("/api/admin/flags")
                .param("appKey", "test-app")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testFlag)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.flagKey").value("test-flag"));

        verify(adminService, times(1)).createFlag(eq("test-app"), any(), any());
    }

    @Test
    void createFlag_shouldReturnError_whenServiceThrows() throws Exception {
        // Given
        when(adminService.createFlag(any(), any(), any()))
            .thenThrow(new RuntimeException("App not found"));

        // When & Then
        mockMvc.perform(post("/api/admin/flags")
                .param("appKey", "invalid-app")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testFlag)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("App not found"));
    }

    @Test
    void getFlags_shouldReturnSingleFlag() throws Exception {
        // Given
        when(adminService.getFlag(any(), any(), any())).thenReturn(testFlag);

        // When & Then
        mockMvc.perform(get("/api/admin/flags")
                .param("appKey", "test-app")
                .param("flagKey", "test-flag"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.flagKey").value("test-flag"));
    }

    @Test
    void getFlags_shouldReturnAllFlags_whenNoFlagKey() throws Exception {
        // Given
        when(adminService.listFlags(any(), any())).thenReturn(List.of(testFlag));

        // When & Then
        mockMvc.perform(get("/api/admin/flags")
                .param("appKey", "test-app"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getFlags_shouldReturn404_whenFlagNotFound() throws Exception {
        // Given
        when(adminService.getFlag(any(), any(), any())).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/admin/flags")
                .param("appKey", "test-app")
                .param("flagKey", "nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateFlag_shouldReturnSuccess() throws Exception {
        // Given
        when(adminService.updateFlag(any(), any(), any(), any())).thenReturn(testFlag);
        when(adminService.getFlag(any(), any(), any())).thenReturn(testFlag);

        // When & Then
        mockMvc.perform(put("/api/admin/flags/test-flag")
                .param("appKey", "test-app")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testFlag)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(adminService, times(1)).updateFlag(eq("test-app"), any(), eq("test-flag"), any());
    }

    @Test
    void deleteFlag_shouldReturnSuccess() throws Exception {
        // Given
        when(adminService.getFlag(any(), any(), any())).thenReturn(testFlag);
        doNothing().when(adminService).deleteFlag(any(), any(), any());

        // When & Then
        mockMvc.perform(delete("/api/admin/flags/test-flag")
                .param("appKey", "test-app"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Flag deleted successfully"));

        verify(adminService, times(1)).deleteFlag(eq("test-app"), any(), eq("test-flag"));
    }
}
