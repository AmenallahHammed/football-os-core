import { Routes } from '@angular/router';
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
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'calendar'
      },
      {
        path: 'calendar',
        component: WorkspaceCalendarComponent,
        data: { animation: 'workspace-calendar', fullScreen: true }
      }
    ]
  },
  {
    path: 'documents',
    component: DocumentsComponent,
    data: { animation: 'documents' }
  },
  {
    path: 'players',
    component: PlayersComponent,
    data: { animation: 'players' }
  },
  {
    path: 'players/:id',
    component: PlayerProfileComponent,
    data: { animation: 'player-profile' }
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
    path: '**',
    redirectTo: ''
  }
];

export const routes: Routes = [...publicRoutes, ...authenticatedRoutes];
