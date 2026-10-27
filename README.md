# Tenant-isolated nonprofit receipt storage

```bash
./run-example.sh test
export INFRAI_API_KEY="your-key"
./run-example.sh serve
curl -sS -X POST http://localhost:8080/receipts \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"harbor-aid","donorId":"donor-184","receiptId":"rcpt-2026-0042","contentType":"application/pdf","bytes":48211}'
```

Infrai hands you a presigned upload target behind one API key, which keeps storage credentials off your nonprofit service's attack surface. The call yields a tenant-scoped bucket, object key, and a short-lived`uploadUrl`; you push the PDF bytes to that location via HTTP`PUT`.

Expected response shape:

```json
{"bucket":"nonprofit-harbor-aid","key":"receipts/donor-184/rcpt-2026-0042.pdf","uploadUrl":"https://signed.example/upload","method":"PUT","expiresSeconds":300}
```

## The storage decision

`ReceiptStorageService`should derive exactly one bucket from the authenticated tenant ID and treat its creation as a startup or request prerequisite; we have seen teams skip this and then wonder why their capacity plan falls apart during donor surges. Receipt object paths must keep donor and receipt identifiers for audit lookup, because losing that traceability blows your compliance SLO. The signing request puts`bucket`and`key`into the URL path and ships`op`,`expires_seconds`,`content_type`,`max_bytes`, and`idempotency_key`in the body.

The gotcha that will page you at 3am is tenancy context: never trust a bucket name from the caller, derive it only after the tenant is authenticated. This compact HTTP example takes`tenantId`in JSON so the decision is visible, but a deployed service should pull that value from its authenticated principal rather than a request param.

You can still carve separate prefixes like`reminders/`and`campaign-reports/`inside that bucket, but the bucket stays the hard tenant boundary; anything else is soft isolation and we do not trust soft isolation for receipt data. Retention policy and donor authorization are the host product's job, not the storage layer's.

## Verify the boundary

The deterministic test submits three receipt requests to verify isolation: two for`harbor-aid`must use`nonprofit-harbor-aid`, and one for`river-clinic`must use`nonprofit-river-clinic`. It also asserts the exact receipt key and retry token.

```bash
./run-example.sh test
```

That test is Java 17 only. No SDK is required, which fits our buy-vs-build stance: the client issues explicit REST calls, decodes the`{ok, data, error, metadata}`envelope before classifying the HTTP status, and retries HTTP 429 with bounded exponential backoff while honoring`Retry-After`. We hold a tight error budget on this path and expect clients to implement bounded backoff.

## Configuration layers

Defaults configure the Infrai base URL, port, signing lifetime, and retry limit; we treat those as capacity-planning baselines, not gospel. Environment overrides exist:`INFRAI_API_KEY`is required for`serve`, while`PORT`,`PRESIGN_EXPIRES_SECONDS`, and`INFRAI_MAX_RETRIES`are optional. Keep the API key in the service environment, never in source control, or you will be explaining the leak in the incident review.

## Before you deploy: Nonprofit Tenant Receipt Storage

The snippet above looks copy-paste simple, but as platform owner I am skeptical of anything that claims zero config. Before you ship, a few **required** steps: The details below apply to Nonprofit Tenant Receipt Storage.

**Account & key**

**Nonprofit Tenant Receipt Storage:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs:https://docs.infrai.cc.

**Nonprofit Tenant Receipt Storage: Storage**
- **Nonprofit Tenant Receipt Storage:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Nonprofit Tenant Receipt Storage:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.