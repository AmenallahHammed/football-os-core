# Manual Fixes Required

## Purpose
These are actions the coding agent cannot safely complete alone because they require local machine access, user-owned secrets, browser/session state, real test data, or human product judgment.

## Manual Fix Categories
- Docker / local machine
- Keycloak Admin UI
- Browser/session/token refresh
- Local secrets
- LAN/IP settings
- Human confirmation for deletion/cleanup
- Real data setup

### MANUAL-001: Start or unlock Docker Desktop
- Related plan: `02-docker-and-infrastructure-plan.md`, `09-verification-plan.md`
- Related error(s): Section 13 Docker verification notes
- Why the agent cannot do this: Docker Desktop and the Docker API are controlled by the user's machine/session.
- When to do this: Before running compose startup, health checks, or end-to-end verification.
- Exact step-by-step instructions for the user:
  1. Open Docker Desktop.
  2. Wait until it reports the engine is running.
  3. If Windows prompts for permissions, approve Docker access.
  4. In a terminal, run `docker compose ps`.
- How to verify it worked: `docker compose ps` returns a table instead of `permission denied while trying to connect to the docker API`.
- What screenshot/log/output to provide if it fails: Provide the Docker Desktop engine status and the full `docker compose ps` output.

### MANUAL-002: Verify or import Keycloak realm through Admin UI if automated import is not used
- Related plan: `03-keycloak-auth-security-plan.md`
- Related error(s): ERR-001
- Why the agent cannot do this: Admin UI login and import confirmation require user credentials and browser interaction.
- When to do this: After the agent creates a realm import file, or if the user chooses not to use compose auto-import.
- Exact step-by-step instructions for the user:
  1. Open `http://localhost:8180`.
  2. Log in with local admin credentials from your private `.env`.
  3. Confirm realm `fos` exists.
  4. Confirm public client `fos-workspace-frontend` exists.
  5. Confirm valid redirect URI includes `http://localhost:4200/*`.
  6. Confirm web origin includes `http://localhost:4200`.
  7. Confirm role and club claim mappers exist if the automated import is not active.
- How to verify it worked: `http://localhost:8180/realms/fos/.well-known/openid-configuration` returns JSON and the frontend login redirects correctly.
- What screenshot/log/output to provide if it fails: Screenshot of the realm/client settings and browser console/network error.

### MANUAL-003: Confirm real club UUID, team UUID, actor UUID, and role names
- Related plan: `03-keycloak-auth-security-plan.md`, `04-gateway-workspace-governance-plan.md`, `08-backend-cleanup-plan.md`
- Related error(s): ERR-004, Sections 6, 7, 11, 12
- Why the agent cannot do this: The correct real tenant/team/user identifiers are business data, not inferable from source code.
- When to do this: Before replacing fallback IDs in frontend or seeded local auth data.
- Exact step-by-step instructions for the user:
  1. Identify the real local club UUID to use for `fos_club_id`.
  2. Identify the default team UUID used by calendar event listing.
  3. Identify at least one actor UUID for a head coach/admin test user.
  4. Confirm backend policy role names, for example `ROLE_HEAD_COACH` and `ROLE_CLUB_ADMIN`.
  5. Provide these values to the execution agent.
- How to verify it worked: Authenticated API calls use real IDs and policy checks allow expected actions.
- What screenshot/log/output to provide if it fails: Token claims, API response body, and backend log line showing actor/role/club.

### MANUAL-004: Set user-owned local `.env` secrets
- Related plan: `01-env-and-profiles-plan.md`
- Related error(s): ERR-003, Sections 4, 5, 11
- Why the agent cannot do this: Real local secrets should be chosen and owned by the user, not generated blindly into ignored files unless explicitly requested.
- When to do this: After `.env.example` is fixed and before full compose startup.
- Exact step-by-step instructions for the user:
  1. Open local `.env`.
  2. Replace Keycloak admin password, webhook secret, OpenSearch password, and OnlyOffice JWT secret with values you own.
  3. Ensure `ONLYOFFICE_JWT_SECRET` is at least 32 bytes.
  4. Keep `.env` uncommitted and ignored.
- How to verify it worked: `docker compose config` shows non-empty values and `ONLYOFFICE_JWT_SECRET` is not `change-me-onlyoffice-secret`.
- What screenshot/log/output to provide if it fails: Redacted `.env` variable names and `docker compose config` excerpts with secrets masked.

