# Sprint 1.5 Plan
## Phase 1 - Angular Workspace Frontend

## Goal
Deliver `fos-workspace-frontend` as a standalone Angular application that uses the gateway exclusively and supports:
- Keycloak login
- document list/upload/view/delete
- calendar/event management
- player profile tabs
- notifications inbox and unread badge
- search UI
- OnlyOffice viewer launch
- production build and local dev workflow

## Hard Prerequisite Gate
Sprint 1.5 must not start until backend capabilities are materially present:
- [ ] Sprint 1.1 document APIs exist
- [ ] Sprint 1.2 event APIs exist
- [ ] Sprint 1.3 profile + OnlyOffice config APIs exist
- [ ] Sprint 1.4 notifications + search APIs exist
- [ ] gateway routes frontend-needed endpoints correctly
- [ ] Keycloak local flow works
- [ ] OnlyOffice local service is available if viewer/edit preview is expected

## Definition of Done
- [ ] Angular project scaffold exists
- [ ] app builds with `npm run build`
- [ ] app runs with `npm start`
- [ ] Keycloak login flow works
- [ ] auth interceptor adds JWT to gateway-bound requests
- [ ] all API access goes through centralized Angular services
- [ ] documents UI supports 3-step upload flow
- [ ] calendar UI supports event creation/edit/delete
- [ ] profile page renders role-gated tabs from API response
- [ ] notification inbox + unread badge work
- [ ] search UI works
- [ ] local proxy config avoids CORS issues in development

## Non-Negotiable Constraints
- [ ] frontend talks only to `fos-gateway`
- [ ] no direct calls to workspace or governance services
- [ ] auth is handled by `angular-oauth2-oidc`
- [ ] JWT attachment is centralized in an interceptor
- [ ] components do not call `HttpClient` directly except where explicitly justified
- [ ] API services own backend communication
- [ ] role-based rendering is implemented in guards/directives/services, not scattered ad-hoc
- [ ] MinIO direct upload bypasses auth interceptor intentionally
- [ ] frontend remains standalone Angular app, not a Maven module

## Current Repo Alignment Notes
- [ ] `fos-workspace-frontend` does not exist yet
- [ ] local proxy config is likely needed because the doc’s env uses absolute API URL first
- [ ] backend must already support the endpoints used by the frontend
- [ ] notification badge polling should be lightweight and failure-tolerant
- [ ] current repo has no frontend build pipeline yet

## Deliverables
- [ ] Angular project scaffold
- [ ] environment config
- [ ] Material theme + base styling
- [ ] auth service
- [ ] auth interceptor
- [ ] auth guard
- [ ] role guard
- [ ] app config
- [ ] shared models
- [ ] API services:
- [ ] document
- [ ] event
- [ ] notification
- [ ] search
- [ ] onlyoffice
- [ ] app routes
- [ ] navbar
- [ ] app shell
- [ ] document list/upload/viewer components
- [ ] calendar + event dialog components
- [ ] player profile component
- [ ] notification inbox component
- [ ] search component
- [ ] proxy config
- [ ] smoke-test checklist

## Execution Order

### 0. Prerequisite Validation
- [ ] confirm backend endpoints exist and are reachable through gateway
- [ ] confirm Keycloak login realm/client is configured
- [ ] confirm gateway JWT path is working
- [ ] confirm OnlyOffice local service is reachable if document viewer is expected
- [ ] stop if backend foundation is not actually there

### 1. Scaffold Angular Project
- [ ] create Angular standalone app
- [ ] install dependencies:
- [ ] `angular-oauth2-oidc`
- [ ] Angular Material/CDK
- [ ] FullCalendar packages
- [ ] configure environment files
- [ ] configure styles/theme
- [ ] add Google fonts and OnlyOffice script to `index.html`

### 1.1 Frontend Scaffold Rules
- [ ] use Angular 17 standalone mode
- [ ] keep project at monorepo root as `fos-workspace-frontend`
- [ ] avoid coupling to Maven
- [ ] keep environment config explicit for local/prod
- [ ] if proxy is planned, prefer relative API base locally

### 2. Implement Authentication Layer
- [ ] create `AuthService`
- [ ] create `auth.interceptor.ts`
- [ ] create `auth.guard.ts`
- [ ] create `role.guard.ts`
- [ ] create `app.config.ts`

### 2.1 Auth Rules
- [ ] initialize OIDC client once on app startup
- [ ] support login redirect
- [ ] support logout
- [ ] expose current auth state reactively
- [ ] expose roles and display name helpers
- [ ] interceptor attaches bearer token to gateway API requests only
- [ ] direct MinIO upload must bypass JWT attachment
- [ ] auth failure should redirect to login or unauthorized route cleanly

### 3. Create Shared Models + API Services
- [ ] add document models
- [ ] add event models
- [ ] add notification models
- [ ] add search models
- [ ] add document API service
- [ ] add event API service
- [ ] add notification API service
- [ ] add search API service
- [ ] add onlyoffice API service

### 3.1 Service Design Rules
- [ ] components do not embed raw endpoint URLs
- [ ] upload flow is encapsulated in document service:
- [ ] initiate
- [ ] direct upload
- [ ] confirm
- [ ] page response types are modeled consistently
- [ ] request/response shapes mirror backend DTOs accurately
- [ ] model drift from backend should be checked before feature work

