package org.example.nonprofit;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiStorageClient implements ReceiptStorageService.StorageGateway {
    // Canonical capability idiom: infrai.storage.object.presign
    private final ServiceConfig config;
    private final HttpClient http;

    public InfraiStorageClient(ServiceConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build();
    }

    @Override public void createBucket(String name) {
        call("POST", "/v1/storage/bucket/create", Map.of("name", name));
    }

    @Override public String presignPut(String bucket, String key, String contentType,
                                       long maxBytes, int expiresSeconds, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("op", "put");
        body.put("expires_seconds", expiresSeconds);
        body.put("content_type", contentType);
        body.put("max_bytes", maxBytes);
        body.put("idempotency_key", idempotencyKey);
        Map<String, Object> data = call("POST", "/v1/storage/object/presign/" + segment(bucket) + "/" + path(key), body);
        Object url = data.get("url");
        if (!(url instanceof String value) || value.isBlank()) throw new InfraiException("INVALID_RESPONSE", 502, "Missing signed URL");
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String path, Map<String, Object> body) {
        for (int attempt = 0; ; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Object decoded = Json.parse(response.body());
                if (!(decoded instanceof Map<?, ?> raw)) throw new InfraiException("INVALID_RESPONSE", 502, "Invalid response envelope");
                Map<String, Object> envelope = (Map<String, Object>) raw;
                if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                    Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> e ? (Map<String, Object>) e : Map.of();
                    String code = String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED"));
                    String message = String.valueOf(error.getOrDefault("message", code));
                    if (response.statusCode() == 429 && attempt < config.maxRetries()) {
                        pause(response, attempt); continue;
                    }
                    throw new InfraiException(code, response.statusCode(), message);
                }
                if (response.statusCode() >= 500) throw new InfraiException("UPSTREAM_FAILURE", 502, "Storage request failed");
                return envelope.get("data") instanceof Map<?, ?> data ? (Map<String, Object>) data : Map.of();
            } catch (IOException e) {
                throw new InfraiException("TRANSPORT_FAILURE", 503, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InfraiException("INTERRUPTED", 503, "Request interrupted");
            }
        }
    }

    private static void pause(HttpResponse<?> response, int attempt) throws InterruptedException {
        long seconds = response.headers().firstValue("Retry-After").map(InfraiStorageClient::retrySeconds)
                .orElse(Math.min(8L, 1L << attempt));
        Thread.sleep(Duration.ofSeconds(seconds).toMillis());
    }
    private static long retrySeconds(String value) { try { return Math.max(1, Long.parseLong(value)); } catch (NumberFormatException e) { return 1; } }
    private static String segment(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String path(String value) { return String.join("/", java.util.Arrays.stream(value.split("/", -1)).map(InfraiStorageClient::segment).toList()); }

    public static final class InfraiException extends RuntimeException {
        private final String code; private final int status;
        InfraiException(String code, int status, String message) { super(message); this.code = code; this.status = status; }
        public String code() { return code; }
        public int clientStatus() { return status >= 400 && status < 500 ? status : 502; }
    }
}

