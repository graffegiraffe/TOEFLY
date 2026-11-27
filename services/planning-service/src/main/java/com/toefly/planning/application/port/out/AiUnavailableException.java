package com.toefly.planning.application.port.out;

/**
 * ai-gateway gave no structured answer: quota, providers down, a timeout or a refusal. WeekGenerator falls back to
 * GreedyWeekPacker (docs/06-ai-agent.md §5.2).
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiUnavailableException(String message) {
        super(message);
    }
}
