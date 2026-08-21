package io.javalibs.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinioObjectStorageIT {

    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-12-18T13-15-44Z");
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static MinioObjectStorage storage;

    @BeforeAll
    static void start() {
        MINIO.start();
        storage = new MinioObjectStorage(MINIO.getS3URL(), MINIO.getS3URL(),
                MINIO.getUserName(), MINIO.getPassword(), "it-bucket",
                Duration.ofMinutes(10), Duration.ofMinutes(5));
        storage.ensureBucket();
    }

    @AfterAll
    static void stop() {
        MINIO.stop();
    }

    @Test
    void putStatGetDeleteRoundTrip() throws Exception {
        byte[] bytes = "nội dung".getBytes(StandardCharsets.UTF_8);
        storage.put("a/b/c.pdf", "application/pdf", bytes.length, new ByteArrayInputStream(bytes));

        assertThat(storage.stat("a/b/c.pdf")).hasValueSatisfying(s -> {
            assertThat(s.size()).isEqualTo(bytes.length);
            assertThat(s.contentType()).isEqualTo("application/pdf");
        });
        try (var in = storage.get("a/b/c.pdf")) {
            assertThat(in.readAllBytes()).isEqualTo(bytes);
        }

        storage.delete("a/b/c.pdf");
        assertThat(storage.stat("a/b/c.pdf")).isEmpty();
    }

    @Test
    void statIsEmptyAndGetThrowsForMissingKey() {
        assertThat(storage.stat("missing/x.pdf")).isEmpty();
        assertThatThrownBy(() -> storage.get("missing/x.pdf")).isInstanceOf(StorageException.class);
    }

    @Test
    void deleteMissingKeyIsNoOp() {
        storage.delete("missing/y.pdf");
    }

    @Test
    void presignPutAcceptsPlainHttpUpload() throws Exception {
        String url = storage.presignPut("presigned/a.txt");
        HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "text/plain")
                        .PUT(HttpRequest.BodyPublishers.ofString("hello")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(storage.stat("presigned/a.txt")).hasValueSatisfying(s -> {
            assertThat(s.size()).isEqualTo(5);
            assertThat(s.contentType()).isEqualTo("text/plain");
        });
    }

    @Test
    void presignGetServesContentWithDisposition() throws Exception {
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
        storage.put("dl/b.txt", "text/plain", bytes.length, new ByteArrayInputStream(bytes));
        String url = storage.presignGet("dl/b.txt", ContentDispositions.attachment("báo cáo.txt"));
        HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isEqualTo("abc");
        assertThat(res.headers().firstValue("Content-Disposition").orElseThrow())
                .contains("attachment").contains("filename*=UTF-8''");
    }
}
