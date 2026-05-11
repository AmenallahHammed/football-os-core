# FOS Workspace Frontend

Angular frontend for the Football OS workspace module.

## Purpose

This app provides the UI for workspace features routed through the backend gateway:

- documents
- calendar/events
- player profile views
- notifications
- search

## Prerequisites

- Node.js 20+
- NPM
- backend services running (`fos-gateway`, `fos-workspace-service`, `fos-governance-service`)

## Install

```bash
npm ci
```

## Run Locally

```bash
npm start
```

Default URL: `http://localhost:4200`

## Build

```bash
npm run build
```

Build output: `dist/fos-workspace-frontend`

## Test

```bash
npm test
```

## Scripts

- `npm start`: start dev server
- `npm run build`: production build
- `npm test`: run unit tests

## Notes

- API calls are expected to go through the gateway at `http://localhost:8080`.
- Ensure Keycloak/infra containers are running when testing authenticated flows.
- The local Angular environment uses:
  - gateway base URL: `http://localhost:8080`
  - Keycloak URL: `http://localhost:8180`
  - Keycloak realm: `fos`
  - Keycloak public client ID: `fos-workspace-frontend`
- The Keycloak client should be public, use Authorization Code with PKCE, allow `http://localhost:4200/*` as a valid redirect URI, and allow `http://localhost:4200` as a web origin.
- Workspace API calls must stay on `/api/v1/...` through the gateway. Do not call `fos-workspace-service` directly on `8082` from the frontend.

## Auth Smoke Test

1. Start Keycloak and backend services.
2. Confirm gateway health is public: `http://localhost:8080/actuator/health`.
3. With gateway security enabled, confirm a protected gateway API returns `401` without a token.
4. Run `npm start` and open `http://localhost:4200`.
5. Click Sign In, complete Keycloak login, and confirm calendar requests go to `http://localhost:8080/api/v1/events` with a bearer token.
6. Use the workspace rail sign-out button and confirm the session returns to `/login`.
