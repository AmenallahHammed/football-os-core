# Phase 1 Sprint 1.5 — fos-workspace-frontend: Angular Application

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Angular frontend for the Workspace module is built and integrated with the backend. It includes: authentication via Keycloak (OIDC), a document manager (upload, preview via OnlyOffice, versioning, delete), a calendar view (create/edit/delete events, attendees, required documents), a player profile page (role-gated tabs), a notification inbox (with unread badge), and a search bar. The application is served on port 4200 in development. In production it is built with `ng build` and served as static files.

**Architecture:**
The Angular app talks to the backend exclusively through the `fos-gateway` (port 8080). It never calls `fos-workspace-service` (port 8082) or `fos-governance-service` (port 8081) directly. Authentication is handled by `angular-oauth2-oidc` — the library manages the Keycloak login redirect, token storage, and token refresh. The app sends the JWT as `Authorization: Bearer {token}` on every HTTP request via an Angular `HttpInterceptor`. Role-based UI rendering (hiding tabs, disabling buttons) is controlled by a `RoleGuard` and a `PermissionDirective`. All API calls are centralized in injectable Angular services (one per backend resource group). The Angular project lives at `fos-workspace-frontend/` at the monorepo root.

**Why not use a BFF (Backend-for-Frontend)?** In Phase 1, the gateway already handles JWT validation and routing. Adding a BFF layer would be over-engineering. The frontend talks to the gateway directly. If we need SSR or aggregation in Phase 2, we can add a BFF then.

**Tech Stack:** Angular 17+, TypeScript, `angular-oauth2-oidc` (Keycloak integration), Angular Material (UI components), `@fullcalendar/angular` (calendar view), OnlyOffice Document Editor (JavaScript SDK), RxJS, Angular Router, HttpClient, Angular Guards/Interceptors

**Folder structure:**
```
fos-workspace-frontend/
├── package.json
├── angular.json
├── tsconfig.json
├── src/
│   ├── index.html
│   ├── main.ts
│   ├── styles.scss
│   └── app/
│       ├── app.config.ts
│       ├── app.routes.ts
│       ├── core/
│       │   ├── auth/
│       │   │   ├── auth.service.ts
│       │   │   └── auth.interceptor.ts
│       │   ├── guards/
│       │   │   ├── auth.guard.ts
│       │   │   └── role.guard.ts
│       │   └── directives/
│       │       └── has-role.directive.ts
│       ├── shared/
│       │   ├── components/
│       │   │   ├── navbar/
│       │   │   │   ├── navbar.component.ts
│       │   │   │   └── navbar.component.html
│       │   │   └── page-not-found/
│       │   │       └── page-not-found.component.ts
│       │   └── models/
│       │       ├── document.model.ts
│       │       ├── event.model.ts
│       │       ├── notification.model.ts
│       │       └── search.model.ts
│       ├── api/
│       │   ├── document.service.ts
│       │   ├── event.service.ts
│       │   ├── notification.service.ts
│       │   ├── search.service.ts
│       │   └── onlyoffice.service.ts
│       └── features/
│           ├── documents/
│           │   ├── document-list/
│           │   │   ├── document-list.component.ts
│           │   │   └── document-list.component.html
│           │   ├── document-upload/
│           │   │   ├── document-upload.component.ts
│           │   │   └── document-upload.component.html
│           │   └── document-viewer/
│           │       ├── document-viewer.component.ts
│           │       └── document-viewer.component.html
│           ├── calendar/
│           │   ├── calendar.component.ts
│           │   ├── calendar.component.html
│           │   └── event-dialog/
│           │       ├── event-dialog.component.ts
│           │       └── event-dialog.component.html
│           ├── profile/
│           │   ├── player-profile.component.ts
│           │   └── player-profile.component.html
│           ├── notifications/
│           │   ├── notification-inbox.component.ts
│           │   └── notification-inbox.component.html
│           └── search/
│               ├── search.component.ts
│               └── search.component.html
```

---

## Task 1: Project Scaffold

**Why:** We initialize the Angular project, install all dependencies, and configure the environment files. This must be done before any component code is written.

- [ ] **Step 1: Create the Angular project**

Run from `football-os-core/`:

```bash
npx @angular/cli@17 new fos-workspace-frontend \
  --routing=true \
  --style=scss \
  --standalone=true \
  --skip-git=true
```

When prompted: choose SCSS for styles, enable routing.

- [ ] **Step 2: Install dependencies**

```bash
cd fos-workspace-frontend

# Keycloak / OAuth2 OIDC
npm install angular-oauth2-oidc

# Angular Material (UI components: buttons, cards, dialogs, tables, etc.)
npm install @angular/material @angular/cdk

# FullCalendar (calendar view)
npm install @fullcalendar/core @fullcalendar/angular @fullcalendar/daygrid \
            @fullcalendar/timegrid @fullcalendar/interaction

# OnlyOffice Document Editor JavaScript SDK
# (loaded from CDN — no npm package needed; added via script tag)
```

- [ ] **Step 3: Configure environment files**

Create `src/environments/environment.ts`:

```typescript
// src/environments/environment.ts
export const environment = {
  production: false,

  // All API calls go through the gateway — NEVER directly to backend services
  apiBaseUrl: 'http://localhost:8080',

  // Keycloak configuration
  keycloak: {
    issuer: 'http://localhost:8180/realms/fos',
    clientId: 'fos-workspace-frontend',
    // The gateway URL the frontend redirects to after Keycloak login
    redirectUri: 'http://localhost:4200',
    scope: 'openid profile email',
    // Whether to show the Keycloak login page in a popup or redirect
    responseType: 'code',
    requireHttps: false,  // false for local development only
  },

  // OnlyOffice Document Server URL
  onlyOfficeUrl: 'http://localhost:8090',
};
```

Create `src/environments/environment.prod.ts`:

```typescript
// src/environments/environment.prod.ts
export const environment = {
  production: true,
  apiBaseUrl: '${API_BASE_URL}',        // replaced at build time by CI/CD
  keycloak: {
    issuer: '${KEYCLOAK_ISSUER}',
    clientId: 'fos-workspace-frontend',
    redirectUri: '${APP_URL}',
    scope: 'openid profile email',
    responseType: 'code',
    requireHttps: true,
  },
  onlyOfficeUrl: '${ONLYOFFICE_URL}',
};
```

- [ ] **Step 4: Add Angular Material theme to styles.scss**

```scss
// src/styles.scss

// Import Angular Material prebuilt theme
@use '@angular/material' as mat;

@include mat.core();

// Define a custom FOS theme using indigo/amber palette
$fos-primary: mat.define-palette(mat.$indigo-palette);
$fos-accent: mat.define-palette(mat.$amber-palette, A200, A100, A400);
$fos-warn: mat.define-palette(mat.$red-palette);

$fos-theme: mat.define-light-theme((
  color: (
    primary: $fos-primary,
    accent: $fos-accent,
    warn: $fos-warn,
  ),
  typography: mat.define-typography-config(),
  density: 0
));

@include mat.all-component-themes($fos-theme);

// Global reset and base styles
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: 'Roboto', sans-serif;
  background-color: #f5f5f5;
  color: #212121;
}

// Utility classes
.full-width { width: 100%; }
.spacer { flex: 1 1 auto; }
.mt-16 { margin-top: 16px; }
.mb-16 { margin-bottom: 16px; }
.p-16 { padding: 16px; }
```

- [ ] **Step 5: Add Google Fonts and OnlyOffice script to index.html**

```html
<!-- src/index.html -->
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Football OS — Workspace</title>
  <base href="/">
  <meta name="viewport" content="width=device-width, initial-scale=1">

  <!-- Google Font: Roboto (required by Angular Material) -->
  <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500&display=swap"
        rel="stylesheet">

  <!-- Angular Material Icons -->
  <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">

  <!-- OnlyOffice Document Editor SDK -->
  <!-- This script must be loaded from the OnlyOffice server, NOT from npm -->
  <script
    src="http://localhost:8090/web-apps/apps/api/documents/api.js"
    type="text/javascript">
  </script>
</head>
<body class="mat-typography">
  <app-root></app-root>
</body>
</html>
```

- [ ] **Step 6: Commit**

```bash
cd fos-workspace-frontend
git add .
git commit -m "chore(frontend): scaffold Angular workspace frontend with Material, FullCalendar, OIDC"
```

---

## Task 2: Authentication — Keycloak Integration

**Why:** Every page in the workspace requires the user to be logged in. We use `angular-oauth2-oidc` to handle the Keycloak OIDC flow. An `HttpInterceptor` automatically attaches the JWT to every HTTP request so we never have to add `Authorization` headers manually in our services.

