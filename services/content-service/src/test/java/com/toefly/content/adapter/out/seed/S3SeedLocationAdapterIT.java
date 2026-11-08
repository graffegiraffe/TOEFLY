package com.toefly.content.adapter.out.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toefly.common.test.ToeflyTestImages;
import com.toefly.content.application.port.out.SeedFileException;
import com.toefly.content.application.seed.SeedManifest.FileRef;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** The seed bundle read from a real SeaweedFS S3 endpoint, as in the local Compose stack. */
@Testcontainers
class S3SeedLocationAdapterIT {

    @Container
    static final GenericContainer<?> SEAWEED = new GenericContainer<>(ToeflyTestImages.SEAWEEDFS)
            .withCommand("server", "-dir=/data", "-s3", "-s3.port=8333")
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCodeMatching(c -> c < 500))
            .withStartupTimeout(Duration.ofMinutes(2));

    static S3Client s3;

    @BeforeAll
    static void bucket() {
        s3 = S3Client.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .endpointOverride(URI.create("http://" + SEAWEED.getHost() + ":" + SEAWEED.getMappedPort(8333)))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .build();
        s3.createBucket(r -> r.bucket("toefly-seed"));
    }

    @AfterAll
    static void close() {
        s3.close();
    }

    @Test
    @DisplayName("the bucket prefix is parsed from the location")
    void parse() {
        assertThat(S3SeedLocationAdapter.parse("s3://toefly-seed/seed/v1/")).containsExactly("toefly-seed", "seed/v1/");
        assertThat(S3SeedLocationAdapter.parse("s3://toefly-seed")).containsExactly("toefly-seed", "");
    }

    @Test
    @DisplayName("without a manifest under the prefix there is nothing to import; a listed file that is missing fails")
    void emptyPrefix() {
        var adapter = new S3SeedLocationAdapter(s3, "toefly-seed", "empty/v1");

        assertThat(adapter.manifest()).isEmpty();
        assertThatThrownBy(() -> adapter.itemSet(new FileRef("a/b.json", "a/b", "ITEM_SET", "sha256:0")))
                .isInstanceOf(SeedFileException.class)
                .hasMessageContaining("s3://toefly-seed/empty/v1/a/b.json");
    }

    @Test
    @DisplayName("the manifest, source and item set uploaded by the scripts are read and verified by hash")
    void readsBundle() {
        String set = """
                {"schemaVersion":"1.0","kind":"ITEM_SET","seedId":"demo/v-001","sourceCode":"demo",
                 "extraction":{"method":"OCR_LLM","reviewed":false,"warnings":["v-001: one item skipped"]},
                 "itemSet":{"taskType":"VOCAB_EXERCISE","provenance":"EXTRACTED","topic":"Art","skills":[],
                  "payload":{"stimulus":{"instructions":"Fill in.","unitRef":{"framework":"COLLINS_VOCAB"}},
                   "items":[{"ordinal":1,"exerciseType":"GAP_FILL","sentence":"A ___.","answerKey":"mural",
                     "acceptedAnswers":[],"explanation":{"summary":"Explanation not provided by the source."}}]}}}
                """;
        byte[] bytes = set.getBytes(StandardCharsets.UTF_8);
        String sha = "sha256:" + BundleSeedLocationAdapter.sha256(bytes);
        put("seed/v1/demo/item-sets/v-001.json", set);
        put("seed/v1/demo/source.json", """
                {"sourceCode":"demo","title":"Demo","publisher":"Tests","year":2026,"examFormat":"LEGACY",
                 "attributionText":"Synthetic fixture","extra":{}}
                """);
        put("seed/v1/manifest.json", """
                {"manifestVersion":1,"sources":[{"sourceCode":"demo","bundleSha256":"sha256:1","files":[
                  {"path":"demo/item-sets/v-001.json","seedId":"demo/v-001","kind":"ITEM_SET","sha256":"%s"}]}]}
                """.formatted(sha));
        var adapter = new S3SeedLocationAdapter(s3, "toefly-seed", "seed/v1/");

        var manifest = adapter.manifest().orElseThrow();
        var file = manifest.sources().getFirst().files().getFirst();
        var item = adapter.itemSet(file);

        assertThat(adapter.source("demo").attributionText()).isEqualTo("Synthetic fixture");
        assertThat(item.seedId()).isEqualTo("demo/v-001");
        assertThat(item.needsReview()).isTrue();
        assertThat(item.extraction().warnings()).containsExactly("v-001: one item skipped");
        assertThatThrownBy(() -> adapter.itemSet(new FileRef(file.path(), file.seedId(), "ITEM_SET", "sha256:0")))
                .isInstanceOf(SeedFileException.class)
                .hasMessageContaining("differs from the manifest");
    }

    private static void put(String key, String body) {
        s3.putObject(r -> r.bucket("toefly-seed").key(key), RequestBody.fromString(body));
    }
}
