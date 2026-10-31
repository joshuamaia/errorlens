import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { downloadBlob, filenameFrom, formatDateTime, simpleName } from '../../core/format';
import { ExecutionSummary } from '../../core/models';
import { NotifyService } from '../../core/notify.service';
import { StatusBadgeComponent } from '../../shared/badges';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { IconComponent } from '../../shared/icon.component';

type Format = 'json' | 'markdown' | 'pdf';

@Component({
  selector: 'el-reports-page',
  imports: [FormsModule, RouterLink, IconComponent, StatusBadgeComponent, EmptyStateComponent],
  template: `
    <div class="page" data-testid="reports-page">
      <div class="page-header">
        <div class="page-icon"><el-icon name="chart" [size]="22" /></div>
        <div>
          <h1>Relatórios</h1>
          <p>Exportação de relatórios técnicos de investigação em JSON, Markdown e PDF (dados sanitizados)</p>
        </div>
      </div>

      <div class="grid grid-3">
        <div class="card fmt"><el-icon name="file-json" [size]="24" class="text-info" /><strong>JSON</strong><span class="small muted">Estrutura completa para integração com outras ferramentas.</span></div>
        <div class="card fmt"><el-icon name="file-text" [size]="24" class="text-success" /><strong>Markdown</strong><span class="small muted">Ideal para tickets, wikis e pull requests.</span></div>
        <div class="card fmt"><el-icon name="file-down" [size]="24" class="text-danger" /><strong>PDF</strong><span class="small muted">Documento para compartilhamento e auditoria.</span></div>
      </div>
      <div class="alert info small"><el-icon name="info" [size]="16" /> Cada relatório inclui identificação, resumo, status HTTP, exception, root cause, timeline, evidências, diagnóstico e recomendações.</div>

      <section class="card">
        <div class="card-header">
          <h3>Execuções</h3>
          <span class="spacer"></span>
          <label class="row small"><input type="checkbox" [(ngModel)]="onlyFailures" (ngModelChange)="load()" /> Somente falhas</label>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>#</th><th>Data</th><th>Cenário</th><th>Status</th><th>Exception</th><th>Exportar</th></tr></thead>
            <tbody>
              @for (e of executions(); track e.id) {
                <tr>
                  <td><a class="mono" [routerLink]="['/investigation', e.id]">#{{ e.id }}</a></td>
                  <td class="small">{{ fmtDate(e.startedAt) }}</td>
                  <td>{{ e.scenarioCode }} – {{ e.scenarioName }}</td>
                  <td><el-status-badge [status]="e.httpStatus" /></td>
                  <td class="mono small">{{ e.exceptionClass ? simple(e.exceptionClass) : '–' }}</td>
                  <td class="row">
                    @for (f of formats; track f) {
                      <button class="btn sm" type="button" [disabled]="busy() === e.id + f" (click)="export(e, f)" [attr.data-testid]="'report-' + f + '-' + e.id">
                        @if (busy() === e.id + f) { <el-icon name="loader" class="spin" [size]="13" /> }
                        {{ f === 'markdown' ? 'MD' : f.toUpperCase() }}
                      </button>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6"><el-empty-state icon="inbox" title="Nenhuma execução disponível" message="Execute cenários para gerar relatórios." /></td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
  styles: `
    .fmt { display: flex; flex-direction: column; gap: 6px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportsPage {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);

  protected readonly formats: Format[] = ['json', 'markdown', 'pdf'];
  protected readonly executions = signal<ExecutionSummary[]>([]);
  protected readonly busy = signal<string | null>(null);
  protected onlyFailures = true;
  protected readonly fmtDate = formatDateTime;
  protected readonly simple = simpleName;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.api.executions({ size: 30, outcome: this.onlyFailures ? 'FAILURE' : undefined }).subscribe({
      next: (page) => this.executions.set(page.content),
      error: () => undefined,
    });
  }

  protected export(execution: ExecutionSummary, format: Format): void {
    this.busy.set(execution.id + format);
    this.api.report(execution.id, format).subscribe({
      next: (response) => {
        this.busy.set(null);
        const ext = format === 'markdown' ? 'md' : format;
        downloadBlob(response.body!, filenameFrom(response.headers.get('Content-Disposition'), `errorlens-execucao-${execution.id}.${ext}`));
        this.notify.success(`Relatório #${execution.id} exportado (${format.toUpperCase()}).`);
      },
      error: () => this.busy.set(null),
    });
  }
}