**Files:**
- Create: `src/app/core/auth/auth.service.ts`
- Create: `src/app/core/auth/auth.interceptor.ts`
- Create: `src/app/core/guards/auth.guard.ts`
- Create: `src/app/core/guards/role.guard.ts`
- Create: `src/app/app.config.ts`

- [ ] **Step 1: Create AuthService**

```typescript
// src/app/core/auth/auth.service.ts
import { Injectable, signal } from '@angular/core';
import { OAuthService, AuthConfig } from 'angular-oauth2-oidc';
import { environment } from '../../../environments/environment';

/**
 * AuthService wraps angular-oauth2-oidc.
 *
 * It handles:
 *   - Configuring the Keycloak OIDC endpoint
 *   - Logging in (redirect to Keycloak)
 *   - Logging out (clear tokens + Keycloak logout)
 *   - Providing the current user's ID and roles
 *   - Providing the access token for the HTTP interceptor
 *
 * Using signals (Angular 17+) for reactive state:
 * Components can use isLoggedIn() in templates directly.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  /** Reactive signal — true when the user has a valid access token */
  readonly isLoggedIn = signal(false);

  private readonly authConfig: AuthConfig = {
    issuer: environment.keycloak.issuer,
    clientId: environment.keycloak.clientId,
    redirectUri: environment.keycloak.redirectUri,
    responseType: environment.keycloak.responseType,
    scope: environment.keycloak.scope,
    requireHttps: environment.keycloak.requireHttps,
    showDebugInformation: !environment.production,
    // Silent token refresh: the library will automatically get a new access token
    // before it expires using a hidden iframe
    useSilentRefresh: true,
    silentRefreshTimeout: 5000,
  };

  constructor(private oauthService: OAuthService) {}

  /**
   * Must be called once on app startup (in app.config.ts).
   * Configures the OIDC client and processes the redirect callback if present.
   */
  async initialize(): Promise<void> {
    this.oauthService.configure(this.authConfig);

    // Load the JWKS from Keycloak (for token signature verification in the browser)
    await this.oauthService.loadDiscoveryDocumentAndTryLogin();

    // Set up automatic silent token refresh
    this.oauthService.setupAutomaticSilentRefresh();

    // Update the signal based on token state
    this.isLoggedIn.set(this.oauthService.hasValidAccessToken());

    // Listen for token events to keep the signal up-to-date
    this.oauthService.events.subscribe(() => {
      this.isLoggedIn.set(this.oauthService.hasValidAccessToken());
    });
  }

  /** Redirects the browser to Keycloak login page */
  login(): void {
    this.oauthService.initCodeFlow();
  }

  /** Clears tokens and redirects to Keycloak logout */
  logout(): void {
    this.oauthService.logOut();
  }

  /** Returns the raw JWT access token string */
  getAccessToken(): string {
    return this.oauthService.getAccessToken();
  }

  /**
   * Returns the current actor's ID (Keycloak subject = actor UUID).
   * This matches the actorId stored in fos-governance-service.
   */
  getActorId(): string | null {
    const claims = this.oauthService.getIdentityClaims() as Record<string, unknown>;
    return claims ? (claims['sub'] as string) : null;
  }

  /**
   * Returns the actor's roles from the JWT.
   * The roles claim is populated by FosJwtConverter in the gateway.
   */
  getRoles(): string[] {
    const claims = this.oauthService.getIdentityClaims() as Record<string, unknown>;
    if (!claims) return [];
    const roles = claims['roles'];
    return Array.isArray(roles) ? roles as string[] : [];
  }

  /** Returns true if the actor has the given role */
  hasRole(role: string): boolean {
    return this.getRoles().includes(role);
  }

  /** Returns the actor's preferred display name */
  getDisplayName(): string {
    const claims = this.oauthService.getIdentityClaims() as Record<string, unknown>;
    return (claims?.['preferred_username'] as string) ?? 'Unknown User';
  }
}
```

- [ ] **Step 2: Create AuthInterceptor**

```typescript
// src/app/core/auth/auth.interceptor.ts
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * HTTP interceptor that attaches the JWT access token to every outgoing request.
 *
 * This is a functional interceptor (Angular 17+ style).
 * It runs before EVERY HttpClient request and adds the Authorization header.
 *
 * Before: GET /api/v1/documents
 * After:  GET /api/v1/documents + Authorization: Bearer eyJhbGci...
 *
 * Without this, every API call would get a 401 from the gateway.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  // Only add the header if we have a valid token
  if (token) {
    const authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(authReq);
  }

  // No token — let the request through (it will get a 401 from the gateway)
  return next(req);
};
```

- [ ] **Step 3: Create AuthGuard**

```typescript
// src/app/core/guards/auth.guard.ts
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/**
 * Route guard that blocks unauthenticated users.
 *
 * Applied to every route that requires login.
 * If the user is not logged in, they are sent to the /login page.
 *
 * Usage in routes:
 *   { path: 'documents', canActivate: [authGuard], component: DocumentListComponent }
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  // Not logged in — redirect to login page
  return router.createUrlTree(['/login']);
};
```

- [ ] **Step 4: Create RoleGuard**

```typescript
// src/app/core/guards/role.guard.ts
import { inject } from '@angular/core';
import { CanActivateFn, Router, ActivatedRouteSnapshot } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/**
 * Route guard that blocks users who do not have the required role.
 *
 * Usage in routes:
 *   {
 *     path: 'admin',
 *     canActivate: [roleGuard],
 *     data: { requiredRole: 'ROLE_CLUB_ADMIN' },
 *     component: AdminComponent
 *   }
 *
 * The required role is passed via route.data.requiredRole.
 */
export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const requiredRole = route.data['requiredRole'] as string | undefined;

  // If no role is required, just check authentication
  if (!requiredRole) {
    return authService.isLoggedIn() || router.createUrlTree(['/login']);
  }

  if (authService.hasRole(requiredRole)) {
    return true;
  }

  // User is logged in but does not have the required role
  return router.createUrlTree(['/unauthorized']);
};
```

- [ ] **Step 5: Create HasRole structural directive**

```typescript
// src/app/core/directives/has-role.directive.ts
import { Directive, Input, TemplateRef, ViewContainerRef, OnInit } from '@angular/core';
import { AuthService } from '../auth/auth.service';

/**
 * Structural directive for role-based UI rendering.
 *
 * Usage in templates:
 *   <button *hasRole="'ROLE_CLUB_ADMIN'">Admin Only Button</button>
 *   <div *hasRole="'ROLE_HEAD_COACH'">Head Coach Content</div>
 *
 * If the current user does NOT have the specified role, the element is
 * removed from the DOM entirely (not just hidden — truly removed).
 * This prevents the element from being visible by inspecting HTML.
 */
@Directive({
  selector: '[hasRole]',
  standalone: true
})
export class HasRoleDirective implements OnInit {

  @Input({ required: true }) hasRole!: string;

  constructor(
    private templateRef: TemplateRef<unknown>,
    private viewContainer: ViewContainerRef,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    if (this.authService.hasRole(this.hasRole)) {
      // User has the role — render the element
      this.viewContainer.createEmbeddedView(this.templateRef);
    } else {
      // User does NOT have the role — remove from DOM
      this.viewContainer.clear();
    }
  }
}
```

- [ ] **Step 6: Create app.config.ts**

```typescript
// src/app/app.config.ts
import { ApplicationConfig, APP_INITIALIZER } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideOAuthClient } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';

/**
 * APP_INITIALIZER runs before Angular renders the first component.
 * We use it to initialize Keycloak authentication before anything loads.
 * Without this, routes would render before we know if the user is logged in.
 */
function initializeAuth(authService: AuthService): () => Promise<void> {
  return () => authService.initialize();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),

    // HTTP client with our auth interceptor
    provideHttpClient(withInterceptors([authInterceptor])),

    // Angular Material animations
    provideAnimations(),

    // angular-oauth2-oidc provider
    provideOAuthClient(),

    // Initialize Keycloak before the app renders
    {
      provide: APP_INITIALIZER,
      useFactory: initializeAuth,
      deps: [AuthService],
      multi: true
    }
  ]
};
```

- [ ] **Step 7: Commit**

```bash
git add src/app/core/ src/app/app.config.ts src/environments/
git commit -m "feat(frontend): add AuthService, AuthInterceptor, AuthGuard, RoleGuard, HasRoleDirective, app.config"
```

---

## Task 3: Shared Models and API Services

**Why:** We define TypeScript interfaces that mirror our backend DTOs. All HTTP calls are centralized in injectable services — components never use `HttpClient` directly.

