import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import type { EChartsCoreOption } from 'echarts/core';

import { ApiService } from '../../core/api.service';
import { formatDateTime, formatDuration, simpleName } from '../../core/format';
import { ExampleDescriptor, LogAnalysis } from '../../core/models';
import { NotifyService } from '../../core/notify.service';
import { ChartDirective, chartPalette } from '../../shared/chart.directive';
import { CodeViewerComponent } from '../../shared/code-viewer.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { IconComponent } from '../../shared/icon.component';
import { LogRow, LogTableComponent } from '../../shared/log-table.component';

const MAX_FILE_BYTES = 2 * 1024 * 1024;
const ALLOWED = ['log', 'txt', 'json'];

@Component({
  selector: 'el-log-analyzer-page',
  imports: [FormsModule, RouterLink, IconComponent, CodeViewerComponent, LogTableComponent, ChartDirective, EmptyStateComponent],
  template: `
    <div class="page" data-testid="log-analyzer-page">
      <div class="page-header">
        <div class="page-icon"><el-icon name="file-text" [size]="22" /></div>
        <div>
          <h1>Log Analyzer</h1>
          <p>Importe logs .log, .txt ou .json: níveis, correlationId, exceptions e visualização cronológica</p>
        </div>
      </div>

      <section class="card input-card">
        <div class="row wrap">
          <select class="input example" [ngModel]="''" (ngModelChange)="loadExample($event)" data-testid="log-example">
            <option value="">Carregar exemplo…</option>
            @for (e of examples(); track e.id) { <option [value]="e.id">{{ e.title }}</option> }
          </select>
          <label class="btn" data-testid="log-upload">
            <el-icon name="upload" [size]="15" /> Importar arquivo (.log, .txt, .json · até 2 MB)
            <input type="file" accept=".log,.txt,.json" hidden (change)="upload($event)" />
          </label>
          <span class="spacer"></span>
          <button class="btn primary" type="button" [disabled]="!content().trim() || loading()" (click)="analyzeText()" data-testid="analyze-logs">
            @if (loading()) { <el-icon name="loader" class="spin" [size]="15" /> } @else { <el-icon name="scan-search" [size]="15" /> }
            Analisar texto
          </button>
        </div>
        <el-code-viewer [value]="content()" (valueChange)="content.set($event)" [readOnly]="false" language="plaintext" height="200px" placeholder="Ou cole os logs aqui…" />
      </section>

      @if (analysis(); as a) {
        <div class="grid grid-5">
          <div class="card stat"><span class="small muted">Entradas</span><strong data-testid="log-total">{{ a.totalEntries }}</strong><span class="small faint">{{ a.format }} · {{ a.totalLines }} linhas</span></div>
          <div class="card stat"><span class="small muted">Erros</span><strong class="text-danger">{{ a.levelCounts['ERROR'] }}</strong></div>
          <div class="card stat"><span class="small muted">Alertas</span><strong class="text-warning">{{ a.levelCounts['WARN'] }}</strong></div>
          <div class="card stat"><span class="small muted">correlationIds</span><strong>{{ a.groups.length }}</strong></div>
          <div class="card stat"><span class="small muted">Período</span><span class="small">{{ fmtDate(a.from) }}<br />{{ fmtDate(a.to) }}</span></div>
        </div>
        @for (w of a.warnings; track $index) { <div class="alert warning small"><el-icon name="alert" [size]="16" /> {{ w }}</div> }

        <section class="card">
          <div class="card-header"><el-icon name="activity" [size]="16" /><h3>Visualização cronológica</h3></div>
          <div class="chart" [elChart]="timelineChart()"></div>
        </section>

        <div class="grid grid-2">
          <section class="card">
            <div class="card-header"><el-icon name="waypoints" [size]="16" /><h3>Agrupamento por correlationId</h3></div>
            <div class="table-wrap">
              <table class="table" data-testid="log-groups">
                <thead><tr><th>correlationId</th><th>Eventos</th><th>Erros</th><th>Duração</th><th>Exceptions</th></tr></thead>
                <tbody>
                  @for (g of a.groups; track g.correlationId) {
                    <tr class="clickable" [class.selected]="selectedGroup() === g.correlationId" (click)="toggleGroup(g.correlationId)">
                      <td class="mono small">{{ g.correlationId }}</td><td>{{ g.count }}</td>
                      <td><span class="badge" [class.danger]="g.errorCount > 0">{{ g.errorCount }}</span></td>
                      <td class="small mono">{{ fmtDuration(g.durationMs) }}</td>
                      <td class="small mono">{{ simplify(g.exceptions) }}</td>
                    </tr>
                  } @empty { <tr><td colspan="5" class="muted small">Nenhum correlationId identificado nos logs.</td></tr> }
                </tbody>
              </table>
            </div>
          </section>
          <section class="card">
            <div class="card-header"><el-icon name="bug" [size]="16" /><h3>Exceptions encontradas</h3></div>
            <ul class="exc">
              @for (e of a.exceptions; track e.className) {
                <li><button class="link mono" type="button" (click)="searchException(e.className)">{{ e.className }}</button><span class="badge danger">{{ e.count }}</span></li>
              } @empty { <li class="muted small">Nenhuma exception identificada.</li> }
            </ul>
            <p class="small muted">Para correlationIds de execuções do laboratório, use a busca global ou o <a routerLink="/history">histórico</a>.</p>
          </section>
        </div>

        <section class="card">
          <div class="card-header"><el-icon name="list-tree" [size]="16" /><h3>Entradas</h3>
            @if (selectedGroup()) { <span class="badge primary">filtrado por {{ selectedGroup() }}</span><button class="btn ghost sm" type="button" (click)="selectedGroup.set(null)">limpar</button> }
          </div>
          <div class="row wrap period">
            <label class="small muted">De</label><input class="input" type="datetime-local" step="1" [ngModel]="from()" (ngModelChange)="from.set($event)" />
            <label class="small muted">Até</label><input class="input" type="datetime-local" step="1" [ngModel]="to()" (ngModelChange)="to.set($event)" />
          </div>
          <el-log-table [rows]="rows()" [showCorrelation]="true" />
        </section>
      } @else if (!loading()) {
        <section class="card"><el-empty-state icon="file-text" title="Nenhum log analisado" message="Importe um arquivo, cole o conteúdo ou carregue um exemplo." /></section>
      }
    </div>
  `,
  styles: `
    .input-card { display: flex; flex-direction: column; gap: 12px; }
    .example { width: 360px; }
    .stat { display: flex; flex-direction: column; gap: 4px; }
    .stat strong { font-size: 22px; }
    .chart { height: 200px; }
    .exc { list-style: none; margin: 0 0 10px; padding: 0; display: flex; flex-direction: column; gap: 6px; }
    .exc li { display: flex; justify-content: space-between; gap: 8px; align-items: center; }
    .link { background: none; border: none; color: var(--el-text); cursor: pointer; padding: 0; text-align: left; overflow-wrap: anywhere; font-size: 12px; }
    .link:hover { color: var(--el-primary); }
    .period { margin-bottom: 10px; }
    .period .input { width: 220px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogAnalyzerPage {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);

  protected readonly content = signal('');
  protected readonly analysis = signal<LogAnalysis | null>(null);
  protected readonly loading = signal(false);
  protected readonly examples = signal<ExampleDescriptor[]>([]);
  protected readonly selectedGroup = signal<string | null>(null);
  protected readonly from = signal('');
  protected readonly to = signal('');

  protected readonly fmtDate = formatDateTime;
  protected readonly fmtDuration = formatDuration;

  protected readonly rows = computed<LogRow[]>(() => {
    const a = this.analysis();
    if (!a) return [];
    const group = this.selectedGroup();
    const from = this.from() ? new Date(this.from()).getTime() : null;
    const to = this.to() ? new Date(this.to()).getTime() : null;
    return a.entries
      .filter((e) => !group || e.correlationId === group)
      .filter((e) => {
        if (!e.timestamp) return from === null && to === null;
        const t = new Date(e.timestamp).getTime();
        return (from === null || t >= from) && (to === null || t <= to);
      })
      .map((e) => ({
        key: e.index,
        timestamp: e.timestamp,
        level: e.level,
        logger: e.logger,
        thread: e.thread,
        message: e.message,
        exceptionClass: e.exceptionClass,
        correlationId: e.correlationId,
        details: e.details,
      }));
  });

  protected readonly timelineChart = computed(() => {
    const entries = (this.analysis()?.entries ?? []).filter((e) => e.timestamp);
    const levels = ['ERROR', 'WARN', 'INFO', 'DEBUG'];
    return (p: ReturnType<typeof chartPalette>): EChartsCoreOption => ({
      tooltip: {
        trigger: 'item',
        backgroundColor: p.surface,
        borderColor: p.border,
        textStyle: { color: p.text },
        formatter: (item: { data: [number, string, string] }) =>
          `${new Date(item.data[0]).toLocaleTimeString('pt-BR')}<br/>${item.data[1]}: ${item.data[2]}`,
      },
      grid: { left: 60, right: 16, top: 10, bottom: 28 },
      xAxis: { type: 'time', axisLabel: { color: p.muted }, axisLine: { lineStyle: { color: p.border } }, splitLine: { show: false } },
      yAxis: { type: 'category', data: levels, axisLabel: { color: p.muted }, splitLine: { lineStyle: { color: p.border, type: 'dashed' } } },
      series: [
        {
          type: 'scatter',
          symbolSize: 9,
          data: entries.map((e) => [new Date(e.timestamp!).getTime(), e.level, (e.message ?? '').slice(0, 80)]),
          itemStyle: {
            color: (params: { data: [number, string] }) =>
              ({ ERROR: p.danger, WARN: p.warning, INFO: p.info })[params.data[1]] ?? p.muted,
          },
        },
      ],
    });
  });

  constructor() {
    this.api.examples('log').subscribe({ next: (list) => this.examples.set(list), error: () => undefined });
  }

  protected loadExample(id: string): void {
    if (!id) return;
    this.api.example(id).subscribe((example) => {
      this.content.set(example.content);
      this.analyzeText(example.descriptor.file);
    });
  }

  protected upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
    if (!ALLOWED.includes(extension)) {
      this.notify.error('Tipo de arquivo não suportado. Use .log, .txt ou .json.');
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      this.notify.error('O arquivo excede o limite de 2 MB.');
      return;
    }
    this.loading.set(true);
    this.api.analyzeLogFile(file).subscribe({
      next: (analysis) => this.done(analysis),
      error: () => this.loading.set(false),
    });
  }

  protected analyzeText(source?: string): void {
    if (!this.content().trim()) return;
    this.loading.set(true);
    this.api.analyzeLogText(this.content(), source).subscribe({
      next: (analysis) => this.done(analysis),
      error: () => this.loading.set(false),
    });
  }

  protected toggleGroup(id: string): void {
    this.selectedGroup.set(this.selectedGroup() === id ? null : id);
  }

  protected searchException(className: string): void {
    this.selectedGroup.set(null);
    this.notify.info(`Use a busca da tabela para filtrar por ${simpleName(className)}.`);
  }

  protected simplify(names: string[]): string {
    return names.map((n) => simpleName(n)).join(', ') || '—';
  }

  private done(analysis: LogAnalysis): void {
    this.analysis.set(analysis);
    this.selectedGroup.set(null);
    this.loading.set(false);
  }
}
