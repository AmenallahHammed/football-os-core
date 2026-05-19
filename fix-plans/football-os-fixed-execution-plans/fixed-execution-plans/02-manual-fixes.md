# Football OS Error Repair — Manual Fixes

## Purpose
This file contains only actions the coding agent must not guess or perform automatically.

Manual fixes are required when the task depends on:

- user-owned secrets
- Docker Desktop / local machine state
- browser session state
- real local database data
- real MinIO objects
- real club/team/actor UUIDs
- product decisions such as deletion or optional services

The agent must reference these manual items instead of inventing values.

---

# MANUAL-001 — Start Or Unlock Docker Desktop

## Related Categories
- Docker Compose verification
- service health checks
- end-to-end smoke tests

## Why Manual
Docker Desktop and the Docker API are controlled by the user's local machine/session.

## User Steps
1. Open Docker Desktop.
2. Wait until the engine says it is running.
3. Approve any Windows permission prompts.
4. From the repository root, run:

```bash
docker compose ps
```

## Success Signal
The command returns a table of services instead of a Docker API connection error.

## If It Fails
Send the full `docker compose ps` output and a screenshot/status of Docker Desktop.

---

# MANUAL-002 — Verify Keycloak Realm Import Or Import Through Admin UI

## Related Categories
- Keycloak realm setup
- frontend login
- role claim mapping

## Why Manual
Admin UI access requires local credentials and browser interaction. Also, Keycloak may ignore imports if its database was already initialized.

## User Steps
1. Open:

```text
http://localhost:8180
```

2. Log in with your local Keycloak admin credentials from your private `.env`.
3. Confirm realm exists:

```text
fos
```

4. Confirm client exists:

```text
fos-workspace-frontend
```

5. Confirm redirect URI includes:

```text
http://localhost:4200/*
```

6. Confirm web origin includes:

```text
http://localhost:4200
```

7. Confirm roles exist, at minimum:

```text
ROLE_HEAD_COACH
ROLE_CLUB_ADMIN
ROLE_MEDICAL_STAFF
ROLE_ANALYST
```

8. Confirm role claims are included in tokens through one or more of:

```text
roles
realm_access.roles
resource_access.fos-workspace-frontend.roles
```

## Success Signal
This URL returns JSON:

```text
http://localhost:8180/realms/fos/.well-known/openid-configuration
```

Frontend login redirects correctly.

## If It Fails
Send screenshots of the realm/client settings and the browser console/network error.

---

# MANUAL-003 — Provide Real Club, Team, Actor UUIDs And Role Names

## Related Categories
- fake UUID cleanup
- no-auth fallback cleanup
- calendar API behavior
- policy authorization

## Why Manual
The correct IDs are local business data, not inferable from source code.

## User Must Provide
```text
club UUID:
team UUID:
actor/user UUID:
role names:
```

Recommended role names:

```text
ROLE_HEAD_COACH
ROLE_CLUB_ADMIN
ROLE_MEDICAL_STAFF
ROLE_ANALYST
```

## Success Signal
Authenticated API calls use real IDs and policy checks allow expected actions.

## If It Fails
Provide:

- decoded token claims with secrets removed
- API response body
- backend log line showing actor/role/club

---

# MANUAL-004 — Set Local `.env` Secrets

## Related Categories
- env/profile fixes
- OnlyOffice JWT
- Docker Compose startup

## Why Manual
Real local secrets must be owned by the user and must not be invented or committed by the agent.

## User Steps
1. Open local ignored `.env`.
2. Set or replace:

```env
KEYCLOAK_ADMIN=
KEYCLOAK_ADMIN_PASSWORD=
KEYCLOAK_WEBHOOK_SECRET=
OPENSEARCH_INITIAL_ADMIN_PASSWORD=
ONLYOFFICE_JWT_SECRET=
```

3. Ensure:

```text
ONLYOFFICE_JWT_SECRET
```

is at least 32 characters.

4. Keep `.env` ignored and uncommitted.

## Success Signal
```bash
docker compose config
```

resolves non-empty values, and the OnlyOffice secret is not the old short placeholder.

