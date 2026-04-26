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