**Files:**
- Create: `src/app/shared/models/document.model.ts`
- Create: `src/app/shared/models/event.model.ts`
- Create: `src/app/shared/models/notification.model.ts`
- Create: `src/app/shared/models/search.model.ts`
- Create: `src/app/api/document.service.ts`
- Create: `src/app/api/event.service.ts`
- Create: `src/app/api/notification.service.ts`
- Create: `src/app/api/search.service.ts`
- Create: `src/app/api/onlyoffice.service.ts`

- [ ] **Step 1: Create models**

```typescript
// src/app/shared/models/document.model.ts
export type DocumentCategory = 'GENERAL' | 'MEDICAL' | 'ADMIN' | 'REPORT' | 'CONTRACT';
export type DocumentVisibility = 'CLUB_WIDE' | 'TEAM_ONLY' | 'PRIVATE';
export type ResourceState = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

export interface DocumentVersion {
  versionId: string;
  originalFilename: string;
  contentType: string;
  fileSizeBytes: number;
  versionNumber: number;
  uploadedByActorId: string;
  uploadedAt: string;
  versionNote?: string;
}

export interface WorkspaceDocument {
  documentId: string;
  name: string;
  description?: string;
  category: DocumentCategory;
  visibility: DocumentVisibility;
  state: ResourceState;
  ownerRefId: string;
  linkedPlayerRefId?: string;
  linkedTeamRefId?: string;
  tags: string[];
  versionCount: number;
  currentVersion?: DocumentVersion;
  createdAt: string;
  updatedAt: string;
  downloadUrl?: string;
}

export interface InitiateUploadRequest {
  name: string;
  description?: string;
  category: DocumentCategory;
  visibility: DocumentVisibility;
  originalFilename: string;
  contentType: string;
  fileSizeBytes: number;
  linkedPlayerRefId?: string;
  linkedTeamRefId?: string;
  tags?: string[];
  versionNote?: string;
}

export interface UploadInitiationResult {
  documentId: string;
  uploadUrl: string;
  objectKey: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
```

```typescript
// src/app/shared/models/event.model.ts
export type EventType = 'TRAINING' | 'MATCH' | 'MEETING' | 'MEDICAL_CHECK' | 'ADMINISTRATIVE' | 'OTHER';

export interface AttendeeRef {
  canonicalRef: { type: string; id: string };
  mandatory: boolean;
  confirmed: boolean;
}

export interface RequiredDocument {
  requirementId: string;
  description: string;
  documentCategory: string;
  assignedToActorId: string;
  submittedDocumentId?: string;
  submitted: boolean;
}

export interface TaskAssignment {
  taskId: string;
  title: string;
  description?: string;
  assignedToActorId: string;
  dueAt: string;
  completed: boolean;
}

export interface WorkspaceEvent {
  eventId: string;
  title: string;
  description?: string;
  type: EventType;
  startAt: string;
  endAt: string;
  location?: string;
  createdByActorId: string;
  teamRefId?: string;
  state: string;
  attendees: AttendeeRef[];
  requiredDocuments: RequiredDocument[];
  tasks: TaskAssignment[];
  reminderSent: boolean;
  createdAt: string;
}

export interface CreateEventRequest {
  title: string;
  description?: string;
  type: EventType;
  startAt: string;
  endAt: string;
  location?: string;
  teamRefId?: string;
  attendees?: { actorId: string; mandatory: boolean; canonicalType: string }[];
  requiredDocuments?: { description: string; documentCategory: string; assignedToActorId: string }[];
  tasks?: { title: string; description?: string; assignedToActorId: string; dueAt: string }[];
}
```

```typescript
// src/app/shared/models/notification.model.ts
export type NotificationType =
  'DOCUMENT_MISSING' | 'DOCUMENT_UPLOADED' | 'EVENT_REMINDER' | 'TASK_ASSIGNED' | 'GENERAL';

export interface WorkspaceNotification {
  notificationId: string;
  type: NotificationType;
  title: string;
  body: string;
  read: boolean;
  relatedDocumentId?: string;
  relatedEventId?: string;
  createdAt: string;
}
```

```typescript
// src/app/shared/models/search.model.ts
import { WorkspaceDocument } from './document.model';
import { WorkspaceEvent } from './event.model';

export interface SearchResponse {
  query: string;
  documents: WorkspaceDocument[];
  events: WorkspaceEvent[];
  totalDocuments: number;
  totalEvents: number;
}
```

- [ ] **Step 2: Create DocumentService**

```typescript
// src/app/api/document.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  WorkspaceDocument, InitiateUploadRequest, UploadInitiationResult, PageResponse,
  DocumentCategory
} from '../shared/models/document.model';

/**
 * Handles all document-related API calls.
 * The three-step upload flow is encapsulated here.
 */
@Injectable({ providedIn: 'root' })
export class DocumentService {

  private readonly base = `${environment.apiBaseUrl}/api/v1/documents`;

  constructor(private http: HttpClient) {}

  /** Step 1: Request a pre-signed upload URL from the backend */
  initiateUpload(request: InitiateUploadRequest): Observable<UploadInitiationResult> {
    return this.http.post<UploadInitiationResult>(`${this.base}/upload/initiate`, request);
  }

  /**
   * Step 2: Upload the file bytes directly to MinIO using the pre-signed URL.
   * Note: This is a direct PUT to MinIO — NOT to our backend.
   * We do NOT use the HttpClient interceptor here because MinIO does not
   * accept our JWT. The pre-signed URL is already the authentication.
   */
  uploadToMinio(uploadUrl: string, file: File): Observable<void> {
    // Using fetch() for the direct MinIO upload to avoid our auth interceptor
    return new Observable(observer => {
      fetch(uploadUrl, {
        method: 'PUT',
        body: file,
        headers: {
          'Content-Type': file.type,
        }
      }).then(response => {
        if (response.ok) {
          observer.next();
          observer.complete();
        } else {
          observer.error(new Error(`MinIO upload failed: ${response.status}`));
        }
      }).catch(err => observer.error(err));
    });
  }

  /** Step 3: Tell the backend the upload is complete */
  confirmUpload(documentId: string, objectKey: string, bucket: string,
                originalRequest: InitiateUploadRequest): Observable<WorkspaceDocument> {
    return this.http.post<WorkspaceDocument>(`${this.base}/upload/confirm`, {
      documentId,
      storageObjectKey: objectKey,
      storageBucket: bucket,
      ...originalRequest
    });
  }

  getDocument(documentId: string): Observable<WorkspaceDocument> {
    return this.http.get<WorkspaceDocument>(`${this.base}/${documentId}`);
  }

  listDocuments(category: DocumentCategory, page = 0, size = 20): Observable<PageResponse<WorkspaceDocument>> {
    return this.http.get<PageResponse<WorkspaceDocument>>(
      `${this.base}?category=${category}&page=${page}&size=${size}`
    );
  }

  deleteDocument(documentId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${documentId}`);
  }
}
```

- [ ] **Step 3: Create EventService**

```typescript
// src/app/api/event.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { WorkspaceEvent, CreateEventRequest, PageResponse } from '../shared/models/event.model';

@Injectable({ providedIn: 'root' })
export class EventService {

  private readonly base = `${environment.apiBaseUrl}/api/v1/events`;

  constructor(private http: HttpClient) {}

  createEvent(request: CreateEventRequest): Observable<WorkspaceEvent> {
    return this.http.post<WorkspaceEvent>(this.base, request);
  }

  getEvent(eventId: string): Observable<WorkspaceEvent> {
    return this.http.get<WorkspaceEvent>(`${this.base}/${eventId}`);
  }

  listEventsByTeam(teamRefId: string, page = 0, size = 50): Observable<PageResponse<WorkspaceEvent>> {
    return this.http.get<PageResponse<WorkspaceEvent>>(
      `${this.base}?teamRefId=${teamRefId}&page=${page}&size=${size}`
    );
  }

  updateEvent(eventId: string, changes: Partial<CreateEventRequest>): Observable<WorkspaceEvent> {
    return this.http.put<WorkspaceEvent>(`${this.base}/${eventId}`, changes);
  }

  deleteEvent(eventId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${eventId}`);
  }
}
```

- [ ] **Step 4: Create NotificationService**

```typescript
// src/app/api/notification.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { WorkspaceNotification, PageResponse } from '../shared/models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationApiService {

  private readonly base = `${environment.apiBaseUrl}/api/v1/notifications`;

  constructor(private http: HttpClient) {}

  getNotifications(unreadOnly = false, page = 0, size = 20): Observable<PageResponse<WorkspaceNotification>> {
    return this.http.get<PageResponse<WorkspaceNotification>>(
      `${this.base}?unreadOnly=${unreadOnly}&page=${page}&size=${size}`
    );
  }

  getUnreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.base}/unread-count`);
  }

  markRead(notificationId: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/${notificationId}/read`, null);
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.base}/mark-all-read`, null);
  }
}
```

- [ ] **Step 5: Create SearchService and OnlyOfficeService**

```typescript
// src/app/api/search.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { SearchResponse } from '../shared/models/search.model';

