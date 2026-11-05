package com.toefly.common.test.contract;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Loads the {@code *.yml} contracts of a directory, sorted by file name. */
public final class Contracts {

    private Contracts() {}

    public static List<Contract> load(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("No contract directory " + directory.toAbsolutePath());
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(file ->
                            file.toString().endsWith(".yml") || file.toString().endsWith(".yaml"))
                    .sorted()
                    .map(Contracts::parse)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Contract parse(Path file) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> root = map(yaml.load(reader), file + ": root");
            String name = string(root.get("name"), file + ": name");
            Map<String, Object> request = optionalMap(root.get("request"));
            Map<String, Object> response = optionalMap(root.get("response"));
            Map<String, Object> input = optionalMap(root.get("input"));
            Map<String, Object> output = optionalMap(root.get("outputMessage"));
            Contract contract = new Contract(
                    name,
                    root.get("description") == null
                            ? ""
                            : String.valueOf(root.get("description")).strip(),
                    request == null ? null : request(request, file),
                    response == null ? null : response(response, file),
                    input == null || input.get("triggeredBy") == null ? null : String.valueOf(input.get("triggeredBy")),
                    output == null ? null : output(output, file),
                    file);
            if (!contract.isRest() && !contract.isMessage()) {
                throw new IllegalArgumentException(
                        file + ": a contract needs request and response, or input.triggeredBy and outputMessage");
            }
            return contract;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Contract.Request request(Map<String, Object> request, Path file) {
        Map<String, Object> matchers = optionalMap(request.get("matchers"));
        Map<String, String> headerRegex = new LinkedHashMap<>();
        if (matchers != null && matchers.get("headers") instanceof List<?> headers) {
            for (Object header : headers) {
                Map<String, Object> entry = map(header, file + ": request.matchers.headers");
                headerRegex.put(
                        string(entry.get("key"), file + ": header matcher key"),
                        string(entry.get("regex"), file + ": header matcher regex"));
            }
        }
        return new Contract.Request(
                string(request.get("method"), file + ": request.method").toUpperCase(),
                string(request.get("url"), file + ": request.url"),
                headers(request.get("headers")),
                request.get("body"),
                headerRegex,
                bodyMatchers(matchers, file));
    }

    private static Contract.Response response(Map<String, Object> response, Path file) {
        Object status = response.get("status");
        if (!(status instanceof Integer code)) {
            throw new IllegalArgumentException(file + ": response.status must be a number");
        }
        return new Contract.Response(
                code,
                headers(response.get("headers")),
                response.get("body"),
                bodyMatchers(optionalMap(response.get("matchers")), file));
    }

    private static Contract.OutputMessage output(Map<String, Object> output, Path file) {
        return new Contract.OutputMessage(
                string(output.get("sentTo"), file + ": outputMessage.sentTo"),
                headers(output.get("headers")),
                output.get("body"),
                bodyMatchers(optionalMap(output.get("matchers")), file));
    }

    private static List<Contract.BodyMatcher> bodyMatchers(@Nullable Map<String, Object> matchers, Path file) {
        List<Contract.BodyMatcher> result = new ArrayList<>();
        if (matchers == null || !(matchers.get("body") instanceof List<?> body)) {
            return result;
        }
        for (Object item : body) {
            Map<String, Object> entry = map(item, file + ": matchers.body");
            String type = string(entry.get("type"), file + ": matcher type");
            if (!List.of("by_regex", "by_type", "by_equality").contains(type)) {
                throw new IllegalArgumentException(file + ": unknown matcher type " + type);
            }
            result.add(new Contract.BodyMatcher(
                    string(entry.get("path"), file + ": matcher path"),
                    type,
                    entry.get("regex") == null ? null : String.valueOf(entry.get("regex")),
                    entry.get("predefined") == null ? null : String.valueOf(entry.get("predefined")),
                    entry.get("minOccurrence") instanceof Integer min ? min : null,
                    entry.get("maxOccurrence") instanceof Integer max ? max : null));
        }
        return result;
    }

    private static Map<String, String> headers(@Nullable Object value) {
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, Object> map = optionalMap(value);
        if (map != null) {
            map.forEach((key, header) -> headers.put(key, String.valueOf(header)));
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Map<String, Object> optionalMap(@Nullable Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static Map<String, Object> map(@Nullable Object value, String where) {
        Map<String, Object> map = optionalMap(value);
        if (map == null) {
            throw new IllegalArgumentException(where + " must be a mapping");
        }
        return map;
    }

    private static String string(@Nullable Object value, String where) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(where + " is required");
        }
        return String.valueOf(value);
    }
}
