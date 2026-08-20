package org.example.nonprofit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReceiptUploadServer {
    private ReceiptUploadServer() {}
    public static void main(String[] args) throws IOException {
        ServiceConfig config = ServiceConfig.fromEnvironment(System.getenv());
        ReceiptStorageService service = new ReceiptStorageService(new InfraiStorageClient(config), config.expiresSeconds());
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/receipts", exchange -> handle(exchange, service));
        server.start();
        System.out.println("Receipt upload service listening on http://localhost:" + config.port());
    }

    @SuppressWarnings("unchecked")
    private static void handle(HttpExchange exchange, ReceiptStorageService service) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { respond(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        try {
            Object parsed = Json.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> raw)) throw new IllegalArgumentException("JSON object required");
            Map<String, Object> in = (Map<String, Object>) raw;
            ReceiptStorageService.ReceiptRequest request = new ReceiptStorageService.ReceiptRequest(
                    string(in, "tenantId"), string(in, "donorId"), string(in, "receiptId"), string(in, "contentType"), number(in, "bytes"));
            ReceiptStorageService.UploadInstruction result = service.prepareReceipt(request);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("bucket", result.bucket()); out.put("key", result.key()); out.put("uploadUrl", result.uploadUrl());
            out.put("method", result.method()); out.put("expiresSeconds", result.expiresSeconds());
            respond(exchange, 200, out);
        } catch (InfraiStorageClient.InfraiException e) {
            respond(exchange, e.clientStatus(), Map.of("error", e.code(), "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, Map.of("error", "invalid_request", "message", e.getMessage()));
        }
    }
    private static String string(Map<String, Object> map, String key) { Object v = map.get(key); if (!(v instanceof String s)) throw new IllegalArgumentException(key + " is required"); return s; }
    private static long number(Map<String, Object> map, String key) { Object v = map.get(key); if (!(v instanceof Number n)) throw new IllegalArgumentException(key + " is required"); return n.longValue(); }
    private static void respond(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes); exchange.close();
    }
}

