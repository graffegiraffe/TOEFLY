package com.toefly.content.adapter.out.seed;

import com.toefly.content.application.port.out.SeedFileException;
import java.util.Optional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Reads the seed bundle from the private bucket {@code toefly-seed} ({@code toefly.content.seed.location=
 * s3://toefly-seed/seed/v1/}); R2 in the cluster, SeaweedFS locally (docs/08-content-pipeline.md §4.9). The
 * manifest is uploaded last, so a partial upload is never seen by the import.
 */
public class S3SeedLocationAdapter extends BundleSeedLocationAdapter {

    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public S3SeedLocationAdapter(S3Client s3, String bucket, String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        this.prefix = prefix.isEmpty() || prefix.endsWith("/") ? prefix : prefix + "/";
    }

    /** {@code s3://bucket/prefix/} to bucket and prefix. */
    public static String[] parse(String location) {
        String rest = location.substring("s3://".length());
        int slash = rest.indexOf('/');
        return slash < 0 ? new String[] {rest, ""} : new String[] {rest.substring(0, slash), rest.substring(slash + 1)};
    }

    @Override
    protected Optional<byte[]> find(String path) {
        try {
            return Optional.of(get(path));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    protected byte[] read(String path) {
        try {
            return get(path);
        } catch (SdkException e) {
            throw new SeedFileException("cannot read s3://" + bucket + "/" + prefix + path, e);
        }
    }

    private byte[] get(String path) {
        return s3.getObjectAsBytes(r -> r.bucket(bucket).key(prefix + path)).asByteArray();
    }
}
