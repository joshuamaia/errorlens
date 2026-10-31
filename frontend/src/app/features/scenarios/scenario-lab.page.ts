import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { CATEGORY_LABELS, DIFFICULTY_LABELS, formatDateTime, formatDuration, simpleName } from '../../core/format';
import { ExecutionDetail, ExecutionMode, Scenario } from '../../core/models';
import { NotifyService } from '../../core/notify.service';
import { MethodBadgeComponent, OutcomeBadgeComponent, StatusBadgeComponent } from '../../shared/badges';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { IconComponent } from '../../shared/icon.component';
import { DialogComponent } from '../../shared/overlays';

interface Filter {
  key: string;
  label: string;
  match: (s: Scenario) => boolean;
}

const tag = (t: string) => (s: Scenario) => s.tags.includes(t);

@Component({
  selector: 'el-scenario-lab-page',
  imports: [RouterLink, DialogComponent, IconComponent, MethodBadgeComponent, StatusBadgeComponent, OutcomeBadgeComponent, EmptyStateComponent],
  templateUrl: './scenario-lab.page.html',
  styleUrl: './scenario-lab.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScenarioLabPage {
  /** Destaca um cenário vindo de outra tela (?code=D01). */
  readonly code = input<string>();

  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);
  protected readonly auth = inject(AuthService);

  protected readonly scenarios = signal<Scenario[]>([]);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly activeFilter = signal('ALL');
  protected readonly query = signal('');
  protected readonly running = signal<string | null>(null);
  protected readonly result = signal<ExecutionDetail | null>(null);
  protected readonly details = signal<Scenario | null>(null);

  protected readonly filters: Filter[] = [
    { key: 'ALL', label: 'Todos', match: () => true },
    { key: 'SUCCESS', label: 'Sucesso', match: (s) => s.expectedOutcome === 'SUCCESS' },
    { key: 'FAILURE', label: 'Falha', match: (s) => s.expectedOutcome !== 'SUCCESS' },
    { key: 'HTTP', label: 'HTTP', match: tag('http') },
    { key: 'VALIDACAO', label: 'Validação', match: tag('validacao') },
    { key: 'SEGURANCA', label: 'Segurança', match: tag('seguranca') },
    { key: 'BANCO', label: 'Banco de dados', match: tag('banco') },
    { key: 'EXCEPTIONS', label: 'Exceptions', match: tag('exceptions') },
    { key: 'RESILIENCIA', label: 'Resiliência', match: tag('resiliencia') },
    { key: 'INTEGRACAO', label: 'Integração', match: tag('integracao') },
  ];

  protected readonly filtered = computed(() => {
    const filter = this.filters.find((f) => f.key === this.activeFilter()) ?? this.filters[0];
    const q = this.query().trim().toLowerCase();
    return this.scenarios().filter(
      (s) =>
        filter.match(s) &&
        (!q || [s.code, s.name, s.description, s.failureType ?? '', s.endpoint].some((v) => v.toLowerCase().includes(q))),
    );
  });

  protected readonly groups = computed(() => {
    const map = new Map<string, Scenario[]>();
    for (const s of this.filtered()) {
      map.set(s.group, [...(map.get(s.group) ?? []), s]);
    }
    return [...map.entries()].map(([name, items]) => ({ name, items }));
  });

  protected readonly categoryLabel = (c: string) => CATEGORY_LABELS[c] ?? c;
  protected readonly difficultyLabel = (d: string) => DIFFICULTY_LABELS[d] ?? d;
  protected readonly fmtDuration = formatDuration;
  protected readonly fmtDate = formatDateTime;
  protected readonly simple = simpleName;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.scenarios().subscribe({
      next: (list) => {
        this.scenarios.set(list);
        this.loading.set(false);
        this.failed.set(false);
        const code = this.code();
        if (code) {
          queueMicrotask(() => document.getElementById('scenario-' + code)?.scrollIntoView({ block: 'center' }));
        }
      },
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      },
    });
  }

  protected run(scenario: Scenario, mode: ExecutionMode = 'DEFAULT'): void {
    this.running.set(scenario.code + ':' + mode);
    this.api.execute(scenario.code, mode).subscribe({
      next: (detail) => {
        this.running.set(null);
        this.result.set(detail);
        const s = detail.summary;
        const message = `${s.scenarioCode}: HTTP ${s.httpStatus ?? '—'} em ${formatDuration(s.durationMs)}`;
        if (s.outcome === 'SUCCESS') this.notify.success(message, 'Execução concluída');
        else if (s.outcome === 'DEGRADED') this.notify.warn(message, 'Resposta degradada');
        else this.notify.warn(message, 'Falha registrada');
        this.refreshStats();
      },
      error: () => this.running.set(null),
    });
  }

  protected compare(scenario: Scenario): void {
    this.running.set(scenario.code + ':COMPARE');
    this.api.compareModes(scenario.code).subscribe({
      next: (result) => {
        this.running.set(null);
        this.router.navigate(['/comparator'], {
          queryParams: { left: result.successExecutionId, right: result.failureExecutionId },
        });
      },
      error: () => this.running.set(null),
    });
  }

  protected isRunning(code: string, mode: string): boolean {
    return this.running() === code + ':' + mode;
  }

  protected openInvestigation(id: number, tab = 'overview'): void {
    this.router.navigate(['/investigation', id], { queryParams: { tab } });
  }

  private refreshStats(): void {
    this.api.scenarios().subscribe({ next: (list) => this.scenarios.set(list), error: () => undefined });
  }
}
