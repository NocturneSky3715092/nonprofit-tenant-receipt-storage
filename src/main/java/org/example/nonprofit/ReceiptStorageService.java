package org.example.nonprofit;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ReceiptStorageService {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,47}");
    private final StorageGateway storage;
    private final int expiresSeconds;

    public ReceiptStorageService(StorageGateway storage, int expiresSeconds) {
        this.storage = storage; this.expiresSeconds = expiresSeconds;
    }

    public UploadInstruction prepareReceipt(ReceiptRequest request) {
        String tenant = valid("tenantId", request.tenantId());
        String donor = valid("donorId", request.donorId());
        String receipt = valid("receiptId", request.receiptId());
        if (!"application/pdf".equals(request.contentType())) throw new IllegalArgumentException("contentType must be application/pdf");
        if (request.bytes() < 1 || request.bytes() > 10_000_000) throw new IllegalArgumentException("bytes must be between 1 and 10000000");
        String bucket = "nonprofit-" + tenant;
        String key = "receipts/" + donor + "/" + receipt + ".pdf";
        String retryToken = tenant + ":receipt:" + receipt;
        storage.createBucket(bucket);
        String url = storage.presignPut(bucket, key, request.contentType(), request.bytes(), expiresSeconds, retryToken);
        return new UploadInstruction(bucket, key, url, "PUT", expiresSeconds);
    }

    private static String valid(String field, String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (!ID.matcher(normalized).matches()) throw new IllegalArgumentException(field + " has an invalid format");
        return normalized;
    }

    public interface StorageGateway {
        void createBucket(String name);
        String presignPut(String bucket, String key, String contentType, long maxBytes, int expiresSeconds, String idempotencyKey);
    }
    public record ReceiptRequest(String tenantId, String donorId, String receiptId, String contentType, long bytes) {}
    public record UploadInstruction(String bucket, String key, String uploadUrl, String method, int expiresSeconds) {}
}