@Injectable({ providedIn: 'root' })
export class SearchService {
  private readonly base = `${environment.apiBaseUrl}/api/v1/search`;
  constructor(private http: HttpClient) {}

  search(query: string): Observable<SearchResponse> {
    return this.http.get<SearchResponse>(`${this.base}?q=${encodeURIComponent(query)}`);
  }
}
```

```typescript
// src/app/api/onlyoffice.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface OnlyOfficeConfigResponse {
  documentServerUrl: string;
  config: {
    document: { fileType: string; key: string; title: string; url: string };
    editorConfig: { callbackUrl: string; lang: string; user: { id: string; name: string }; mode: string };
    documentType: string;
    token: string;
  };
  token: string;
}

@Injectable({ providedIn: 'root' })
export class OnlyOfficeService {
  private readonly base = `${environment.apiBaseUrl}/api/v1/onlyoffice`;
  constructor(private http: HttpClient) {}

  getEditorConfig(documentId: string, mode: 'view' | 'edit'): Observable<OnlyOfficeConfigResponse> {
    return this.http.post<OnlyOfficeConfigResponse>(`${this.base}/config`, { documentId, mode });
  }
}
```

- [ ] **Step 6: Commit**

```bash
git add src/app/shared/ src/app/api/
git commit -m "feat(frontend): add TypeScript models and API services for all workspace resources"
```

---

## Task 4: App Routes and Navbar

- [ ] **Step 1: Create app.routes.ts**

```typescript
// src/app/app.routes.ts
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  // Public route — login page
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component')
                           .then(m => m.LoginComponent)
  },

  // All workspace routes require authentication
  {
    path: '',
    canActivate: [authGuard],
    children: [
      // Default redirect to documents
      { path: '', redirectTo: 'documents', pathMatch: 'full' },

      // Documents
      {
        path: 'documents',
        loadComponent: () => import('./features/documents/document-list/document-list.component')
                               .then(m => m.DocumentListComponent)
      },

      // Calendar
      {
        path: 'calendar',
        loadComponent: () => import('./features/calendar/calendar.component')
                               .then(m => m.CalendarComponent)
      },

      // Player Profile — accessible to authenticated users (role filtering is in the component)
      {
        path: 'profiles/players/:playerId',
        loadComponent: () => import('./features/profile/player-profile.component')
                               .then(m => m.PlayerProfileComponent)
      },

      // Notifications inbox
      {
        path: 'notifications',
        loadComponent: () => import('./features/notifications/notification-inbox.component')
                               .then(m => m.NotificationInboxComponent)
      },

      // Search
      {
        path: 'search',
        loadComponent: () => import('./features/search/search.component')
                               .then(m => m.SearchComponent)
      },

      // Unauthorized page
      {
        path: 'unauthorized',
        loadComponent: () => import('./shared/components/page-not-found/page-not-found.component')
                               .then(m => m.PageNotFoundComponent)
      }
    ]
  },

  // Catch-all
  { path: '**', redirectTo: '' }
];
```

- [ ] **Step 2: Create NavbarComponent**

```typescript
// src/app/shared/components/navbar/navbar.component.ts
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatBadgeModule } from '@angular/material/badge';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationApiService } from '../../../api/notification.service';
import { interval } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [
    RouterLink, RouterLinkActive,
    MatToolbarModule, MatButtonModule, MatIconModule,
    MatBadgeModule, MatMenuModule
  ],
  template: `
    <mat-toolbar color="primary">
      <!-- Brand -->
      <span class="brand">⚽ Football OS — Workspace</span>
      <span class="spacer"></span>

      <!-- Navigation links -->
      <a mat-button routerLink="/documents" routerLinkActiveOptions="{exact:false}">
        <mat-icon>folder</mat-icon> Documents
      </a>
      <a mat-button routerLink="/calendar">
        <mat-icon>calendar_today</mat-icon> Calendar
      </a>
      <a mat-button routerLink="/search">
        <mat-icon>search</mat-icon> Search
      </a>

      <!-- Notification bell with unread count badge -->
      <a mat-icon-button routerLink="/notifications"
         [matBadge]="unreadCount() > 0 ? unreadCount() : null"
         matBadgeColor="warn">
        <mat-icon>notifications</mat-icon>
      </a>

      <!-- User menu -->
      <button mat-icon-button [matMenuTriggerFor]="userMenu">
        <mat-icon>account_circle</mat-icon>
      </button>
      <mat-menu #userMenu="matMenu">
        <button mat-menu-item disabled>
          <mat-icon>person</mat-icon>
          <span>{{ displayName() }}</span>
        </button>
        <button mat-menu-item (click)="logout()">
          <mat-icon>logout</mat-icon>
          <span>Logout</span>
        </button>
      </mat-menu>
    </mat-toolbar>
  `,
  styles: [`
    .brand { font-size: 1.1rem; font-weight: 500; margin-right: 16px; }
    .spacer { flex: 1 1 auto; }
    a { text-decoration: none; color: inherit; }
  `]
})
export class NavbarComponent implements OnInit {

  readonly unreadCount = signal(0);
  readonly displayName = signal('');

  constructor(
    private authService: AuthService,
    private notificationService: NotificationApiService
  ) {
    // Poll for unread count every 60 seconds
    interval(60_000)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.refreshUnreadCount());
  }

  ngOnInit(): void {
    this.displayName.set(this.authService.getDisplayName());
    this.refreshUnreadCount();
  }

  logout(): void { this.authService.logout(); }

  private refreshUnreadCount(): void {
    this.notificationService.getUnreadCount().subscribe({
      next: res => this.unreadCount.set(res.count),
      error: () => {} // silently ignore errors on background poll
    });
  }
}
```

- [ ] **Step 3: Create AppComponent as the shell**

```typescript
// src/app/app.component.ts
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent],
  template: `
    <!-- Show navbar only when the user is logged in -->
    @if (authService.isLoggedIn()) {
      <app-navbar />
    }
    <!-- The active route renders here -->
    <main style="padding: 24px;">
      <router-outlet />
    </main>
  `
})
export class AppComponent {
  constructor(readonly authService: AuthService) {}
}
```

- [ ] **Step 4: Commit**

```bash
git add src/app/app.routes.ts src/app/app.component.ts src/app/shared/components/
git commit -m "feat(frontend): add app routes, NavbarComponent with notification badge, AppComponent shell"
```

---

## Task 5: Document List and Upload Components

- [ ] **Step 1: Create DocumentListComponent**

```typescript
// src/app/features/documents/document-list/document-list.component.ts
import { Component, OnInit, signal } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatDialog } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DocumentService } from '../../../api/document.service';
import { WorkspaceDocument, DocumentCategory } from '../../../shared/models/document.model';
import { DocumentUploadComponent } from '../document-upload/document-upload.component';

