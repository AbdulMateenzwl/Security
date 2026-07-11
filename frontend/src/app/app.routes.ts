import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: 'chats',
    canActivate: [authGuard],
    loadComponent: () => import('./features/chats/chats').then((m) => m.Chats),
  },
  { path: '', pathMatch: 'full', redirectTo: 'chats' },
  { path: '**', redirectTo: 'chats' },
];