### 4. App Shell + Routing
- [ ] create `app.routes.ts`
- [ ] create navbar
- [ ] create app shell component
- [ ] wire protected routes
- [ ] add unauthorized and fallback paths

### 4.1 Navigation Rules
- [ ] navbar shows only when authenticated
- [ ] navbar includes documents, calendar, search, notifications
- [ ] unread badge polling is resilient to transient failures
- [ ] routes lazy-load feature components where practical
- [ ] no direct backend assumptions baked into route structure beyond gateway API contracts

### 5. Document UI
- [ ] create `DocumentListComponent`
- [ ] create `DocumentUploadComponent`
- [ ] create `DocumentViewerComponent`

### 5.1 Document UI Rules
- [ ] category filter loads documents
- [ ] upload dialog performs 3-step flow
- [ ] errors are surfaced clearly
- [ ] delete refreshes list
- [ ] viewer opens document preview/edit
- [ ] OnlyOffice is used for office-compatible files
- [ ] PDFs and non-office docs have a sane fallback behavior
- [ ] avoid interceptor on direct MinIO upload step

### 6. Calendar UI
- [ ] create `CalendarComponent`
- [ ] create `EventDialogComponent`

### 6.1 Calendar Rules
- [ ] FullCalendar renders backend events
- [ ] create dialog supports event creation
- [ ] edit dialog supports update/delete
- [ ] date select opens create flow
- [ ] event click opens edit flow
- [ ] hardcoded team fallback is temporary and documented if still used
- [ ] do not leave permanent demo-only assumptions hidden

### 7. Profile + Notifications + Search UI
- [ ] create `PlayerProfileComponent`
- [ ] create `NotificationInboxComponent`
- [ ] create `SearchComponent`

### 7.1 Profile UI Rules
- [ ] tabs are rendered from API sections
- [ ] null sections are hidden
- [ ] document lists inside tabs are reusable/readable
- [ ] no duplicate tab-permission logic if backend already enforces visibility

### 7.2 Notifications UI Rules
- [ ] inbox lists notifications
- [ ] unread styling is visible
- [ ] clicking unread item marks it read
- [ ] mark-all-read works
- [ ] navbar unread badge updates after changes

### 7.3 Search UI Rules
- [ ] query entry is debounced or intentionally submit-driven
- [ ] documents and events are shown clearly
- [ ] empty states are explicit
- [ ] unauthorized results are never fabricated client-side

### 8. Local Dev Proxy + Final Smoke Setup
- [ ] create `src/proxy.conf.json`
- [ ] wire proxy into Angular serve config
- [ ] switch local `apiBaseUrl` to relative path when proxy is used
- [ ] verify login and API requests from dev server work without CORS errors

### 9. Verification
- [ ] run `npm run build`
- [ ] run `npm start`
- [ ] run full local smoke:
- [ ] login redirect
- [ ] post-login landing
- [ ] document listing
- [ ] upload flow
- [ ] viewer launch
- [ ] calendar display
- [ ] event create
- [ ] notification badge
- [ ] search results

## Strict Acceptance Checklist
- [ ] frontend compiles with no TS errors
- [ ] app serves on `4200`
- [ ] login works against Keycloak
- [ ] JWT is attached to gateway API requests
- [ ] documents page loads
- [ ] upload flow completes
- [ ] calendar loads events
- [ ] event dialog can save
- [ ] player profile page renders role-gated content
- [ ] notification bell shows unread count
- [ ] inbox page loads notifications
- [ ] search page returns results
- [ ] OnlyOffice viewer opens for supported docs
- [ ] no direct service-to-backend bypasses exist

## Risks
- [ ] backend endpoints may still not be fully implemented when frontend starts
- [ ] Keycloak client configuration may drift from frontend env
- [ ] proxy/base URL setup may conflict if not decided early
- [ ] direct MinIO upload can be broken by wrong CORS/presign assumptions
- [ ] OnlyOffice script/runtime can fail independently of Angular app
- [ ] hardcoded team/profile assumptions can leak into production-like flow
- [ ] no frontend tests in this phase means smoke discipline must be strong

## Risk Mitigations
- [ ] validate each API service against real gateway endpoint before feature UI coding
- [ ] decide local API strategy early: absolute URL vs proxy
- [ ] keep direct upload logic isolated in one service method
- [ ] add clear empty/error states to all screens
- [ ] do manual smoke after every major feature group
- [ ] keep frontend models aligned to backend DTO changes continuously

## Suggested Commit Boundaries
- [ ] commit 1: project scaffold + deps + theme
- [ ] commit 2: auth layer
- [ ] commit 3: shared models + API services
- [ ] commit 4: routes + navbar + shell
- [ ] commit 5: document feature
- [ ] commit 6: calendar feature
- [ ] commit 7: profile + notifications + search
- [ ] commit 8: proxy config + smoke verification

## Out of Scope
- [ ] websocket/push notifications
- [ ] drag-and-drop uploads
- [ ] PWA/offline mode
- [ ] Angular unit/component test suite
- [ ] full responsive/mobile redesign
- [ ] dark mode
- [ ] i18n/localization

## Exit Criteria
Sprint 1.5 is complete only when:
- [ ] frontend builds cleanly
- [ ] frontend runs locally
- [ ] auth and gateway integration work
- [ ] all major backend-backed pages are usable
- [ ] local smoke checklist passes end-to-end
- [ ] no architecture violations were introduced
