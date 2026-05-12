import { Routes } from '@angular/router';
import { AuthGuard } from './core/auth/auth.guard';
import { PublicAuthGuard } from './core/auth/public-auth.guard';
import { LandingPageComponent } from './features/landing-page/landing-page.component';
import { LoginPageComponent } from './features/login-page/login-page.component';
import { DocumentsComponent } from './features/documents/documents.component';
import { InboxComponent } from './features/inbox/inbox.component';
import { NotificationsComponent } from './features/notifications/notifications.component';
import { PlayerProfileComponent } from './features/player-profile/player-profile.component';
import { PlayersComponent } from './features/players/players.component';
import { RequestAccessPageComponent } from './features/request-access-page/request-access-page.component';
import { SettingsComponent } from './features/settings/settings.component';
import { WorkspaceCalendarComponent } from './features/workspace-calendar/workspace-calendar.component';
import { WorkspaceOnlyofficeEditorComponent } from './features/workspace-onlyoffice/workspace-onlyoffice-editor.component';
import { WorkspaceProfileComponent } from './features/workspace-profile/workspace-profile.component';

export const publicRoutes: Routes = [
  {
    path: '',
    component: LandingPageComponent,
    pathMatch: 'full',
    data: { animation: 'landing-page', fullScreen: true }
  },
  {
    path: 'login',
    component: LoginPageComponent,
    canActivate: [PublicAuthGuard],
    data: { animation: 'login-page', fullScreen: true }
  },
  {
    path: 'request-access',
    component: RequestAccessPageComponent,
    data: { animation: 'request-access-page', fullScreen: true }
  }
];

export const authenticatedRoutes: Routes = [
  {
    path: 'workspace',
    canActivate: [AuthGuard],
    canActivateChild: [AuthGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'calendar'
      },
      {
        path: 'calendar',
        component: WorkspaceCalendarComponent,
        data: { animation: 'workspace-calendar', fullHeight: true }
      },
      {
        path: 'documents/:documentId/editor',
        component: WorkspaceOnlyofficeEditorComponent,
        data: { animation: 'workspace-document-editor', fullHeight: true }
      },
      {
        path: 'documents',
        component: DocumentsComponent,
        data: { animation: 'documents' }
      },
      {
        path: 'onlyoffice-test/:documentId',
        component: WorkspaceOnlyofficeEditorComponent,
        data: { animation: 'workspace-onlyoffice-test', fullHeight: true, onlyofficeDebug: true }
      },
      {
        path: 'notifications',
        component: NotificationsComponent,
        data: { animation: 'notifications' }
      },
      {
        path: 'inbox',
        component: InboxComponent,
        data: { animation: 'inbox' }
      },
      {
        path: 'settings',
        component: SettingsComponent,
        data: { animation: 'settings' }
      },
      {
        path: 'players',
        component: PlayersComponent,
        data: { animation: 'players' }
      },
      {
        path: 'profile',
        component: WorkspaceProfileComponent,
        data: { animation: 'workspace-profile' }
      }
    ]
  },
  {
    path: 'documents',
    redirectTo: 'workspace/documents',
    pathMatch: 'full'
  },
  {
    path: 'players',
    redirectTo: 'workspace/players',
    pathMatch: 'full'
  },
  {
    path: 'players/:id',
    component: PlayerProfileComponent,
    canActivate: [AuthGuard],
    data: { animation: 'player-profile' }
  },
  {
    path: 'notifications',
    redirectTo: 'workspace/notifications',
    pathMatch: 'full'
  },
  {
    path: 'inbox',
    redirectTo: 'workspace/inbox',
    pathMatch: 'full'
  },
  {
    path: 'settings',
    redirectTo: 'workspace/settings',
    pathMatch: 'full'
  },
  {
    path: 'profile',
    redirectTo: 'workspace/profile',
    pathMatch: 'full'
  },
  {
    path: '**',
    redirectTo: ''
  }
];

export const routes: Routes = [...publicRoutes, ...authenticatedRoutes];