## If It Fails
Send redacted variable names and compose output with secret values masked.

---

# MANUAL-005 — Set LAN IP For Phone/External Device Testing

## Related Categories
- OnlyOffice public URL
- MinIO public URL
- browser/device access

## Why Manual
The correct LAN IP depends on the user's local network adapter and target device.

## When Needed
Only needed when testing from another device or when `localhost` is not reachable by the browser/device that opens OnlyOffice.

## User Steps
1. Find your machine LAN IPv4 address.
2. Set in local `.env`:

```env
HOST_LAN_IP=<your-ipv4>
ONLYOFFICE_PUBLIC_URL=http://<your-ipv4>:8084
```

3. Restart affected containers.
4. From the target device, open:

```text
http://<your-ipv4>:8084/web-apps/apps/api/documents/api.js
```

## Success Signal
The target device loads the OnlyOffice `api.js` file.

## If It Fails
Send browser error, machine IP output, and firewall/network profile status.

---

# MANUAL-006 — Refresh Browser Session After Auth Changes

## Related Categories
- Keycloak mapper changes
- backend role extraction
- frontend auth behavior

## Why Manual
Old browser tokens may remain in local/session storage after Keycloak or JWT parsing changes.

## User Steps
1. Log out of Football OS.
2. Clear site data for:

```text
localhost:4200
localhost:8180
```

3. Log in again.
4. Retry the workflow.

## Success Signal
New access token contains expected role/club claims and API calls stop returning unexpected `401` or `403`.

## If It Fails
Send:

- browser network request
- decoded token claims with secrets removed
- gateway/workspace response

---

# MANUAL-007 — Decide Whether Old Frontend Folders Can Be Deleted

## Related Categories
- frontend cleanup
- mock/demo folder cleanup

## Why Manual
Old folders may contain design/reference work the user still wants.

## Current Rule
The agent must not delete these automatically:

```text
fos-workspace-frontend/src/app/features/calendar/
fos-workspace-frontend/src/app/shared/onlyoffice-editor/
fos-workspace-frontend/frontend-plan/
```

## User Decision Required
For each folder, choose one:

```text
keep
archive later
delete later
```

## Success Signal
The execution agent has explicit written confirmation before deleting anything.

---

# MANUAL-008 — Verify Real MinIO Objects For Document Editing

## Related Categories
- MinIO
- OnlyOffice document loading
- document config endpoint

## Why Manual
The agent cannot know which local bucket/object/document is valid in your database.

## User Steps
1. Open MinIO Console:

```text
http://localhost:9001
```

2. Confirm the configured bucket exists, for example:

```text
fos-workspace
fos-workspace-dev
fos-workspace-staging
```

3. Confirm a test document object exists.
4. Provide the matching backend document UUID.

## Success Signal
The document URL from `/api/v1/onlyoffice/config` can load the object and OnlyOffice can open it.

## If It Fails
Send MinIO bucket/object screenshot and backend document UUID.

---

# MANUAL-009 — Decide Whether OpenSearch Belongs In Minimal Local Stack

## Related Categories
- Docker Compose cleanup
- local resource usage

## Why Manual
This is a product/dev-experience decision, not a code correctness issue.

## Current Rule
The agent must leave OpenSearch unchanged unless you decide otherwise.

## User Decision Required
Choose one:

```text
keep OpenSearch in default full compose
move OpenSearch behind a compose profile
remove OpenSearch from minimal local documentation only
```

## Success Signal
The compose behavior matches your decision.

---

# MANUAL-010 — Provide Real Team And Document UUIDs For Final Smoke Tests

## Related Categories
- calendar events
- OnlyOffice editing
- authenticated end-to-end testing

## Why Manual
Real IDs require seeded/local data that the agent cannot safely invent.

## User Must Provide
```text
team UUID for calendar listing:
document UUID with uploaded current version:
actor/user expected to access them:
```

## Success Signal
Calendar and OnlyOffice endpoints return `200` with expected data.

## If It Fails
Send API response body and the IDs used, with secrets omitted.
