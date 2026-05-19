# Step 05 — MinIO Storage Flow

## 1. Purpose
Clarify and verify MinIO storage behavior for backend storage, browser access, and OnlyOffice document access while preserving the `StoragePort` architecture.

## 2. Errors Covered
- `STORAGE_PROVIDER` modes are unclear between `noop` and `minio`.
- `MINIO_ENDPOINT` and `MINIO_PUBLIC_ENDPOINT` may be confused between container-internal and browser/public access.
- Bucket naming differs by mode and needs explicit documentation.
- Browser and OnlyOffice public URL reachability depends on host/LAN settings.
- Workspace must not bypass `StoragePort` or instantiate storage clients directly.
- MinIO buckets, objects, and volumes must not be deleted automatically.

## 3. Files To Inspect
- `.env.example`
- `.env.dev.example`
- `README.md`
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `fos-workspace-service/src/main/resources/application.yml`
- `fos-workspace-service/src/main/resources/application-dev.yml`
- `fos-workspace-service/src/main/java/`
- `fos-sdk/sdk-storage/src/main/java/`
- `fos-sdk/sdk-storage/src/test/java/`

## 4. Files Allowed To Modify
- `.env.example`
- `.env.dev.example`
- `README.md`
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `fos-workspace-service/src/main/resources/application.yml`
- `fos-workspace-service/src/main/resources/application-dev.yml`
- Storage tests under `fos-sdk/sdk-storage/src/test/java/`

## 5. Files Forbidden To Modify
- `.env`
- `.env.dev`
- `report/`
- `reports/`
- `rapport/`
- `rapports/`
- `target/`
- `node_modules/`
- `dist/`
- `.git/`
- MinIO buckets
- MinIO objects
- Docker volumes
- Workspace domain/application code that would bypass `StoragePort`.

## 6. Automatic Fixes To Perform
1. Document storage modes.
   - Objective: Make `noop` and `minio` modes explicit.
   - Exact implementation instruction: In `.env.example`, `.env.dev.example`, and README, document `STORAGE_PROVIDER=noop` as metadata-only/lightweight host development and `STORAGE_PROVIDER=minio` as real upload/download/OnlyOffice editing.
   - Safety rule: Do not add real access keys or secrets.
   - Verification command: `Select-String -Path .env.example,.env.dev.example,README.md -Pattern "STORAGE_PROVIDER|noop|minio"`
   - Expected result: Storage modes are visible in committed templates/docs.
   - Stop condition if it fails: Stop if `.env.dev.example` is absent because Step 01 did not run.

2. Separate internal and public MinIO endpoints.
   - Objective: Prevent backend, browser, and OnlyOffice endpoint confusion.
   - Exact implementation instruction: Document Docker-internal backend endpoint as `MINIO_ENDPOINT=http://minio:9000` and public/browser/OnlyOffice endpoint as `MINIO_PUBLIC_ENDPOINT=http://host.docker.internal:9000` for Docker-on-host local use. Document LAN override through `HOST_LAN_IP` only as manual.
   - Safety rule: Do not edit real `.env` or assume a LAN IP.
   - Verification command: `Select-String -Path .env.example,.env.dev.example,README.md -Pattern "MINIO_ENDPOINT|MINIO_PUBLIC_ENDPOINT|HOST_LAN_IP"`
   - Expected result: Internal and public endpoint roles are clearly separated.
   - Stop condition if it fails: Record `MANUAL-005` if public reachability requires LAN-specific values.

3. Clarify bucket naming.
   - Objective: Avoid confusion between local buckets such as `fos-workspace`, `fos-workspace-dev`, and `fos-workspace-staging`.
   - Exact implementation instruction: Ensure env templates and README document the configured `MINIO_BUCKET` per mode. If compose already sets a bucket, do not rename it; document current behavior.
   - Safety rule: Do not rename buckets or move/delete objects automatically.
   - Verification command: `docker compose config | Select-String "MINIO|STORAGE|BUCKET"`
   - Expected result: Bucket behavior is visible in config or documentation.
   - Stop condition if it fails: Keep current bucket defaults and document that real object checks require `MANUAL-008`.

4. Verify `StoragePort` architecture.
   - Objective: Ensure workspace does not instantiate storage clients directly.
   - Exact implementation instruction: Search workspace and SDK storage sources. Raw storage clients such as `MinioClient`, `S3Client`, or `BlobClient` must appear only in SDK storage adapters, not workspace domain/application code.
   - Safety rule: Do not bypass `StoragePort`.
   - Verification command: `rg -n "MinioClient|S3Client|BlobClient|StoragePort" fos-workspace-service/src/main/java fos-sdk/sdk-storage/src/main/java`
   - Expected result: Workspace uses `StoragePort`; raw clients are confined to storage adapters.
   - Stop condition if it fails: Stop and create a focused refactor task instead of broad storage rewrites.

5. Prepare real document smoke instructions.
   - Objective: Enable later OnlyOffice verification when real data exists.
   - Exact implementation instruction: Add README instructions requiring a real bucket, object, backend document UUID, and current version before running document editing smoke tests.
   - Safety rule: Do not seed databases or upload files unless the user separately asks.
   - Verification command: `Select-String -Path README.md -Pattern "MinIO|document UUID|bucket|object"`
   - Expected result: Real-data prerequisites are documented.
   - Stop condition if it fails: Record `MANUAL-008` and `MANUAL-010`.

## 7. Manual-Only Blockers
- `MANUAL-005`: User must set LAN IP for external device or LAN testing.
- `MANUAL-008`: User must verify real MinIO bucket/object for document editing.
- `MANUAL-010`: User must provide real document/team UUIDs for final smoke tests.

## 8. Verification Commands
- `Select-String -Path .env.example,.env.dev.example,README.md -Pattern "STORAGE_PROVIDER|MINIO_ENDPOINT|MINIO_PUBLIC_ENDPOINT|MINIO_BUCKET"`
- `docker compose config | Select-String "MINIO|STORAGE|BUCKET"`
- `rg -n "MinioClient|S3Client|BlobClient|StoragePort" fos-workspace-service/src/main/java fos-sdk/sdk-storage/src/main/java`
- `mvn -pl fos-sdk/sdk-storage -am -DskipTests compile`
- `mvn -pl fos-workspace-service -am -DskipTests compile`

## 9. Acceptance Criteria
- [ ] Storage modes are documented in committed templates/docs.
- [ ] `MINIO_ENDPOINT` is documented as backend/container internal.
- [ ] `MINIO_PUBLIC_ENDPOINT` is documented as browser/OnlyOffice reachable.
- [ ] Bucket naming is visible and not automatically changed.
- [ ] Workspace storage access remains through `StoragePort`.
- [ ] No MinIO buckets, objects, or volumes are deleted.

## 10. Documentation To Update
Update `README.md` with storage modes, endpoint roles, bucket naming, public reachability, real document smoke prerequisites, and the rule that storage access goes through `StoragePort`.

## 11. Rollback Plan
Revert env template, README, compose, workspace resource, and storage test changes from this step. Do not delete buckets, objects, Docker volumes, or local MinIO data.
