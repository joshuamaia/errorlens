import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { interval, startWith, switchMap, catchError, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { HealthReport } from '../core/models';
import { ThemeService } from '../core/theme.service';
import { TourService } from '../core/tour.service';
import { IconComponent } from '../shared/icon.component';
import { LogoComponent } from '../shared/logo.component';
import { TourPanelComponent } from './tour-panel.component';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  analyst?: boolean;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

@Component({
  selector: 'el-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, IconComponent, LogoComponent, TourPanelComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  protected readonly tour = inject(TourService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly health = signal<HealthReport | null>(null);
  protected readonly menuOpen = signal(false);
  protected readonly userMenu = signal(false);
  protected search = '';

  protected readonly nav: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: '/scenarios', label: 'Scenario Lab', icon: 'flask' },
    { path: '/investigation', label: 'Investigation Center', icon: 'search', analyst: true },
    { path: '/comparator', label: 'Execution Comparator', icon: 'arrow-left-right', analyst: true },
    { path: '/stacktrace', label: 'Stack Trace Analyzer', icon: 'code', analyst: true },
    { path: '/http-analyzer', label: 'HTTP Error Analyzer', icon: 'globe', analyst: true },
    { path: '/logs', label: 'Log Analyzer', icon: 'file-text', analyst: true },
    { path: '/history', label: 'Histórico', icon: 'history', analyst: true },
    { path: '/reports', label: 'Relatórios', icon: 'chart', analyst: true },
  ];

  constructor() {
    interval(30_000)
      .pipe(
        startWith(0),
        switchMap(() => this.api.health().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((report) => this.health.set(report));
  }

  protected submitSearch(): void {
    const term = this.search.trim();
    if (!term) return;
    this.search = '';
    if (/^#?\d+$/.test(term)) {
      this.router.navigate(['/investigation', term.replace('#', '')]);
    } else if (UUID.test(term)) {
      this.router.navigate(['/investigation'], { queryParams: { correlationId: term } });
    } else {
      this.router.navigate(['/history'], { queryParams: { q: term } });
    }
  }

  protected onSearchKey(event: KeyboardEvent, input: HTMLInputElement): void {
    if (event.key === 'Escape') input.blur();
  }

  protected focusSearch(event: KeyboardEvent, input: HTMLInputElement): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      input.focus();
    }
  }

  protected healthTone(status?: string): string {
    return status === 'UP' ? 'up' : status === 'DEGRADED' ? 'degraded' : status ? 'down' : '';
  }
}
