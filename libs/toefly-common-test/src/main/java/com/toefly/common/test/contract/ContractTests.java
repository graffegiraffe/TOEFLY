package com.toefly.common.test.contract;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Producer side of the contracts (ADR-0031): one dynamic test per contract file, for {@code @TestFactory} methods
 * of the component test of a service.
 */
public final class ContractTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ContractTests() {}

    /** REST contracts of the directory, run through the exchanger of the service. */
    public static Stream<DynamicTest> rest(Path directory, ContractExchanger exchanger) {
        return Contracts.load(directory).stream()
                .filter(Contract::isRest)
                .map(contract -> DynamicTest.dynamicTest(
                        contract.name(), contract.file().toUri(), () -> verifyRest(contract, exchanger)));
    }

    /**
     * Message contracts of the directory: {@code input.triggeredBy} is a no-argument method of {@code triggers}
     * that performs the business action; the receiver reads what it published.
     */
    public static Stream<DynamicTest> messages(Path directory, Object triggers, MessageReceiver receiver) {
        return Contracts.load(directory).stream()
                .filter(Contract::isMessage)
                .map(contract -> DynamicTest.dynamicTest(
                        contract.name(), contract.file().toUri(), () -> verifyMessage(contract, triggers, receiver)));
    }

    static void verifyRest(Contract contract, ContractExchanger exchanger) throws Exception {
        Contract.Request request = contract.request();
        Contract.Response expected = contract.response();
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(request.headers());
        headers.remove("Authorization");
        ContractExchanger.ContractResponse actual = exchanger.exchange(new ContractExchanger.ContractRequest(
                request.method(), request.url(), headers, request.body() == null ? null : json(request.body())));
        List<String> differences = new ArrayList<>();
        if (actual.status() != expected.status()) {
            differences.add("status: expected " + expected.status() + ", got " + actual.status());
        }
        differences.addAll(HeaderComparator.differences(expected.headers(), Map.of(), actual.headers()));
        if (expected.body() != null || !expected.bodyMatchers().isEmpty()) {
            differences.addAll(
                    BodyComparator.differences(expected.body(), parse(actual.body()), expected.bodyMatchers()));
        }
        fail(contract, differences, actual.body());
    }

    static void verifyMessage(Contract contract, Object triggers, MessageReceiver receiver) throws Exception {
        Contract.OutputMessage expected = contract.outputMessage();
        Object mark = receiver.mark(expected.sentTo());
        trigger(contract, triggers);
        ContractMessage actual = receiver.receive(expected.sentTo(), mark);
        Map<String, List<String>> headers = new TreeMap<>();
        actual.headers().forEach((name, value) -> headers.put(name, List.of(value)));
        List<String> differences = new ArrayList<>(HeaderComparator.differences(expected.headers(), Map.of(), headers));
        differences.addAll(BodyComparator.differences(expected.body(), actual.body(), expected.bodyMatchers()));
        fail(contract, differences, actual.body());
    }

    private static void trigger(Contract contract, Object triggers) throws Exception {
        Method method = findMethod(triggers.getClass(), contract.triggerMethod());
        method.setAccessible(true);
        try {
            method.invoke(triggers);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    return method;
                }
            }
        }
        throw new IllegalArgumentException("No method " + name + "() in " + type.getName() + " for input.triggeredBy");
    }

    private static void fail(Contract contract, List<String> differences, @Nullable Object actual) {
        if (!differences.isEmpty()) {
            throw new AssertionError("Contract " + contract.name() + " (" + contract.file() + ") is broken:\n  "
                    + String.join("\n  ", differences) + "\nactual: " + actual);
        }
    }

    static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    static @Nullable Object parse(@Nullable String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        return JSON.readValue(body, Object.class);
    }
}
