# File Upload Local Troubleshooting

This note documents the local file upload investigation and fix from June 5, 2026.
It is intentionally scoped to local `dev` runs of Football OS.

## Correct Upload Flow

The frontend upload sequence must remain:

```text
1. POST /api/v1/documents/upload/initiate through the gateway
2. PUT the file binary to the exact presigned MinIO URL returned by the backend
3. POST /api/v1/documents/upload/confirm through the gateway
```

The frontend must not build MinIO URLs manually, must not call `confirm` if the
MinIO `PUT` fails, and must not attach the application bearer token to presigned
MinIO URLs.

## Observed Failure

The failing request was:

```text
POST http://localhost:8080/api/v1/documents/upload/initiate
```

The request reached the gateway and workspace service, but the workspace service
returned `500` before an `uploadUrl` was produced.

The workspace logs contained:

```text
SignatureDoesNotMatch
```

Because `initiate` failed before returning a presigned URL:

- no MinIO `PUT` request could happen
- no `confirm` request happened
- the old skipped-PUT frontend bug had not returned

## Root Cause

The root cause was local Spring Boot configuration loading.

The shared `.env` file lives at the repository root. Spring Boot services can be
started either from the repository root or from an individual service module.
When `fos-workspace-service` was launched from its module directory, the
repository-root `.env` was not loaded. MinIO configuration then partially fell
back to defaults, which caused the MinIO request signature to be generated with
the wrong local credentials.

MinIO rejected the request with `SignatureDoesNotMatch` during upload initiation.

This was not caused by:

- the frontend upload sequence
- the Angular auth interceptor
- a stale browser-facing MinIO IP address
- MinIO CORS
- a confirm/object-key mismatch

## Config Fix

The local `dev` profiles now import both the repository-root `.env` path when a
service is launched from a module directory and the normal current-directory
`.env` path:

```yaml
spring:
  config:
    import:
      - optional:file:../.env[.properties]
      - optional:file:.env[.properties]
```

Files updated:

- `fos-workspace-service/src/main/resources/application-dev.yml`
- `fos-gateway/src/main/resources/application-dev.yml`
- `fos-governance-service/src/main/resources/application-dev.yml`
- `LOCAL_RUN.md`

The `LOCAL_RUN.md` note was updated to state that local Spring Boot `dev`
profiles import the repository-root `.env` even when services are launched from
module directories.

## Current Local Values

Do not print or commit MinIO secrets. The relevant non-secret local values are:

```text
Angular API base URL:        http://localhost:8080
Internal MinIO endpoint:     http://localhost:9000
Browser MinIO endpoint:      http://localhost:9000
MinIO bucket:                fos-workspace
```

`http://localhost:9000` is appropriate for this local setup because MinIO port
`9000` is published to the host by Docker Compose.

## Frontend Safeguards Checked

The existing Angular code was inspected and left unchanged:

- upload logic performs `initiate -> PUT uploadUrl -> confirm`
- the `PUT` uses the exact `uploadUrl` returned by the backend
- `confirm` is only reached after the binary upload succeeds
- duplicate files are filtered before upload
- the auth interceptor only attaches the app bearer token to gateway API calls
- absolute MinIO presigned URLs are not rewritten through the gateway

## Verification Performed

After the config fix:

- `fos-workspace-service` was rebuilt and restarted with the `dev` profile
- upload initiation generated a presigned URL with host `localhost:9000`
- the exact returned MinIO URL accepted the file binary with HTTP `200`
- upload confirmation returned HTTP `200`
- the uploaded object existed in the `fos-workspace` bucket
- refreshing the frontend showed the uploaded document as `Active`
- workspace logs showed upload initiation and confirmation without
  `SignatureDoesNotMatch`

Backend checks run:

```powershell
mvn -pl fos-workspace-service -am -DskipTests compile
mvn -pl fos-workspace-service test
```

Both commands completed successfully. The workspace-service test run reported
49 tests, 0 failures, 0 errors, and 0 skipped.

## Browser Automation Note

The already-open Chrome tab was inspected, but automated file selection was
blocked by the Chrome extension permissions. To allow Codex-driven file uploads
through Chrome, open:

```text
chrome://extensions
```

Then open the Codex extension details and enable:

```text
Allow access to file URLs
```

See:

```text
https://developers.openai.com/codex/app/chrome-extension#upload-files
```

## If Uploads Break Again

Check these in order:

1. Confirm the frontend sends `POST /api/v1/documents/upload/initiate` through
   `http://localhost:8080`.
2. If `initiate` fails, inspect workspace logs before looking for a MinIO `PUT`.
3. If `initiate` succeeds, verify the returned `uploadUrl` host is browser
   reachable.
4. For this local setup, the returned MinIO URL should normally use
   `http://localhost:9000`.
5. Verify the MinIO `PUT` uses the exact returned URL and does not include an
   application bearer token.
6. Verify `confirm` is only sent after the MinIO `PUT` succeeds.
7. If `SignatureDoesNotMatch` appears again, confirm the service actually loaded
   the repository-root `.env` and that MinIO credentials match the running
   Docker Compose MinIO container.
