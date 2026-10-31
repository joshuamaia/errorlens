import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import {
  downloadBlob,
  filenameFrom,
  formatDateTime,
  formatDuration,
  prettyJson,
  simpleName,
} from '../../core/format';
import { ExecutionDetail, ExecutionSummary, LogLine, Timeline } from '../../core/models';
import { NotifyService } from '../../core/notify.service';
import { MethodBadgeComponent, OutcomeBadgeComponent, StatusBadgeComponent } from '../../shared/badges';
import { CodeViewerComponent } from '../../shared/code-viewer.component';
import { DiagnosisCardComponent } from '../../shared/diagnosis-card.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { IconComponent } from '../../shared/icon.component';
import { LogRow, LogTableComponent } from '../../shared/log-table.component';
import { TimelineViewComponent } from '../../shared/timeline-view.component';

type Tab = 'overview' | 'request' | 'response' | 'exception' | 'stacktrace' | 'timeline' | 'logs' | 'diagnosis';

@Component({
  selector: 'el-investigation-page',
  imports: [
    FormsModule,
    RouterLink,
    IconComponent,
    StatusBadgeComponent,
    OutcomeBadgeComponent,
    MethodBadgeComponent,
    CodeViewerComponent,
    DiagnosisCardComponent,
    TimelineViewComponent,
    LogTableComponent,
    EmptyStateComponent,
  ],
  templateUrl: './investigation.page.html',
  styleUrl: './investigation.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvestigationPage {
  readonly id = input<string>();
  readonly tab = input<string>();
  readonly correlationId = input<string>();

  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);
  protected readonly auth = inject(AuthService);

  protected readonly detail = signal<ExecutionDetail | null>(null);
  protected readonly timeline = signal<Timeline | null>(null);
  protected readonly logs = signal<LogLine[]>([]);
  protected readonly recent = signal<ExecutionSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly notFound = signal(false);
  protected readonly activeTab = signal<Tab>('overview');
  protected readonly exporting = signal<string | null>(null);
  protected lookup = '';

  protected readonly tabs: { key: Tab; label: string; icon: string }[] = [
    { key: 'overview', label: 'Overview', icon: 'eye' },
    { key: 'request', label: 'Request', icon: 'arrow-right' },
    { key: 'response', label: 'Response', icon: 'arrow-left-right' },
    { key: 'exception', label: 'Exception', icon: 'bug' },
    { key: 'stacktrace', label: 'Stack Trace', icon: 'code' },
    { key: 'timeline', label: 'Timeline', icon: 'waypoints' },
    { key: 'logs', label: 'Logs', icon: 'file-text' },
    { key: 'diagnosis', label: 'Diagnosis', icon: 'lightbulb' },
  ];

  protected readonly logRows = computed<LogRow[]>(() =>
    this.logs().map((l) => ({
      key: l.seq,
      timestamp: l.timestamp,
      level: l.level,
      logger: l.logger,
      thread: l.thread,
      message: l.message,
      exceptionClass: l.exceptionClass,
    })),
  );

  protected readonly requestHeadersJson = computed(() => JSON.stringify(this.detail()?.requestHeaders ?? {}, null, 2));
  protected readonly responseHeadersJson = computed(() => JSON.stringify(this.detail()?.responseHeaders ?? {}, null, 2));
  protected readonly requestBody = computed(() => prettyJson(this.detail()?.requestBody));
  protected readonly responseBody = computed(() => prettyJson(this.detail()?.responseBody));

  protected readonly fmtDuration = formatDuration;
  protected readonly fmtDate = formatDateTime;
  protected readonly simple = simpleName;

  constructor() {
    effect(() => {
      const id = this.id();
      const correlationId = this.correlationId();
      untracked(() => {
        if (id) this.load(Number(id));
        else if (correlationId) this.loadByCorrelation(correlationId);
        else this.loadRecent();
      });
    });
    effect(() => {
      const tab = this.tab() as Tab | undefined;
      if (tab && this.tabs.some((t) => t.key === tab)) this.activeTab.set(tab);
    });
  }

  protected selectTab(tab: Tab): void {
    this.activeTab.set(tab);
    this.router.navigate([], { queryParams: { tab }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  protected search(): void {
    const term = this.lookup.trim();
    if (!term) return;
    if (/^#?\d+$/.test(term)) this.router.navigate(['/investigation', term.replace('#', '')]);
    else this.router.navigate(['/investigation'], { queryParams: { correlationId: term } });
  }

  protected copyTechnicalInfo(): void {
    const d = this.detail();
    if (!d) return;
    const s = d.summary;
    const lines = [
      `ErrorLens — Execução #${s.id}`,
      `Cenário: ${s.scenarioCode} - ${s.scenarioName} (modo ${s.mode})`,
      `Endpoint: ${s.httpMethod} ${s.endpoint}`,
      `Status HTTP: ${s.httpStatus ?? '—'} (esperado ${s.expectedStatus}) — ${s.outcome}`,
      `Duração: ${formatDuration(s.durationMs)}`,
      `Início: ${s.startedAt}`,
      `CorrelationId: ${s.correlationId}`,
      `TraceId: ${s.traceId ?? '—'}`,
      `errorCode: ${s.errorCode ?? '—'}`,
      `Exception: ${s.exceptionClass ?? '—'}${d.exceptionMessage ? ': ' + d.exceptionMessage : ''}`,
      `Root cause: ${s.rootCauseClass ?? '—'}${d.rootCauseMessage ? ': ' + d.rootCauseMessage : ''}`,
      ...d.diagnoses.map((x, i) => `Diagnóstico ${i + 1}: ${x.title} (confiança ${x.confidence})`),
    ];
    navigator.clipboard
      .writeText(lines.join('\n'))
      .then(() => this.notify.success('Informações técnicas copiadas para a área de transferência.'))
      .catch(() => this.notify.error('Não foi possível acessar a área de transferência.'));
  }

  protected copy(value: string | null | undefined, label: string): void {
    if (!value) return;
    navigator.clipboard.writeText(value).then(() => this.notify.info(`${label} copiado.`));
  }

  protected export(format: 'json' | 'markdown' | 'pdf'): void {
    const d = this.detail();
    if (!d) return;
    this.exporting.set(format);
    this.api.report(d.summary.id, format).subscribe({
      next: (response) => {
        this.exporting.set(null);
        const ext = format === 'markdown' ? 'md' : format;
        downloadBlob(response.body!, filenameFrom(response.headers.get('Content-Disposition'), `errorlens-execucao-${d.summary.id}.${ext}`));
        this.notify.success(`Relatório ${format.toUpperCase()} exportado.`);
      },
      error: () => this.exporting.set(null),
    });
  }

  private load(id: number): void {
    this.loading.set(true);
    this.notFound.set(false);
    forkJoin({ detail: this.api.execution(id), timeline: this.api.timeline(id), logs: this.api.logs(id) }).subscribe({
      next: ({ detail, timeline, logs }) => {
        this.detail.set(detail);
        this.timeline.set(timeline);
        this.logs.set(logs);
        this.loading.set(false);
      },
      error: () => {
        this.detail.set(null);
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }

  private loadByCorrelation(correlationId: string): void {
    this.loading.set(true);
    this.api.executionByCorrelation(correlationId).subscribe({
      next: (detail) => this.router.navigate(['/investigation', detail.summary.id], { replaceUrl: true }),
      error: () => {
        this.loading.set(false);
        this.notFound.set(true);
      },
    });
  }

  private loadRecent(): void {
    this.detail.set(null);
    this.api.executions({ size: 10 }).subscribe({ next: (page) => this.recent.set(page.content), error: () => undefined });
  }
}