@Component({
  selector: 'app-document-list',
  standalone: true,
  imports: [
    MatTableModule, MatButtonModule, MatIconModule, MatSelectModule,
    MatFormFieldModule, MatChipsModule, MatProgressSpinnerModule,
    FormsModule, RouterLink
  ],
  template: `
    <div style="display:flex; align-items:center; justify-content:space-between; margin-bottom:16px;">
      <h2>Documents</h2>

      <!-- Category filter -->
      <mat-form-field style="width:200px;">
        <mat-label>Category</mat-label>
        <mat-select [(ngModel)]="selectedCategory" (ngModelChange)="loadDocuments()">
          <mat-option value="GENERAL">General</mat-option>
          <mat-option value="REPORT">Reports</mat-option>
          <mat-option value="MEDICAL">Medical</mat-option>
          <mat-option value="ADMIN">Admin</mat-option>
          <mat-option value="CONTRACT">Contracts</mat-option>
        </mat-select>
      </mat-form-field>

      <button mat-raised-button color="primary" (click)="openUploadDialog()">
        <mat-icon>upload</mat-icon> Upload Document
      </button>
    </div>

    <!-- Loading state -->
    @if (loading()) {
      <mat-spinner diameter="40" style="margin: 40px auto;"></mat-spinner>
    }

    <!-- Document table -->
    @if (!loading()) {
      <table mat-table [dataSource]="documents()" style="width:100%;">

        <!-- Name column -->
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let doc">
            <a [href]="doc.downloadUrl" target="_blank" style="text-decoration:none; color:inherit;">
              <mat-icon style="vertical-align:middle; margin-right:8px;">description</mat-icon>
              {{ doc.name }}
            </a>
          </td>
        </ng-container>

        <!-- Category column -->
        <ng-container matColumnDef="category">
          <th mat-header-cell *matHeaderCellDef>Category</th>
          <td mat-cell *matCellDef="let doc">
            <mat-chip>{{ doc.category }}</mat-chip>
          </td>
        </ng-container>

        <!-- Version column -->
        <ng-container matColumnDef="version">
          <th mat-header-cell *matHeaderCellDef>Version</th>
          <td mat-cell *matCellDef="let doc">v{{ doc.versionCount }}</td>
        </ng-container>

        <!-- Date column -->
        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Uploaded</th>
          <td mat-cell *matCellDef="let doc">{{ doc.createdAt | date:'short' }}</td>
        </ng-container>

        <!-- Actions column -->
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let doc">
            <button mat-icon-button (click)="openViewer(doc)" matTooltip="Preview / Edit">
              <mat-icon>visibility</mat-icon>
            </button>
            <button mat-icon-button color="warn" (click)="deleteDocument(doc)" matTooltip="Delete">
              <mat-icon>delete</mat-icon>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
      </table>

      @if (documents().length === 0) {
        <div style="text-align:center; padding:40px; color:#9e9e9e;">
          <mat-icon style="font-size:48px;">folder_open</mat-icon>
          <p>No documents found. Upload your first document.</p>
        </div>
      }
    }
  `
})
export class DocumentListComponent implements OnInit {

  readonly documents = signal<WorkspaceDocument[]>([]);
  readonly loading = signal(false);
  selectedCategory: DocumentCategory = 'GENERAL';
  readonly displayedColumns = ['name', 'category', 'version', 'date', 'actions'];

  constructor(
    private documentService: DocumentService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void { this.loadDocuments(); }

  loadDocuments(): void {
    this.loading.set(true);
    this.documentService.listDocuments(this.selectedCategory).subscribe({
      next: page => {
        this.documents.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.snackBar.open('Failed to load documents', 'Close', { duration: 3000 });
        this.loading.set(false);
      }
    });
  }

  openUploadDialog(): void {
    const ref = this.dialog.open(DocumentUploadComponent, { width: '500px' });
    ref.afterClosed().subscribe(result => {
      if (result === 'uploaded') this.loadDocuments();
    });
  }

  openViewer(doc: WorkspaceDocument): void {
    this.dialog.open(
      import('../document-viewer/document-viewer.component').then(m => m.DocumentViewerComponent),
      { width: '90vw', height: '90vh', data: { document: doc } }
    );
  }

  deleteDocument(doc: WorkspaceDocument): void {
    if (!confirm(`Delete "${doc.name}"? This cannot be undone.`)) return;
    this.documentService.deleteDocument(doc.documentId).subscribe({
      next: () => {
        this.snackBar.open('Document deleted', 'Close', { duration: 2000 });
        this.loadDocuments();
      },
      error: () => this.snackBar.open('Delete failed', 'Close', { duration: 3000 })
    });
  }
}
```

- [ ] **Step 2: Create DocumentUploadComponent**

```typescript
// src/app/features/documents/document-upload/document-upload.component.ts
import { Component, signal } from '@angular/core';
import { MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { FormsModule } from '@angular/forms';
import { DocumentService } from '../../../api/document.service';
import { DocumentCategory, DocumentVisibility, InitiateUploadRequest } from '../../../shared/models/document.model';

@Component({
  selector: 'app-document-upload',
  standalone: true,
  imports: [
    MatDialogModule, MatButtonModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatProgressBarModule, FormsModule
  ],
  template: `
    <h2 mat-dialog-title>Upload Document</h2>
    <mat-dialog-content>

      <!-- Step indicator -->
      <p style="color:#9e9e9e; margin-bottom:16px;">
        @switch (uploadStep()) {
          @case (1) { Step 1 of 3: Fill in document details }
          @case (2) { Step 2 of 3: Uploading to storage... }
          @case (3) { Step 3 of 3: Confirming upload... }
        }
      </p>

      @if (uploadStep() === 1) {
        <mat-form-field class="full-width">
          <mat-label>Document Name</mat-label>
          <input matInput [(ngModel)]="form.name" required>
        </mat-form-field>

        <mat-form-field class="full-width mt-16">
          <mat-label>Category</mat-label>
          <mat-select [(ngModel)]="form.category">
            <mat-option value="GENERAL">General</mat-option>
            <mat-option value="REPORT">Report</mat-option>
            <mat-option value="MEDICAL">Medical</mat-option>
            <mat-option value="ADMIN">Admin</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field class="full-width mt-16">
          <mat-label>Visibility</mat-label>
          <mat-select [(ngModel)]="form.visibility">
            <mat-option value="CLUB_WIDE">Club Wide</mat-option>
            <mat-option value="TEAM_ONLY">Team Only</mat-option>
            <mat-option value="PRIVATE">Private</mat-option>
          </mat-select>
        </mat-form-field>

        <div class="mt-16">
          <label for="fileInput" style="display:block; margin-bottom:8px; color:#616161;">
            Select File
          </label>
          <input id="fileInput" type="file" (change)="onFileSelected($event)"
                 accept=".pdf,.docx,.xlsx,.pptx,.jpg,.png">
        </div>

        @if (selectedFile()) {
          <p style="margin-top:8px; color:#4caf50;">
            ✓ {{ selectedFile()!.name }} ({{ (selectedFile()!.size / 1024).toFixed(1) }} KB)
          </p>
        }
      }

      @if (uploadStep() === 2 || uploadStep() === 3) {
        <mat-progress-bar mode="indeterminate"></mat-progress-bar>
        <p style="margin-top:16px; text-align:center;">
          {{ uploadStep() === 2 ? 'Uploading file...' : 'Saving metadata...' }}
        </p>
      }

      @if (errorMessage()) {
        <p style="color:#f44336; margin-top:8px;">{{ errorMessage() }}</p>
      }

    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="uploadStep() > 1">Cancel</button>
      <button mat-raised-button color="primary"
              [disabled]="!canUpload() || uploadStep() > 1"
              (click)="startUpload()">
        Upload
      </button>
    </mat-dialog-actions>
  `,
  styles: ['.full-width { width: 100%; } .mt-16 { margin-top: 16px; }']
})
export class DocumentUploadComponent {

  readonly uploadStep = signal(1);
  readonly selectedFile = signal<File | null>(null);
  readonly errorMessage = signal('');

  form: Partial<InitiateUploadRequest> = {
    category: 'GENERAL',
    visibility: 'CLUB_WIDE'
  };

  constructor(
    private dialogRef: MatDialogRef<DocumentUploadComponent>,
    private documentService: DocumentService
  ) {}

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.selectedFile.set(input.files[0]);
    }
  }

  canUpload(): boolean {
    return !!(this.form.name && this.selectedFile() && this.form.category && this.form.visibility);
  }

