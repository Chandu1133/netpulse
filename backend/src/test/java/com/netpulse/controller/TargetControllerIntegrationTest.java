package com.netpulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netpulse.domain.ProtocolType;
import com.netpulse.dto.ChaosRuleRequest;
import com.netpulse.dto.TargetRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TargetControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listTargetsReturnsSixSeededTargets() throws Exception {
        mockMvc.perform(get("/api/targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].name", is("Local Mock Target")));
    }

    @Test
    void createToggleChaosAndDeleteTargetLifecycle() throws Exception {
        TargetRequest createReq = new TargetRequest(
                "Example HTTPS",
                "example.com",
                443,
                "/",
                ProtocolType.HTTPS,
                15,
                2500,
                true
        );

        MvcResult created = mockMvc.perform(post("/api/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Example HTTPS")))
                .andExpect(jsonPath("$.status", is("UNKNOWN")))
                .andReturn();

        Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/targets/{id}/toggle", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)))
                .andExpect(jsonPath("$.status", is("PAUSED")));

        ChaosRuleRequest chaosReq = new ChaosRuleRequest(300, 40, 25.0, false, false, true);
        mockMvc.perform(post("/api/chaos/{targetId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chaosReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addedLatencyMs", is(300)))
                .andExpect(jsonPath("$.packetDropPct", is(25.0)));

        mockMvc.perform(get("/api/targets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaosActive", is(true)));

        mockMvc.perform(delete("/api/chaos/{targetId}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/targets/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/targets/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationErrorsReturn400WithFieldMap() throws Exception {
        TargetRequest invalid = new TargetRequest("", "", 70000, null, null, 1, 50, true);

        mockMvc.perform(post("/api/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.host").exists())
                .andExpect(jsonPath("$.validationErrors.protocol").exists());
    }

    @Test
    void overviewMockTargetAndOpenApiEndpointsWork() throws Exception {
        mockMvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTargets", greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.openIncidents", is(0)));

        mockMvc.perform(get("/api/mock-target"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title", is("NetPulse Network Telemetry & Anomaly Detection API")));

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chaos"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/targets/1/probes"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/targets/1/traceroute?simulate=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulated", is(true)));

        TargetRequest updateReq = new TargetRequest(
                "Local Mock Target Updated", "localhost", 8080, "/api/mock-target",
                ProtocolType.HTTP, 15, 2500, true);
        mockMvc.perform(put("/api/targets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Local Mock Target Updated")));
    }
}
