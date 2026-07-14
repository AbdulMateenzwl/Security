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
  {
    path: 'chats/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/chats/conversation/conversation').then((m) => m.Conversation),
  },
  {
    path: 'chats/:id/tasks',
    canActivate: [authGuard],
    loadComponent: () => import('./features/tasks/task-board/task-board').then((m) => m.TaskBoard),
  },
  {
    path: 'security',
    canActivate: [authGuard],
    loadComponent: () => import('./features/security/security').then((m) => m.Security),
  },
  { path: '', pathMatch: 'full', redirectTo: 'chats' },
  { path: '**', redirectTo: 'chats' },
];
