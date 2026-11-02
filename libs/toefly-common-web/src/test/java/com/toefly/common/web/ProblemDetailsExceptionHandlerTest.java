package com.toefly.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ProblemDetailsExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SampleController())
            .setControllerAdvice(new ProblemDetailsExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();

    @Test
    void rendersCatalogTypeWithExtensions() throws Exception {
        mvc.perform(get("/versioned").header(RequestIds.HEADER, "req-1"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(header().string(RequestIds.HEADER, "req-1"))
                .andExpect(jsonPath("$.type").value("https://toefly.app/problems/version-mismatch"))
                .andExpect(jsonPath("$.title").value("Resource version mismatch"))
                .andExpect(jsonPath("$.status").value(412))
                .andExpect(jsonPath("$.code").value("VERSION_MISMATCH"))
                .andExpect(jsonPath("$.currentVersion").value(7))
                .andExpect(jsonPath("$.requestId").value("req-1"));
    }

    @Test
    void rendersValidationErrorsWithPointers() throws Exception {
        mvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://toefly.app/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].pointer").value("/title"));
    }

    @Test
    void mapsFrameworkErrorsToCatalog() throws Exception {
        mvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void hidesUnexpectedErrors() throws Exception {
        mvc.perform(get("/broken"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("The request could not be processed."));
    }

    @RestController
    static class SampleController {

        @GetMapping("/versioned")
        String versioned() {
            throw new VersionMismatchException();
        }

        @PostMapping("/goals")
        String goals(@Valid @RequestBody Goal goal) {
            return goal.title();
        }

        @GetMapping("/broken")
        String broken() {
            throw new IllegalStateException("secret internal detail");
        }
    }

    record Goal(@NotBlank String title) {}

    @ProblemType(value = "version-mismatch", status = 412, title = "Resource version mismatch")
    static class VersionMismatchException extends RuntimeException implements ProblemProperties {

        VersionMismatchException() {
            super("The resource was modified by another request.");
        }

        @Override
        public Map<String, Object> problemProperties() {
            return Map.of("currentVersion", 7);
        }
    }
}
