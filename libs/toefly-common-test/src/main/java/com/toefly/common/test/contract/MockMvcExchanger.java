package com.toefly.common.test.contract;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Exchanger of servlet services: the contract request goes through MockMvc, signed by {@code authentication}. */
public final class MockMvcExchanger implements ContractExchanger {

    private final MockMvc mvc;
    private final RequestPostProcessor authentication;

    private MockMvcExchanger(MockMvc mvc, RequestPostProcessor authentication) {
        this.mvc = mvc;
        this.authentication = authentication;
    }

    public static MockMvcExchanger of(MockMvc mvc, RequestPostProcessor authentication) {
        return new MockMvcExchanger(mvc, authentication);
    }

    @Override
    public ContractResponse exchange(ContractRequest request) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(
                        HttpMethod.valueOf(request.method()), request.url())
                .with(authentication);
        request.headers().forEach(builder::header);
        if (request.body() != null) {
            builder.content(request.body());
        }
        MockHttpServletResponse response = mvc.perform(builder).andReturn().getResponse();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, response.getHeaders(name));
        }
        return new ContractResponse(response.getStatus(), headers, response.getContentAsString(StandardCharsets.UTF_8));
    }
}