  startUpload(): void {
    const file = this.selectedFile()!;
    const request: InitiateUploadRequest = {
      name: this.form.name!,
      category: this.form.category as DocumentCategory,
      visibility: this.form.visibility as DocumentVisibility,
      originalFilename: file.name,
      contentType: file.type,
      fileSizeBytes: file.size
    };

    // Step 1 → Step 2: Get pre-signed URL
    this.uploadStep.set(2);
    this.documentService.initiateUpload(request).subscribe({
      next: initResult => {
        // Step 2: Upload to MinIO
        this.documentService.uploadToMinio(initResult.uploadUrl, file).subscribe({
          next: () => {
            // Step 3: Confirm upload
            this.uploadStep.set(3);
            this.documentService.confirmUpload(
              initResult.documentId,
              initResult.objectKey,
              'fos-workspace',
              request
            ).subscribe({
              next: () => this.dialogRef.close('uploaded'),
              error: err => {
                this.errorMessage.set('Failed to confirm upload. Please try again.');
                this.uploadStep.set(1);
              }
            });
          },
          error: () => {
            this.errorMessage.set('Failed to upload file to storage. Please try again.');
            this.uploadStep.set(1);
          }
        });
      },
      error: () => {
        this.errorMessage.set('Failed to initiate upload. Please check your permissions.');
        this.uploadStep.set(1);
      }
    });
  }
}
```

- [ ] **Step 3: Create DocumentViewerComponent (OnlyOffice)**

```typescript
// src/app/features/documents/document-viewer/document-viewer.component.ts
import { Component, OnInit, OnDestroy, Inject, AfterViewInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { OnlyOfficeService } from '../../../api/onlyoffice.service';
import { WorkspaceDocument } from '../../../shared/models/document.model';
import { AuthService } from '../../../core/auth/auth.service';

// Declare global DocsAPI from OnlyOffice SDK loaded in index.html
declare const DocsAPI: {
  DocEditor: new (elementId: string, config: unknown) => { destroyEditor: () => void };
};

@Component({
  selector: 'app-document-viewer',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <div style="display:flex; align-items:center; padding:8px 16px; background:#f5f5f5;">
      <mat-icon style="margin-right:8px;">description</mat-icon>
      <span style="font-weight:500; flex:1;">{{ data.document.name }}</span>
      <button mat-icon-button mat-dialog-close>
        <mat-icon>close</mat-icon>
      </button>
    </div>

    <!-- OnlyOffice mounts here — must have a fixed height for the editor to render -->
    <div id="onlyoffice-editor"
         style="width:100%; height:calc(90vh - 60px);">
    </div>
  `
})
export class DocumentViewerComponent implements OnInit, OnDestroy {

  private editor: { destroyEditor: () => void } | null = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { document: WorkspaceDocument },
    private dialogRef: MatDialogRef<DocumentViewerComponent>,
    private onlyOfficeService: OnlyOfficeService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    // Choose mode based on role — admin and head coach get edit mode
    const mode = this.authService.hasRole('ROLE_CLUB_ADMIN') ||
                 this.authService.hasRole('ROLE_HEAD_COACH')
                 ? 'edit' : 'view';

    this.onlyOfficeService.getEditorConfig(
      this.data.document.documentId, mode
    ).subscribe({
      next: response => this.launchEditor(response),
      error: err => console.error('Failed to load OnlyOffice config', err)
    });
  }

  ngOnDestroy(): void {
    // Clean up the OnlyOffice editor instance when the dialog closes
    this.editor?.destroyEditor();
  }

  private launchEditor(response: ReturnType<OnlyOfficeService['getEditorConfig']> extends import('rxjs').Observable<infer T> ? T : never): void {
    if (typeof DocsAPI === 'undefined') {
      console.error(
        'OnlyOffice DocsAPI is not loaded. ' +
        'Make sure OnlyOffice is running at http://localhost:8090 ' +
        'and the script tag in index.html is correct.'
      );
      return;
    }

    const config = {
      ...response.config,
      // Inject the current user's display name for collaborative awareness
      editorConfig: {
        ...response.config.editorConfig,
        user: {
          id: this.authService.getActorId(),
          name: this.authService.getDisplayName()
        }
      }
    };

    this.editor = new DocsAPI.DocEditor('onlyoffice-editor', config);
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/app/features/documents/
git commit -m "feat(frontend): add DocumentListComponent, DocumentUploadComponent (3-step flow), DocumentViewerComponent (OnlyOffice)"
```

---

## Task 6: Calendar Component

- [ ] **Step 1: Create CalendarComponent**

```typescript
// src/app/features/calendar/calendar.component.ts
import { Component, OnInit, signal } from '@angular/core';
import { FullCalendarModule } from '@fullcalendar/angular';
import { CalendarOptions, EventInput } from '@fullcalendar/core';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EventService } from '../../api/event.service';
import { EventDialogComponent } from './event-dialog/event-dialog.component';

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [FullCalendarModule, MatButtonModule, MatIconModule],
  template: `
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
      <h2>Calendar</h2>
      <button mat-raised-button color="primary" (click)="openCreateEventDialog()">
        <mat-icon>add</mat-icon> New Event
      </button>
    </div>

    <full-calendar [options]="calendarOptions"></full-calendar>
  `
})
export class CalendarComponent implements OnInit {

  readonly calendarOptions: CalendarOptions = {
    plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin],
    initialView: 'dayGridMonth',
    headerToolbar: {
      left: 'prev,next today',
      center: 'title',
      right: 'dayGridMonth,timeGridWeek,timeGridDay'
    },
    events: [],
    editable: false,
    selectable: true,
    // When user clicks an event, open the edit dialog
    eventClick: (info) => this.onEventClick(info),
    // When user selects a date range, open create dialog
    select: (info) => this.openCreateEventDialog(info.startStr, info.endStr),
    eventColor: '#3f51b5',
  };

  constructor(
    private eventService: EventService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void { this.loadEvents(); }

  loadEvents(): void {
    // In Phase 1 we load events for a hardcoded team.
    // In Phase 2 the team ID comes from the actor's profile.
    const teamId = localStorage.getItem('selectedTeamId') ?? '';
    if (!teamId) {
      this.snackBar.open('No team selected. Using demo data.', 'Close', { duration: 3000 });
      return;
    }

    this.eventService.listEventsByTeam(teamId).subscribe({
      next: page => {
        const fcEvents: EventInput[] = page.content.map(e => ({
          id: e.eventId,
          title: `[${e.type}] ${e.title}`,
          start: e.startAt,
          end: e.endAt,
          extendedProps: e
        }));
        // Update calendar events immutably
        this.calendarOptions.events = fcEvents;
      },
      error: () => this.snackBar.open('Failed to load events', 'Close', { duration: 3000 })
    });
  }

  openCreateEventDialog(start?: string, end?: string): void {
    const ref = this.dialog.open(EventDialogComponent, {
      width: '600px',
      data: { mode: 'create', startAt: start, endAt: end }
    });
    ref.afterClosed().subscribe(result => {
      if (result === 'saved') this.loadEvents();
    });
  }

  onEventClick(info: { event: { id: string; extendedProps: unknown } }): void {
    this.dialog.open(EventDialogComponent, {
      width: '600px',
      data: { mode: 'edit', event: info.event.extendedProps }
    });
  }
}
```

- [ ] **Step 2: Create EventDialogComponent**

```typescript
// src/app/features/calendar/event-dialog/event-dialog.component.ts
import { Component, Inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormsModule } from '@angular/forms';
import { EventService } from '../../../api/event.service';
import { CreateEventRequest, EventType, WorkspaceEvent } from '../../../shared/models/event.model';

@Component({
  selector: 'app-event-dialog',
  standalone: true,
  imports: [
    MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatDatepickerModule, MatNativeDateModule, FormsModule
  ],
  template: `
    <h2 mat-dialog-title>{{ data.mode === 'create' ? 'Create Event' : 'Edit Event' }}</h2>
    <mat-dialog-content>

      <mat-form-field class="full-width">
        <mat-label>Title</mat-label>
        <input matInput [(ngModel)]="form.title" required>
      </mat-form-field>

      <mat-form-field class="full-width mt-16">
        <mat-label>Type</mat-label>
        <mat-select [(ngModel)]="form.type">
          <mat-option value="TRAINING">Training</mat-option>
          <mat-option value="MATCH">Match</mat-option>
          <mat-option value="MEETING">Meeting</mat-option>
          <mat-option value="MEDICAL_CHECK">Medical Check</mat-option>
          <mat-option value="ADMINISTRATIVE">Administrative</mat-option>
          <mat-option value="OTHER">Other</mat-option>
        </mat-select>
      </mat-form-field>

      <mat-form-field class="full-width mt-16">
        <mat-label>Description</mat-label>
        <textarea matInput [(ngModel)]="form.description" rows="3"></textarea>
      </mat-form-field>

      <mat-form-field class="full-width mt-16">
        <mat-label>Location</mat-label>
        <input matInput [(ngModel)]="form.location">
      </mat-form-field>

      @if (saving()) {
        <p style="text-align:center; color:#9e9e9e;">Saving...</p>
      }

    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      @if (data.mode === 'edit') {
        <button mat-button color="warn" (click)="deleteEvent()" [disabled]="saving()">
          Delete
        </button>
      }
      <button mat-raised-button color="primary" (click)="save()" [disabled]="!form.title || saving()">
        Save
      </button>
    </mat-dialog-actions>
  `,
  styles: ['.full-width { width: 100%; } .mt-16 { margin-top: 16px; }']
})
export class EventDialogComponent {

  readonly saving = signal(false);

  form: Partial<CreateEventRequest> = {
    type: 'TRAINING',
    startAt: this.data.startAt ?? new Date().toISOString(),
    endAt: this.data.endAt ?? new Date(Date.now() + 3_600_000).toISOString()
  };

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: {
      mode: 'create' | 'edit';
      event?: WorkspaceEvent;
      startAt?: string;
      endAt?: string;
    },
    private dialogRef: MatDialogRef<EventDialogComponent>,
    private eventService: EventService,
    private snackBar: MatSnackBar
  ) {
    // Pre-populate form when editing
    if (data.mode === 'edit' && data.event) {
      this.form = {
        title: data.event.title,
        description: data.event.description,
        type: data.event.type,
        startAt: data.event.startAt,
        endAt: data.event.endAt,
        location: data.event.location
      };
    }
  }

  save(): void {
    this.saving.set(true);
    const request = this.form as CreateEventRequest;

    const operation$ = this.data.mode === 'create'
      ? this.eventService.createEvent(request)
      : this.eventService.updateEvent(this.data.event!.eventId, request);

    operation$.subscribe({
      next: () => {
        this.snackBar.open('Event saved', 'Close', { duration: 2000 });
        this.dialogRef.close('saved');
      },
      error: () => {
        this.snackBar.open('Failed to save event', 'Close', { duration: 3000 });
        this.saving.set(false);
      }
    });
  }

  deleteEvent(): void {
    if (!confirm(`Delete "${this.data.event?.title}"?`)) return;
    this.saving.set(true);
    this.eventService.deleteEvent(this.data.event!.eventId).subscribe({
      next: () => {
        this.snackBar.open('Event deleted', 'Close', { duration: 2000 });
        this.dialogRef.close('saved');
      },
      error: () => {
        this.snackBar.open('Failed to delete event', 'Close', { duration: 3000 });
        this.saving.set(false);
      }
    });
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/app/features/calendar/
git commit -m "feat(frontend): add CalendarComponent (FullCalendar), EventDialogComponent"
```

---

## Task 7: Player Profile, Notifications, and Search Components

- [ ] **Step 1: Create PlayerProfileComponent**

```typescript
// src/app/features/profile/player-profile.component.ts
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatTabsModule } from '@angular/material/tabs';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { environment } from '../../../environments/environment';
import { WorkspaceDocument } from '../../shared/models/document.model';

interface PlayerProfileResponse {
  playerId: string;
  playerName: string;
  position: string;
  nationality: string;
  dateOfBirth: string;
  documents: WorkspaceDocument[] | null;
  reports: WorkspaceDocument[] | null;
  medicalRecords: WorkspaceDocument[] | null;
  adminDocuments: WorkspaceDocument[] | null;
  documentCount: number;
  reportCount: number;
  medicalRecordCount: number;
  adminDocumentCount: number;
}

@Component({
  selector: 'app-player-profile',
  standalone: true,
  imports: [
    MatTabsModule, MatCardModule, MatTableModule,
    MatChipsModule, MatProgressSpinnerModule
  ],
  template: `
    @if (loading()) {
      <mat-spinner diameter="40" style="margin:40px auto;"></mat-spinner>
    }

    @if (!loading() && profile()) {
      <!-- Player header card -->
      <mat-card style="margin-bottom:24px;">
        <mat-card-header>
          <mat-card-title>{{ profile()!.playerName }}</mat-card-title>
          <mat-card-subtitle>
            {{ profile()!.position }} · {{ profile()!.nationality }} · {{ profile()!.dateOfBirth }}
          </mat-card-subtitle>
        </mat-card-header>
      </mat-card>

      <!-- Role-gated tabs — null sections are hidden -->
      <mat-tab-group>

        @if (profile()!.documents !== null) {
          <mat-tab [label]="'Documents (' + profile()!.documentCount + ')'">
            <ng-container *ngTemplateOutlet="docTable; context: { docs: profile()!.documents }">
            </ng-container>
          </mat-tab>
        }

        @if (profile()!.reports !== null) {
          <mat-tab [label]="'Reports (' + profile()!.reportCount + ')'">
            <ng-container *ngTemplateOutlet="docTable; context: { docs: profile()!.reports }">
            </ng-container>
          </mat-tab>
        }

        @if (profile()!.medicalRecords !== null) {
          <mat-tab [label]="'Medical (' + profile()!.medicalRecordCount + ')'">
            <ng-container *ngTemplateOutlet="docTable; context: { docs: profile()!.medicalRecords }">
            </ng-container>
          </mat-tab>
        }

        @if (profile()!.adminDocuments !== null) {
          <mat-tab [label]="'Admin (' + profile()!.adminDocumentCount + ')'">
            <ng-container *ngTemplateOutlet="docTable; context: { docs: profile()!.adminDocuments }">
            </ng-container>
          </mat-tab>
        }

      </mat-tab-group>
    }

    <!-- Document table template (reused across tabs) -->
    <ng-template #docTable let-docs="docs">
      <div style="padding:16px;">
        @if (!docs || docs.length === 0) {
          <p style="color:#9e9e9e;">No documents in this section.</p>
        }
        @if (docs && docs.length > 0) {
          <table mat-table [dataSource]="docs" style="width:100%;">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Name</th>
              <td mat-cell *matCellDef="let doc">
                <a [href]="doc.downloadUrl" target="_blank">{{ doc.name }}</a>
              </td>
            </ng-container>
            <ng-container matColumnDef="version">
              <th mat-header-cell *matHeaderCellDef>Version</th>
              <td mat-cell *matCellDef="let doc">v{{ doc.versionCount }}</td>
            </ng-container>
            <ng-container matColumnDef="date">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let doc">{{ doc.createdAt | date:'short' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="['name','version','date']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['name','version','date'];"></tr>
          </table>
        }
      </div>
    </ng-template>
  `
})
export class PlayerProfileComponent implements OnInit {

  readonly profile = signal<PlayerProfileResponse | null>(null);
  readonly loading = signal(true);

  constructor(private route: ActivatedRoute, private http: HttpClient) {}

  ngOnInit(): void {
    const playerId = this.route.snapshot.paramMap.get('playerId');
    this.http.get<PlayerProfileResponse>(
      `${environment.apiBaseUrl}/api/v1/profiles/players/${playerId}`
    ).subscribe({
      next: p => { this.profile.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
```

- [ ] **Step 2: Create NotificationInboxComponent**

```typescript
// src/app/features/notifications/notification-inbox.component.ts
import { Component, OnInit, signal } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { DatePipe } from '@angular/common';
import { NotificationApiService } from '../../api/notification.service';
import { WorkspaceNotification } from '../../shared/models/notification.model';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-notification-inbox',
  standalone: true,
  imports: [MatListModule, MatIconModule, MatButtonModule, MatChipsModule, DatePipe],
  template: `
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
      <h2>Notifications</h2>
      <button mat-button (click)="markAllRead()">Mark all as read</button>
    </div>

    @if (notifications().length === 0) {
      <div style="text-align:center; padding:40px; color:#9e9e9e;">
        <mat-icon style="font-size:48px;">notifications_none</mat-icon>
        <p>No notifications</p>
      </div>
    }

    <mat-list>
      @for (n of notifications(); track n.notificationId) {
        <mat-list-item
          [style.background]="n.read ? 'transparent' : '#e8eaf6'"
          style="cursor:pointer; border-radius:4px; margin-bottom:4px;"
          (click)="markRead(n)">
          <mat-icon matListItemIcon>
            {{ notificationIcon(n.type) }}
          </mat-icon>
          <span matListItemTitle>{{ n.title }}</span>
          <span matListItemLine>{{ n.body }}</span>
          <span matListItemMeta style="font-size:12px; color:#9e9e9e;">
            {{ n.createdAt | date:'short' }}
          </span>
        </mat-list-item>
      }
    </mat-list>
  `
})
export class NotificationInboxComponent implements OnInit {

  readonly notifications = signal<WorkspaceNotification[]>([]);

  constructor(
    private notificationService: NotificationApiService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void { this.loadNotifications(); }

  loadNotifications(): void {
    this.notificationService.getNotifications().subscribe({
      next: page => this.notifications.set(page.content),
      error: () => this.snackBar.open('Failed to load notifications', 'Close', { duration: 3000 })
    });
  }

  markRead(n: WorkspaceNotification): void {
    if (n.read) return;
    this.notificationService.markRead(n.notificationId).subscribe({
      next: () => {
        this.notifications.update(list =>
          list.map(item => item.notificationId === n.notificationId
            ? { ...item, read: true } : item)
        );
      }
    });
  }

  markAllRead(): void {
    this.notificationService.markAllRead().subscribe({
      next: () => {
        this.notifications.update(list => list.map(n => ({ ...n, read: true })));
        this.snackBar.open('All marked as read', 'Close', { duration: 2000 });
      }
    });
  }

  notificationIcon(type: string): string {
    const icons: Record<string, string> = {
      DOCUMENT_MISSING: 'warning',
      DOCUMENT_UPLOADED: 'upload_file',
      EVENT_REMINDER: 'event',
      TASK_ASSIGNED: 'task_alt',
      GENERAL: 'info'
    };
    return icons[type] ?? 'notifications';
  }
}
```

- [ ] **Step 3: Create SearchComponent**

```typescript
// src/app/features/search/search.component.ts
import { Component, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { FormsModule } from '@angular/forms';
import { SearchService } from '../../api/search.service';
import { SearchResponse } from '../../shared/models/search.model';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [
    MatFormFieldModule, MatInputModule, MatIconModule, MatButtonModule,
    MatTabsModule, MatListModule, MatProgressSpinnerModule, MatChipsModule, FormsModule
  ],
  template: `
    <h2>Search Workspace</h2>

    <mat-form-field style="width:100%; margin-top:16px;">
      <mat-label>Search documents, events...</mat-label>
      <input matInput [(ngModel)]="query"
             (keyup.enter)="search()"
             placeholder="Type and press Enter">
      <button mat-icon-button matSuffix (click)="search()" [disabled]="!query.trim()">
        <mat-icon>search</mat-icon>
      </button>
    </mat-form-field>

    @if (loading()) {
      <mat-spinner diameter="40" style="margin:24px auto;"></mat-spinner>
    }

    @if (!loading() && results()) {
      <mat-tab-group style="margin-top:16px;">

        <mat-tab [label]="'Documents (' + results()!.totalDocuments + ')'">
          <mat-list>
            @for (doc of results()!.documents; track doc.documentId) {
              <mat-list-item>
                <mat-icon matListItemIcon>description</mat-icon>
                <span matListItemTitle>
                  <a [href]="doc.downloadUrl" target="_blank">{{ doc.name }}</a>
                </span>
                <span matListItemLine>
                  <mat-chip>{{ doc.category }}</mat-chip>
                  &nbsp;v{{ doc.versionCount }}
                </span>
              </mat-list-item>
            }
            @if (results()!.totalDocuments === 0) {
              <p style="padding:16px; color:#9e9e9e;">No documents found.</p>
            }
          </mat-list>
        </mat-tab>

        <mat-tab [label]="'Events (' + results()!.totalEvents + ')'">
          <mat-list>
            @for (event of results()!.events; track event.eventId) {
              <mat-list-item>
                <mat-icon matListItemIcon>event</mat-icon>
                <span matListItemTitle>{{ event.title }}</span>
                <span matListItemLine>
                  <mat-chip>{{ event.type }}</mat-chip>
                  &nbsp;{{ event.startAt | date:'short' }}
                </span>
              </mat-list-item>
            }
            @if (results()!.totalEvents === 0) {
              <p style="padding:16px; color:#9e9e9e;">No events found.</p>
            }
          </mat-list>
        </mat-tab>

      </mat-tab-group>
    }
  `
})
export class SearchComponent {

  query = '';
  readonly results = signal<SearchResponse | null>(null);
  readonly loading = signal(false);

  constructor(private searchService: SearchService) {}

  search(): void {
    if (!this.query.trim()) return;
    this.loading.set(true);
    this.searchService.search(this.query).subscribe({
      next: res => { this.results.set(res); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
```

- [ ] **Step 4: Create a minimal LoginComponent**

```typescript
// src/app/features/auth/login.component.ts
import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatIconModule],
  template: `
    <div style="display:flex; justify-content:center; align-items:center; height:80vh;">
      <mat-card style="padding:40px; text-align:center; max-width:400px;">
        <mat-icon style="font-size:64px; color:#3f51b5;">sports_soccer</mat-icon>
        <h1 style="margin:16px 0 8px;">Football OS</h1>
        <p style="color:#616161; margin-bottom:24px;">Workspace — Professional Football Management</p>
        <button mat-raised-button color="primary" (click)="authService.login()" style="width:100%;">
          <mat-icon>login</mat-icon> Login with Keycloak
        </button>
      </mat-card>
    </div>
  `
})
export class LoginComponent {
  constructor(readonly authService: AuthService) {}
}
```

- [ ] **Step 5: Commit**

```bash
git add src/app/features/profile/ src/app/features/notifications/ \
        src/app/features/search/ src/app/features/auth/
git commit -m "feat(frontend): add PlayerProfileComponent, NotificationInboxComponent, SearchComponent, LoginComponent"
```

---

## Task 8: Build and Smoke Test

- [ ] **Step 1: Verify the project compiles**

```bash
cd fos-workspace-frontend
npm run build
```

Expected: BUILD SUCCESS with no TypeScript errors. Output goes to `dist/fos-workspace-frontend/`.

- [ ] **Step 2: Start the development server**

```bash
npm start
```

Expected: Angular serves at `http://localhost:4200`. The browser opens the login page.

- [ ] **Step 3: Full stack smoke test**

Start all services:

```bash
# Terminal 1 — Infrastructure
cd football-os-core && docker-compose up -d

# Terminal 2 — Governance service
java -jar fos-governance-service/target/fos-governance-service-*.jar

# Terminal 3 — Workspace service
java -jar fos-workspace-service/target/fos-workspace-service-*.jar

# Terminal 4 — Gateway
java -jar fos-gateway/target/fos-gateway-*.jar

# Terminal 5 — Frontend
cd fos-workspace-frontend && npm start
```

Open `http://localhost:4200` and verify:

- [ ] Login page renders with the Keycloak login button
- [ ] Clicking login redirects to Keycloak at `http://localhost:8180`
- [ ] After login, user is redirected back to the workspace with the navbar visible
- [ ] Documents page loads and shows the category filter and Upload button
- [ ] Uploading a PDF: dialog opens → file selected → Upload clicked → three-step progress → document appears in list
- [ ] Clicking a document opens the OnlyOffice viewer (if OnlyOffice container is running)
- [ ] Calendar page shows the FullCalendar grid
- [ ] Clicking a date opens the Create Event dialog
- [ ] Notification bell shows unread count badge
- [ ] Search bar finds documents by name

- [ ] **Step 4: Add proxy configuration for local development**

Create `src/proxy.conf.json` to avoid CORS issues when the Angular dev server calls the gateway:

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "info"
  }
}
```

Update `angular.json` to use the proxy in serve configuration:
```json
"serve": {
  "options": {
    "proxyConfig": "src/proxy.conf.json"
  }
}
```

Update `environment.ts` to use relative URL when proxy is active:
```typescript
// With proxy — use relative URL
apiBaseUrl: '',  // empty = same origin, proxy handles routing to http://localhost:8080
```

- [ ] **Step 5: Final commit**

```bash
git add .
git commit -m "chore(frontend): sprint 1.5 complete — Angular workspace frontend fully integrated"
```

---

## Sprint Test Criteria

Sprint 1.5 is complete when:

1. `npm run build` produces a clean production build with no TypeScript errors
2. `npm start` serves the app at `http://localhost:4200`
3. Login with Keycloak redirects correctly and lands on the documents page
4. The three-step document upload flow works end-to-end (initiate → MinIO → confirm)
5. The calendar shows events fetched from the backend; new events can be created
6. The player profile page shows role-gated tabs (Medical tab missing for Coaching Staff)
7. The notification inbox shows unread count badge in the navbar
8. The search bar returns results filtered by the caller's role
9. The OnlyOffice viewer opens for DOCX files (requires OnlyOffice container running)
10. All previous backend tests still pass: `mvn test -q` in `fos-workspace-service`

---

## What NOT to Include in This Sprint

- **Real-time notifications (WebSocket/SSE)** — Phase 2+
- **Drag-and-drop file upload** — Phase 2+
- **Offline mode / PWA** — Phase 2+
- **Angular unit tests** — They are deferred to Phase 2. The backend integration tests give sufficient coverage for Phase 1.
- **Mobile-responsive layout** — Phase 2+ (mobile app is separate per ARCHITECTURE.md)
- **Dark mode** — Phase 2+
- **Localization (Arabic for Saudi market)** — Phase 2+. The `i18n` infrastructure can be added when Angular's built-in i18n is configured.

---

## Phase 1 Complete — Summary

Phase 1 delivers the full Workspace module:

| Sprint | What was built |
|--------|----------------|
| 1.1    | Service scaffold, MongoDB, Mongock, Document domain, file upload flow |
| 1.2    | Calendar & event management, reminder strategy, OPA event policies |
| 1.3    | Player profiles (Facade), medical/admin docs, OnlyOffice config service |
| 1.4    | Kafka notification consumer, notification inbox, search, OnlyOffice save handler |
| 1.5    | Angular frontend — auth, documents, calendar, profiles, notifications, search |

**Backend test count at end of Phase 1:** 10+ integration tests in `fos-workspace-service`  
**Frontend:** Clean `ng build` production output  
**Ready for Phase 2:** `fos-ingest-service` (Wyscout/GPExe connectors, Saga pattern for import jobs)
