# 05 — MinIO Storage

## 1. Purpose
Validate and clarify MinIO storage configuration for browser uploads, backend storage operations, and OnlyOffice document access while preserving the `StoragePort` architecture.

## 2. Source Errors From erreurs.md
- Section 4: Storage/env variables
- Section 5: `.env` variables `MINIO_ENDPOINT`, `MINIO_PUBLIC_ENDPOINT`, `STORAGE_PROVIDER`
- Section 9: MinIO URL compatibility
- Section 12: Storage/provider mismatch
- Section 11: `noop.fos.local`

## 3. Classification
- Clarify env templates and README storage modes: Agent-fixable.
- Verify all storage goes through `StoragePort`: Agent-fixable.
- Real MinIO object/document testing: Manual-only; see MANUAL-008 and MANUAL-010.
- LAN reachability for public endpoint: Manual-only when needed; see MANUAL-005.

## 4. Files Allowed To Modify
- `.env.example`
- `.env.dev.example`
- `README.md`
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `fos-workspace-service/src/main/resources/application.yml`
- `fos-workspace-service/src/main/resources/application-dev.yml`
- Storage tests under `fos-sdk/sdk-storage/src/test/java/`

## 5. Files Forbidden To Touch
- Global forbidden folders: `report/`, `reports/`, `rapport/`, `rapports/`, `target/`, `node_modules/`, `dist/`, `.git/`
- Do not bypass `StoragePort`.
- Do not introduce raw storage clients into workspace domain/application code.
- Do not delete MinIO volumes or objects.

## 6. Current Problem Summary
Full compose hardcodes `STORAGE_PROVIDER=minio`, while dev profile defaults to `noop`. Public MinIO endpoints are intended for browser and OnlyOffice but need clear mode-specific documentation. `noop.fos.local` is a deliberate dev placeholder but cannot support real editing.

## 7. Target State
Storage modes are explicit: `noop` for lightweight host dev, `minio` for real document upload/editing. Backend uses internal MinIO endpoint, browser/OnlyOffice use public endpoint, and all access remains behind `StoragePort`.

## 8. Step-by-Step Execution Plan
### Step 1: Document storage modes in env templates
- Objective: Make `STORAGE_PROVIDER` and MinIO endpoints clear.
- Files to inspect: `.env.example`, `.env.dev.example`, workspace application YAML
- Files to modify: `.env.example`, `.env.dev.example`, `README.md`
- Exact change to make: Add comments explaining `STORAGE_PROVIDER=noop` for metadata-only dev and `STORAGE_PROVIDER=minio` for real upload/editing. Keep `MINIO_ENDPOINT=http://minio:9000` for Docker containers and `MINIO_PUBLIC_ENDPOINT=http://host.docker.internal:9000` for browser/OnlyOffice in Docker.
- Safety rule: Do not add real credentials.
- Verification command: `Select-String -Path .env.example -Pattern "STORAGE_PROVIDER|MINIO_ENDPOINT|MINIO_PUBLIC_ENDPOINT"`
- Expected result: All three variables are present and documented.
- What to do if verification fails: Re-add missing variables/comments.

### Step 2: Verify StoragePort-only architecture
- Objective: Ensure workspace does not instantiate MinIO directly.
- Files to inspect: `fos-workspace-service/src/main/java`, `fos-sdk/sdk-storage/src/main/java`
- Files to modify: None unless a violation is found
- Exact change to make: Run search for direct MinIO client use in workspace. If found, refactor through `StoragePort`; otherwise record pass.
- Safety rule: No storage implementation changes unless direct misuse is found.
- Verification command: `rg -n "MinioClient|S3Client|BlobClient|StoragePort" fos-workspace-service\\src\\main\\java fos-sdk\\sdk-storage\\src\\main\\java`
- Expected result: Raw clients appear only in SDK storage adapters.
- What to do if verification fails: Stop and create a focused refactor task.

### Step 3: Align bucket names by mode
- Objective: Avoid confusion between `fos-workspace`, `fos-workspace-dev`, and defaults.
- Files to inspect: workspace application YAML, `.env.example`, Docker compose env
- Files to modify: `.env.example`, README, possibly compose env
- Exact change to make: Document full Docker bucket as `fos-workspace-staging` or current compose bucket behavior, and dev bucket as `fos-workspace-dev`. If consistency is desired, add `MINIO_BUCKET` to compose/env templates.
- Safety rule: Do not rename existing buckets or delete objects automatically.
- Verification command: `docker compose config | Select-String "MINIO|STORAGE|BUCKET"`
- Expected result: Bucket behavior is visible or documented.
- What to do if verification fails: Keep current bucket defaults and document them.

### Step 4: Prepare real document smoke test instructions
- Objective: Enable final OnlyOffice verification once user provides data.
- Files to inspect: README and manual-fixes
- Files to modify: `README.md` if needed
- Exact change to make: Add a short checklist for verifying a document object exists in MinIO and a backend document has a current version.
- Safety rule: Do not seed database or upload files unless user explicitly asks.
- Verification command: `docker compose config`
- Expected result: Documentation-only change does not break config.
- What to do if verification fails: Revert docs/config changes.

## 9. Verification Commands
- `Select-String -Path .env.example -Pattern "STORAGE_PROVIDER|MINIO_ENDPOINT|MINIO_PUBLIC_ENDPOINT|MINIO_BUCKET"`
- `rg -n "MinioClient|S3Client|BlobClient|StoragePort" fos-workspace-service\\src\\main\\java fos-sdk\\sdk-storage\\src\\main\\java`
- `docker compose config`
- `mvn -pl fos-sdk/sdk-storage -am -DskipTests compile`
- `mvn -pl fos-workspace-service -am -DskipTests compile`

## 10. Acceptance Criteria
- [ ] Storage mode is documented in committed env templates.
- [ ] No workspace domain/application code bypasses `StoragePort`.
- [ ] MinIO internal and public endpoints are clearly separated.
- [ ] Real document editing tests are marked as requiring user-provided object/document IDs.

## 11. Rollback Plan
Revert env template and README changes. Do not delete buckets, objects, or volumes.

## 12. Notes For The Execution Agent
OnlyOffice depends on this plan for document URL reachability. Do not change storage adapters unless verification finds a concrete issue.
