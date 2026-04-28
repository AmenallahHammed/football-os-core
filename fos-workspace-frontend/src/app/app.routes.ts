import { Routes } from '@angular/router';
import { DocumentsComponent } from './features/documents/documents.component';
import { HomeComponent } from './features/home/home.component';
import { InboxComponent } from './features/inbox/inbox.component';
import { NotificationsComponent } from './features/notifications/notifications.component';
import { PlayerProfileComponent } from './features/player-profile/player-profile.component';
import { PlayersComponent } from './features/players/players.component';
import { SettingsComponent } from './features/settings/settings.component';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'home'
  },
  {
    path: 'home',
    component: HomeComponent,
    data: { animation: 'home' }
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
    redirectTo: 'home'
  }
];
