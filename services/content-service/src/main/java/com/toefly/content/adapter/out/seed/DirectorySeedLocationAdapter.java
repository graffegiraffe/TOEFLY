package com.toefly.content.adapter.out.seed;

import com.toefly.content.application.port.out.SeedFileException;
import com.toefly.content.application.port.out.SeedLocationPort;
import com.toefly.content.application.seed.SeedItemSet;
import com.toefly.content.application.seed.SeedItemSet.SeedItem;
import com.toefly.content.application.seed.SeedManifest;
import com.toefly.content.application.seed.SeedSourceInfo;
import com.toefly.content.domain.catalog.ExamFormat;
import com.toefly.content.domain.catalog.Provenance;
import com.toefly.content.domain.catalog.TaskType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the seed bundle from a directory ({@code toefly.content.seed.location=dir:/path}); the layout is the one of
 * docs/08-content-pipeline.md §4.2 with {@code manifest.json} in the root.
 */
public class DirectorySeedLocationAdapter implements SeedLocationPort {

    private final Path root;
    private final JsonMapper json = JsonMapper.builder().build();

    public DirectorySeedLocationAdapter(Path root) {
        this.root = root;
    }

    @Override
    public Optional<SeedManifest> manifest() {
        Path file = root.resolve("manifest.json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        JsonNode node = read(file);
        List<SeedManifest.SourceBundle> sources = new ArrayList<>();
        for (JsonNode s : node.path("sources")) {
            List<SeedManifest.FileRef> files = new ArrayList<>();
            for (JsonNode f : s.path("files")) {
                files.add(new SeedManifest.FileRef(
                        text(f, "path"), text(f, "seedId"), text(f, "kind"), text(f, "sha256")));
            }
            sources.add(new SeedManifest.SourceBundle(text(s, "sourceCode"), text(s, "bundleSha256"), files));
        }
        return Optional.of(new SeedManifest(sources));
    }

    @Override
    public SeedSourceInfo source(String sourceCode) {
        JsonNode n = read(root.resolve(sourceCode).resolve("source.json"));
        JsonNode extra = n.path("extra");
        return new SeedSourceInfo(
                text(n, "sourceCode"),
                text(n, "title"),
                text(n, "publisher"),
                n.hasNonNull("year") ? n.get("year").asInt() : null,
                n.hasNonNull("url") ? n.get("url").asString() : null,
                ExamFormat.valueOf(text(n, "examFormat")),
                text(n, "attributionText"),
                extra.isMissingNode() ? "{}" : Canonical.of(extra));
    }

    @Override
    public SeedItemSet itemSet(SeedManifest.FileRef ref) {
        Path file = root.resolve(ref.path());
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new SeedFileException("cannot read " + ref.path(), e);
        }
        String actual = "sha256:" + sha256(bytes);
        if (!actual.equals(ref.sha256())) {
            throw new SeedFileException("hash of " + ref.path() + " differs from the manifest");
        }
        JsonNode env = json.readTree(new String(bytes, StandardCharsets.UTF_8));
        JsonNode set = env.path("itemSet");
        JsonNode payload = set.path("payload");
        if (!"ITEM_SET".equals(env.path("kind").asString())) {
            throw new SeedFileException(ref.path() + " is not an ITEM_SET");
        }
        String seedId = text(env, "seedId");
        TaskType type = TaskType.valueOf(text(set, "taskType"));
        List<SeedItem> items = new ArrayList<>();
        for (JsonNode i : payload.path("items")) {
            int ordinal = i.path("ordinal").asInt(items.size() + 1);
            items.add(new SeedItem(
                    Uuids.item(seedId, ordinal),
                    ordinal,
                    i.hasNonNull("subskill") ? i.get("subskill").asString() : null,
                    i.path("maxScore").asInt(1)));
        }
        if (items.isEmpty()) {
            throw new SeedFileException(ref.path() + " has no items");
        }
        List<String> skills = new ArrayList<>();
        set.path("skills").forEach(s -> skills.add(s.asString()));
        JsonNode locator = env.path("locator");
        String method = env.path("extraction").path("method").asString("TEXT_LAYER");
        String canonical = Canonical.of(payload);
        StringBuilder text = new StringBuilder();
        Canonical.collectStrings(payload, text);
        return new SeedItemSet(
                seedId,
                Uuids.itemSet(seedId),
                text(env, "sourceCode"),
                type,
                Provenance.valueOf(set.path("provenance").asString("EXTRACTED")),
                set.hasNonNull("cefr") ? set.get("cefr").asString() : null,
                text(set, "topic"),
                set.hasNonNull("domain") ? set.get("domain").asString() : null,
                skills,
                locator.path("pages").path(0).isInt()
                        ? locator.path("pages").path(0).asInt()
                        : null,
                locator.path("questionNumbers").path(0).isInt()
                        ? String.valueOf(locator.path("questionNumbers").path(0).asInt())
                        : null,
                env.path("extraction").path("reviewed").asBoolean(false),
                "OCR_LLM".equals(method),
                payload.path("stimulus").path("audio").path("durationSeconds").asDouble(0),
                canonical,
                sha256(canonical.getBytes(StandardCharsets.UTF_8)),
                text.toString(),
                items);
    }

    private JsonNode read(Path file) {
        try {
            return json.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new SeedFileException("cannot read " + file, e);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.asString().isBlank()) {
            throw new SeedFileException("missing field '" + field + "'");
        }
        return v.asString();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
