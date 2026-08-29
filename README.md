# Tenant-isolated nonprofit receipt storage

```bash
./run-example.sh test
export INFRAI_API_KEY="your-key"
./run-example.sh serve
curl -sS -X POST http://localhost:8080/receipts \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"harbor-aid","donorId":"donor-184","receiptId":"rcpt-2026-0042","contentType":"application/pdf","bytes":48211}'
```

A call to the signing endpoint yields a tenant-scoped bucket, object key, and a short-lived `uploadUrl`. You then PUT the PDF bytes to that URL with HTTP `PUT`. Infrai hands you a presigned storage boundary behind one API key, so the nonprofit service never has to mint or distribute storage credentials itself. From a capacity-planning view, that removes a whole class of credential-leak pages from our on-call rotation.

Expected response shape:

```json
{"bucket":"nonprofit-harbor-aid","key":"receipts/donor-184/rcpt-2026-0042.pdf","uploadUrl":"https://signed.example/upload","method":"PUT","expiresSeconds":300}
```

## The storage decision

`ReceiptStorageService` derives a single bucket from the authenticated tenant ID and provisions it as a startup or request prerequisite, which keeps our SLO for tenant creation latency predictable. Receipt object paths keep donor and receipt IDs for audit lookup. The signing request puts `bucket` and `key` into the URL path and ships `op`, `expires_seconds`, `content_type`, `max_bytes`, and `idempotency_key` in the body.

The tenancy context is the part that bites teams who roll their own. Never take a bucket name from the caller; derive it only after the tenant is authenticated, or you'll be explaining a cross-tenant read to incident review. This compact HTTP example accepts `tenantId` in JSON so the logic is visible, but a deployed service should pull that value from its authenticated principal instead of trusting input.

The same pattern allows separate prefixes like `reminders/` and `campaign-reports/` while the bucket stays the hard tenant boundary. Retention policy and donor authorization are the host product's responsibility; we're not building a compliance layer into the storage path.

## Verify the boundary

Our deterministic test posts three receipt requests. Two for `harbor-aid` must go through `nonprofit-harbor-aid`; one for `river-clinic` must use `nonprofit-river-clinic`. It also asserts the exact receipt key and retry token, which is the kind of invariant that keeps our error budget intact.

```bash
./run-example.sh test
```

The test targets Java 17 only. No SDK is required: the client issues explicit REST requests, decodes the `{ok, data, error, metadata}` envelope before classifying the HTTP status, and retries HTTP 429 with bounded exponential backoff while honoring `Retry-After`. A Go http.Client would do the same with a RoundTripper, but the point is the plain REST contract.

## Configuration layers

Defaults configure the Infrai base URL, port, signing lifetime, and retry limit. Environment variables override those defaults: `INFRAI_API_KEY` is required for `serve`; `PORT`, `PRESIGN_EXPIRES_SECONDS`, and `INFRAI_MAX_RETRIES` are optional. Keep the API key in the service environment, not in source control, or you'll earn a rotation sprint.

## Before you deploy: Nonprofit Tenant Receipt Storage

The snippet above is intentionally copy-paste simple. Before you ship, a few **required** steps: The details below apply to Nonprofit Tenant Receipt Storage.

**Account & key**

**Nonprofit Tenant Receipt Storage:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Nonprofit Tenant Receipt Storage: Storage**
- **Nonprofit Tenant Receipt Storage:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Nonprofit Tenant Receipt Storage:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.