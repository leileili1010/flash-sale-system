package com.flashsale.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.common.time.TimeController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = {TimeController.class, WebLayerTest.ProbeController.class})
@Import({GlobalExceptionHandler.class, WebLayerTest.Config.class, WebLayerTest.ProbeController.class})
class WebLayerTest {

    static final Instant NOW = Instant.parse("2026-11-12T18:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void timeEndpointReturnsServerClock() throws Exception {
        mockMvc.perform(get("/api/time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epochMillis").value(NOW.toEpochMilli()));
    }

    @Test
    void businessErrorsMapToTheirStatusAndCode() throws Exception {
        mockMvc.perform(get("/test/sold-out"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLD_OUT"))
                .andExpect(jsonPath("$.detail").value("This item is sold out."));
    }

    @Test
    void validationErrorsListOffendingFields() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    void malformedJsonIsAValidationFailure() throws Exception {
        mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @TestConfiguration
    static class Config {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping("/test/sold-out")
        void soldOut() {
            throw new ApiException(ErrorCode.SOLD_OUT);
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("database password is hunter2");
        }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody Probe probe) {
        }

        record Probe(@NotBlank String name, @Min(1) int quantity) {
        }
    }
}
