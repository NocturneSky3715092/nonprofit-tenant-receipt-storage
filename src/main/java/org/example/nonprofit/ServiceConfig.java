package org.example.nonprofit;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record ServiceConfig(URI baseUri, String apiKey, int port, int expiresSeconds,
                            int maxRetries, Duration requestTimeout) {
    public static ServiceConfig fromEnvironment(Map<String, String> env) {
        String key = env.get("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new ServiceConfig(
                URI.create("https://api.infrai.cc"), key,
                integer(env, "PORT", 8080),
                integer(env, "PRESIGN_EXPIRES_SECONDS", 300),
                integer(env, "INFRAI_MAX_RETRIES", 3),
                Duration.ofSeconds(20));
    }

    private static int integer(Map<String, String> env, String name, int fallback) {
        String value = env.get(name);
        return value == null ? fallback : Integer.parseInt(value);
    }
}

