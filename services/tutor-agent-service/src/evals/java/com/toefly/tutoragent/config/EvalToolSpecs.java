package com.toefly.tutoragent.config;

import com.toefly.tutoragent.application.port.out.ChatModelPort;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** The tool signatures of the service, for the evals outside Spring. */
public final class EvalToolSpecs {

    private EvalToolSpecs() {}

    public static List<ChatModelPort.ToolSpec> load() {
        return ToolSpecsLoader.load(JsonMapper.builder().build());
    }
}
