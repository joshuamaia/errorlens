import { Routes } from '@angular/router';

import { analystGuard, authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Entrar · ErrorLens',
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · ErrorLens',
        loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'scenarios',
        title: 'Scenario Lab · ErrorLens',
        loadComponent: () => import('./features/scenarios/scenario-lab.page').then((m) => m.ScenarioLabPage),
      },
      {
        path: 'investigation',
        title: 'Investigation Center · ErrorLens',
        canActivate: [analystGuard],
        loadComponent: () => import('./features/investigation/investigation.page').then((m) => m.InvestigationPage),
      },
      {
        path: 'investigation/:id',
        title: 'Investigação · ErrorLens',
        canActivate: [analystGuard],
        loadComponent: () => import('./features/investigation/investigation.page').then((m) => m.InvestigationPage),
      },
      {
        path: 'comparator',
        title: 'Execution Comparator · ErrorLens',
        canActivate: [analystGuard],
        loadComponent: () => import('./features/comparator/comparator.page').then((m) => m.ComparatorPage),
      },
      {
        path: 'stacktrace',
        title: 'Stack Trace Analyzer · ErrorLens',
        canActivate: [analystGuard],
        loadComponent: () => import('./features/stacktrace/stacktrace.page').then((m) => m.StackTracePage),
      },
      {
        path: 'http-analyzer',
        title: 'HTTP Error Analyzer · ErrorLens',
        canActivate: [analystGuard],
        loadComponent: () => import('./features/http-analyzer/http-analyzer.page').then((m) => m.HttpAnalyzerPage),
      },
      {
        path: 'logs',
        title: 'Log Analyzer · ErrorLens',
        canActivate: [analystGuard],
        loadComponent: () => import('./features/logs/log-analyzer.page').then((m) => m.LogAnalyzerPage),
      },
      {
        path: 'history',
        title: 'Histórico · ErrorLens',
        canActivate: [analystGuard],
        loadComponent: () => import('./features/history/history.page').then((m) => m.HistoryPage),
      },
      {
        path: 'reports',
        title: 'Relatórios · ErrorLens',
        canActivate: [analystGuard],
        loadComponent: () => import('./features/reports/reports.page').then((m) => m.ReportsPage),
      },
      {
        path: 'tour',
        title: 'Guided Tour · ErrorLens',
        loadComponent: () => import('./features/tour/tour.page').then((m) => m.TourPage),
      },
      {
        path: 'settings',
        title: 'Configurações · ErrorLens',
        loadComponent: () => import('./features/settings/settings.page').then((m) => m.SettingsPage),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
