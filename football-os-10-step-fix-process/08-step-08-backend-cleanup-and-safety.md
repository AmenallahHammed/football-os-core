# Step 08 — Backend Cleanup And Safety

## 1. Purpose
Clean backend safety issues not owned by the auth, gateway, storage, or OnlyOffice steps. This step fixes host-run script dependency behavior, bounds dev/no-auth fallback UUIDs, verifies architecture constraints, preserves Mongo/Mongock rules, protects Kafka consumer patterns, and keeps Maven versions unchanged.

## 2. Errors Covered
- Host-run scripts may omit Maven `-am`, failing on clean machines.
- Fallback UUIDs must be clearly dev/no-auth only.
- Backend architecture constraints need verification after earlier fixes.
- Mongo schema/index changes must not bypass Mongock.
- Kafka consumers must continue through `AbstractFosConsumer`.
- Maven version `0.1.0-SNAPSHOT` must be preserved.

## 3. Files To Inspect
- `run-gateway.sh`
- `run-governance.sh`
- `run-workspace.sh`
- `Makefile`
- `README.md`
- `pom.xml`
- `fos-gateway/pom.xml`
- `fos-governance-service/pom.xml`
- `fos-workspace-service/pom.xml`
- `fos-sdk/**/pom.xml`
- `fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/event/application/EventService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java`
- `fos-workspace-service/src/main/java/`
- `fos-governance-service/src/main/java/`

## 4. Files Allowed To Modify
- `run-gateway.sh`
- `run-governance.sh`
- `run-workspace.sh`
- `Makefile`
- `README.md`
- `fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/event/application/EventService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java`
- Related backend tests if fallback naming/config changes require them.

## 5. Files Forbidden To Modify
- `report/`
- `reports/`
- `rapport/`
- `rapports/`
- `target/`
- `node_modules/`
- `dist/`
- `.git/`
- Any `pom.xml` version value.
- Mongo schema/index definitions outside Mongock migration files.
- `AI_CONTEXT/` or `diagrams/` deletion without explicit user confirmation.

## 6. Automatic Fixes To Perform
1. Add `-am` to host-run Maven scripts.
   - Objective: Make module run scripts work on clean workstations.
   - Exact implementation instruction: In `run-gateway.sh`, `run-governance.sh`, `run-workspace.sh`, and relevant Makefile targets, ensure Maven commands using `-pl` also include `-am`, for example `mvn -pl fos-workspace-service -am spring-boot:run`.
   - Safety rule: Do not change ports, profiles, or module names unless they are already wrong.
   - Verification command: `rg -n "mvn| -pl |--projects|-am" run-gateway.sh run-governance.sh run-workspace.sh Makefile`
   - Expected result: Every module-specific Maven run command includes `-am`.
   - Stop condition if it fails: Stop if scripts use a non-Maven runner or generated content.

2. Bound dev/no-auth fallback UUIDs.
   - Objective: Prevent fallback IDs from appearing production-like.
   - Exact implementation instruction: Rename constants/comments so fallback IDs clearly say local no-auth development fallback. If simple and safe, expose them through dev-only properties such as `fos.dev.fallback-club-id`, `fos.dev.fallback-team-id`, and `fos.dev.fallback-actor-id`.
   - Safety rule: Do not remove fallbacks required by no-auth local development and do not invent real IDs.
   - Verification command: `mvn -pl fos-workspace-service -am -DskipTests compile`
   - Expected result: Workspace compiles and fallback usage is visibly dev/no-auth only.
   - Stop condition if it fails: Record `MANUAL-003` if real IDs are required.

3. Verify backend architecture constraints.
   - Objective: Catch direct infra/client violations before final verification.
   - Exact implementation instruction: Search backend source for raw `MongoClient`, workspace imports of `com.fos.governance`, direct `OpaClient`, direct storage clients, and Kafka listeners not using the shared consumer pattern.
   - Safety rule: Do not perform broad refactors in this step; fix only small obvious violations.
   - Verification command: `rg -n "MongoClient|com\\.fos\\.governance|OpaClient|MinioClient|S3Client|@KafkaListener|extends AbstractFosConsumer" fos-workspace-service/src/main/java fos-governance-service/src/main/java`
   - Expected result: No forbidden domain/application usage; Kafka consumers remain compatible with `AbstractFosConsumer`.
   - Stop condition if it fails: Stop and report exact file/line violations.

4. Verify Mongo/Mongock constraints.
   - Objective: Ensure schema/index changes do not bypass migrations.
   - Exact implementation instruction: If this step or earlier steps require Mongo schema/index changes, implement them only through Mongock. Otherwise run a static search and record no schema change.
   - Safety rule: Do not change Mongo schemas outside Mongock migration files.
   - Verification command: `rg -n "Indexed|CompoundIndex|MongoTemplate|MongoClient|mongock|ChangeUnit" fos-workspace-service/src/main/java fos-governance-service/src/main/java`
   - Expected result: Any schema/index migration need is visible and handled through Mongock.
   - Stop condition if it fails: Stop if a schema/index change is needed but no Mongock pattern is available.

5. Preserve Maven versions.
   - Objective: Ensure cleanup does not alter project versioning.
   - Exact implementation instruction: Search POM files and confirm versions remain `0.1.0-SNAPSHOT`. Do not edit version fields.
   - Safety rule: Never change Maven version in this process.
   - Verification command: `rg -n "<version>0\\.1\\.0-SNAPSHOT</version>|<version>" -g "pom.xml"`
   - Expected result: Project versions remain `0.1.0-SNAPSHOT`.
   - Stop condition if it fails: Stop and report any changed or unexpected version before editing.

## 7. Manual-Only Blockers
- `MANUAL-003`: User must provide real club/team/actor UUIDs if backend behavior cannot rely on dev-only fallbacks.

## 8. Verification Commands
- `rg -n "mvn| -pl |--projects|-am" run-gateway.sh run-governance.sh run-workspace.sh Makefile`
- `mvn -pl fos-workspace-service -am -DskipTests compile`
- `mvn compile -DskipTests`
- `rg -n "MongoClient|com\\.fos\\.governance|OpaClient|MinioClient|S3Client|@KafkaListener|extends AbstractFosConsumer" fos-workspace-service/src/main/java fos-governance-service/src/main/java`
- `rg -n "Indexed|CompoundIndex|MongoTemplate|MongoClient|mongock|ChangeUnit" fos-workspace-service/src/main/java fos-governance-service/src/main/java`
- `rg -n "<version>0\\.1\\.0-SNAPSHOT</version>|<version>" -g "pom.xml"`
- `git status --short`

## 9. Acceptance Criteria
- [ ] Host-run scripts include `-am` for module-specific Maven commands.
- [ ] Fallback UUIDs are clearly dev/no-auth only or configurable as dev fallback properties.
- [ ] Workspace does not depend on governance internals.
- [ ] Storage, policy, Kafka, and Mongo architecture rules remain satisfied.
- [ ] Maven version `0.1.0-SNAPSHOT` remains unchanged.
- [ ] No Mongo schema/index change bypasses Mongock.

## 10. Documentation To Update
Update `README.md` with host-run script behavior, dev/no-auth fallback meaning, architecture constraints, and any backend cleanup notes. Do not document fake IDs as production-ready values.

## 11. Rollback Plan
Revert run scripts, Makefile, README, backend service files, and tests changed in this step. Do not alter Maven versions, Mongo data, Kafka topics, generated folders, or local databases.
