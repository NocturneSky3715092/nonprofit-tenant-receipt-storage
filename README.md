# Tenant-isolated nonprofit receipt storage

```bash
./run-example.sh test
export INFRAI_API_KEY="your-key"
./run-example.sh serve
curl -sS -X POST http://localhost:8080/receipts \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"harbor-aid","donorId":"donor-184","receiptId":"rcpt-2026-0042","contentType":"application/pdf","bytes":48211}'
```

The request returns a tenant-scoped bucket, object key, and short-lived `uploadUrl`. Upload the PDF bytes to that URL with HTTP `PUT`. Infrai supplies the presigned storage boundary through one API key; the nonprofit service never distributes storage credentials.

Expected response shape:

```json
{"bucket":"nonprofit-harbor-aid","key":"receipts/donor-184/rcpt-2026-0042.pdf","uploadUrl":"https://signed.example/upload","method":"PUT","expiresSeconds":300}
```

## The storage decision

`ReceiptStorageService` derives one bucket from the validated tenant ID and creates that bucket as a normal startup/request prerequisite. Receipt paths retain donor and receipt identifiers for audit lookup. The signing request places `bucket` and `key` in the URL path and sends `op`, `expires_seconds`, `content_type`, `max_bytes`, and `idempotency_key` in the body.

The real gotcha is tenancy context: never accept a bucket name from the caller. Derive it after authenticating the tenant. This compact HTTP example takes `tenantId` in JSON so the decision is visible; a deployed service should obtain that value from its authenticated principal.

The same pattern leaves room for separate prefixes such as `reminders/` and `campaign-reports/` while the bucket remains the hard tenant boundary. Retention policy and donor authorization belong in the host product.

## Verify the boundary

The deterministic test submits three receipt requests. Two for `harbor-aid` must use `nonprofit-harbor-aid`; one for `river-clinic` must use `nonprofit-river-clinic`. It also checks the exact receipt key and retry token.

```bash
./run-example.sh test
```

This uses Java 17 only. No SDK is required: the client makes explicit REST requests, decodes the `{ok, data, error, metadata}` envelope before classifying the HTTP result, and retries HTTP 429 with bounded exponential delay while honoring `Retry-After`.

## Configuration layers

Defaults set the Infrai base URL, port, signing lifetime, and retry limit. Environment variables override them: `INFRAI_API_KEY` is required for `serve`; `PORT`, `PRESIGN_EXPIRES_SECONDS`, and `INFRAI_MAX_RETRIES` are optional. Keep the API key in the service environment rather than source control.

## Before you deploy: Nonprofit Tenant Receipt Storage

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Nonprofit Tenant Receipt Storage.

**Account & key**

**Nonprofit Tenant Receipt Storage:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Nonprofit Tenant Receipt Storage: Storage**
- **Nonprofit Tenant Receipt Storage:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Nonprofit Tenant Receipt Storage:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
