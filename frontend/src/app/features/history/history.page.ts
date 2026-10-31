import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { formatDateTime, formatDuration, simpleName } from '../../core/format';
import { ExecutionSummary, HistoryFilter, Page, Scenario } from '../../core/models';
import { ConfirmService } from '../../core/confirm.service';
import { NotifyService } from '../../core/notify.service';
import { OutcomeBadgeComponent, StatusBadgeComponent } from '../../shared/badges';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'el-history-page',
  imports: [ReactiveFormsModule, FormsModule, IconComponent, StatusBadgeComponent, OutcomeBadgeComponent, EmptyStateComponent],
  templateUrl: './history.page.html',
  styleUrl: './history.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoryPage {
  readonly q = input<string>();

  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);
  private readonly confirm = inject(ConfirmService);
  protected readonly auth = inject(AuthService);

  protected readonly page = signal<Page<ExecutionSummary> | null>(null);
  protected readonly scenarios = signal<Scenario[]>([]);
  protected readonly loading = signal(false);
  protected readonly selected = signal<number[]>([]);
  protected readonly sort = signal<{ field: string; direction: 'asc' | 'desc' }>({ field: 'startedAt', direction: 'desc' });
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected retentionDays = 30;

  protected readonly filters = inject(NonNullableFormBuilder).group({
    from: [''],
    to: [''],
    outcome: [''],
    httpStatus: [''],
    scenario: [''],
    correlationId: [''],
    exception: [''],
    q: [''],
  });

  protected readonly fmtDate = formatDateTime;
  protected readonly fmtDuration = formatDuration;
  protected readonly simple = simpleName;

  constructor() {
    this.api.scenarios().subscribe({ next: (s) => this.scenarios.set(s), error: () => undefined });
    effect(() => {
      const q = this.q();
      untracked(() => {
        if (q) this.filters.patchValue({ q });
        this.search();
      });
    });
  }

  protected search(resetPage = true): void {
    if (resetPage) this.pageIndex.set(0);
    const f = this.filters.getRawValue();
    const filter: HistoryFilter = {
      from: f.from ? new Date(f.from).toISOString() : undefined,
      to: f.to ? new Date(f.to).toISOString() : undefined,
      outcome: f.outcome || undefined,
      httpStatus: f.httpStatus ? Number(f.httpStatus) : undefined,
      scenario: f.scenario || undefined,
      correlationId: f.correlationId.trim() || undefined,
      exception: f.exception.trim() || undefined,
      q: f.q.trim() || undefined,
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: this.sort().field,
      direction: this.sort().direction,
    };
    this.loading.set(true);
    this.api.executions(filter).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected clear(): void {
    this.filters.reset();
    this.search();
  }

  protected sortBy(field: string): void {
    const current = this.sort();
    this.sort.set({ field, direction: current.field === field && current.direction === 'desc' ? 'asc' : 'desc' });
    this.search();
  }

  protected sortIcon(field: string): string {
    const s = this.sort();
    return s.field !== field ? '' : s.direction === 'desc' ? '↓' : '↑';
  }

  protected go(index: number): void {
    this.pageIndex.set(index);
    this.search(false);
  }

  protected toggle(id: number, event: Event): void {
    event.stopPropagation();
    const current = this.selected();
    if (current.includes(id)) this.selected.set(current.filter((x) => x !== id));
    else this.selected.set([...current, id].slice(-2));
  }

  protected open(id: number): void {
    this.router.navigate(['/investigation', id]);
  }

  protected compareSelected(): void {
    const [left, right] = this.selected();
    this.router.navigate(['/comparator'], { queryParams: { left, right } });
  }

  protected remove(execution: ExecutionSummary, event: Event): void {
    event.stopPropagation();
    this.confirm
      .ask('Excluir execução', `Excluir a execução #${execution.id} (${execution.scenarioCode}) e seus logs, etapas e diagnósticos?`, 'Excluir')
      .then((accepted) => {
        if (!accepted) return;
        this.api.deleteExecution(execution.id).subscribe(() => {
          this.notify.success(`Execução #${execution.id} excluída.`);
          this.selected.set(this.selected().filter((x) => x !== execution.id));
          this.search(false);
        });
      });
  }

  protected purge(): void {
    const days = Math.max(1, Math.floor(this.retentionDays));
    this.confirm.ask('Aplicar retenção', `Remover todas as execuções com mais de ${days} dia(s)?`, 'Remover').then((accepted) => {
      if (!accepted) return;
      this.api.purge(days).subscribe((result) => {
        this.notify.success(`${result.removed} execução(ões) removida(s).`);
        this.search();
      });
    });
  }
}
