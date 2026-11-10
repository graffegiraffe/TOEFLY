package com.toefly.practice.application.port.in;

import com.toefly.common.web.ProblemProperties;
import com.toefly.common.web.ProblemType;
import com.toefly.practice.domain.attempt.TaskType;
import java.util.Map;

/** Writing and Speaking sets are answered through assessment-service and speech-service. */
@ProblemType(value = "business-rule-violated", status = 422)
public class TaskTypeNotSupportedException extends RuntimeException implements ProblemProperties {

    public TaskTypeNotSupportedException(TaskType taskType) {
        super(taskType + " is not a closed task type; it is answered as a submission.");
    }

    @Override
    public Map<String, Object> problemProperties() {
        return Map.of("rule", "closed-task-types-only");
    }
}