### MANUAL-005: Set `HOST_LAN_IP` for LAN/mobile OnlyOffice testing
- Related plan: `01-env-and-profiles-plan.md`, `06-onlyoffice-plan.md`
- Related error(s): ERR-006, Section 9
- Why the agent cannot do this: The correct LAN IP depends on the user's network adapter and test device.
- When to do this: Only when testing from another device or when `localhost` is not reachable by the browser/device.
- Exact step-by-step instructions for the user:
  1. Find the machine LAN IPv4 address.
  2. Set `HOST_LAN_IP=<that IPv4>` in local `.env`.
  3. Ensure `ONLYOFFICE_PUBLIC_URL=http://${HOST_LAN_IP}:8084`.
  4. Restart affected containers.
- How to verify it worked: From the target device, open `http://<HOST_LAN_IP>:8084/web-apps/apps/api/documents/api.js`.
- What screenshot/log/output to provide if it fails: Browser error, machine IP output, and firewall/network profile status.

### MANUAL-006: Refresh browser session after Keycloak mapper/security changes
- Related plan: `03-keycloak-auth-security-plan.md`
- Related error(s): ERR-004
- Why the agent cannot do this: Browser session storage and active login state are user-local.
- When to do this: After changing Keycloak mappers, backend token parsing, or frontend auth settings.
- Exact step-by-step instructions for the user:
  1. Log out of Football OS.
  2. Clear site data for `localhost:4200` and `localhost:8180` if old tokens persist.
  3. Log in again.
  4. Retry the protected workflow.
- How to verify it worked: New access token contains expected role/club claims and API calls no longer return unexpected 401/403.
- What screenshot/log/output to provide if it fails: Browser network request, decoded token claims with secrets omitted, and gateway/workspace response.

### MANUAL-007: Confirm obsolete frontend folders can be deleted or archived
- Related plan: `07-frontend-cleanup-plan.md`
- Related error(s): Section 6, Section 10
- Why the agent cannot do this: Deleting old UI folders may remove design/reference work the user still wants.
- When to do this: Before any cleanup plan deletes or archives frontend mock folders.
- Exact step-by-step instructions for the user:
  1. Review `fos-workspace-frontend/src/app/features/calendar/`.
  2. Review `fos-workspace-frontend/src/app/shared/onlyoffice-editor/`.
  3. Review `fos-workspace-frontend/frontend-plan/`.
  4. Decide for each: keep, archive, or delete later.
  5. Provide the decision to the execution agent.
- How to verify it worked: The execution agent has explicit written confirmation before removing anything.
- What screenshot/log/output to provide if it fails: The exact folder path and intended keep/delete decision.

### MANUAL-008: Verify real MinIO objects for document editing tests
- Related plan: `05-minio-storage-plan.md`, `06-onlyoffice-plan.md`, `09-verification-plan.md`
- Related error(s): Section 9, Section 12
- Why the agent cannot do this: Real object contents and document IDs depend on local user test data.
- When to do this: Before testing actual OnlyOffice document opening and saving.
- Exact step-by-step instructions for the user:
  1. Open MinIO Console at `http://localhost:9001`.
  2. Confirm bucket `fos-workspace` or the configured bucket exists.
  3. Confirm a test document object exists.
  4. Provide the corresponding backend document UUID.
- How to verify it worked: The document download URL from `/api/v1/onlyoffice/config` returns the object when requested from browser and OnlyOffice can load it.
- What screenshot/log/output to provide if it fails: MinIO bucket/object screenshot and the backend document UUID.

### MANUAL-009: Decide whether OpenSearch belongs in minimal local stack
- Related plan: `02-docker-and-infrastructure-plan.md`
- Related error(s): Section 8, Section 10
- Why the agent cannot do this: This is a product/dev-experience decision about local resource usage.
- When to do this: Before changing full compose to remove, profile, or keep OpenSearch.
- Exact step-by-step instructions for the user:
  1. Decide whether local minimal setup should start OpenSearch by default.
  2. If no, approve moving it behind a compose profile or keeping it only as optional documentation.
  3. If yes, confirm which service uses it and why.
- How to verify it worked: `docker compose config` reflects the intended OpenSearch behavior.
- What screenshot/log/output to provide if it fails: Your decision and any local resource constraints.

### MANUAL-010: Provide real document/team UUIDs for final end-to-end smoke tests
- Related plan: `05-minio-storage-plan.md`, `06-onlyoffice-plan.md`, `09-verification-plan.md`
- Related error(s): Sections 6, 7, 11, 12
- Why the agent cannot do this: Real IDs require seeded/local data that the agent cannot safely invent for your database.
- When to do this: Before final authenticated document/calendar smoke tests.
- Exact step-by-step instructions for the user:
  1. Provide a real team UUID for calendar event list tests.
  2. Provide a real document UUID with a current uploaded version.
  3. Provide the actor/user expected to access them.
- How to verify it worked: Calendar and OnlyOffice endpoints return 200 with expected data.
- What screenshot/log/output to provide if it fails: API response body and IDs used, with secrets omitted.
