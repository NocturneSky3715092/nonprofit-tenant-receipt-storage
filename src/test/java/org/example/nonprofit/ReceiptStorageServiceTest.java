package org.example.nonprofit;

import java.util.ArrayList;
import java.util.List;

public final class ReceiptStorageServiceTest {
    public static void main(String[] args) {
        RecordingStorage storage = new RecordingStorage();
        ReceiptStorageService service = new ReceiptStorageService(storage, 300);
        var first = service.prepareReceipt(new ReceiptStorageService.ReceiptRequest("harbor-aid", "donor-184", "rcpt-2026-0042", "application/pdf", 48211));
        var retry = service.prepareReceipt(new ReceiptStorageService.ReceiptRequest("harbor-aid", "donor-184", "rcpt-2026-0042", "application/pdf", 48211));
        var other = service.prepareReceipt(new ReceiptStorageService.ReceiptRequest("river-clinic", "donor-9", "rcpt-2026-0077", "application/pdf", 900));
        check(first.bucket().equals("nonprofit-harbor-aid"), "first tenant bucket");
        check(retry.bucket().equals(first.bucket()), "stable tenant bucket");
        check(!other.bucket().equals(first.bucket()), "tenant isolation");
        check(first.key().equals("receipts/donor-184/rcpt-2026-0042.pdf"), "audit key");
        check(storage.tokens.get(0).equals(storage.tokens.get(1)), "stable retry token");
        System.out.println("PASS: receipt storage preserves tenant isolation and retry identity");
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
    private static final class RecordingStorage implements ReceiptStorageService.StorageGateway {
        final List<String> tokens = new ArrayList<>();
        public void createBucket(String name) {}
        public String presignPut(String bucket, String key, String contentType, long maxBytes, int expiresSeconds, String idempotencyKey) {
            tokens.add(idempotencyKey); return "https://signed.example/upload";
        }
    }
}

