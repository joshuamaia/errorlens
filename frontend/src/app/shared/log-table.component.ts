import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { formatTime, levelTone } from '../core/format';
import { IconComponent } from './icon.component';

export interface LogRow {
  key: number;
  timestamp: string | null;
  level: string;
  logger: string | null;
  thread?: string | null;
  message: string | null;
  exceptionClass?: string | null;
  correlationId?: string | null;
  details?: string | null;
}

/** Tabela de logs com filtro por nível, busca textual e destaque de erros. */
@Component({
  selector: 'el-log-table',
  imports: [IconComponent],
  template: `
    <div class="row wrap filters">
      <div class="chips">
        @for (level of levels; track level) {
          <button type="button" class="chip" [class.active]="selected().has(level)" (click)="toggle(level)">
            <span class="dot {{ tone(level) }}"></span>{{ level }} <span class="faint">{{ count(level) }}</span>
          </button>
        }
      </div>
      <span class="spacer"></span>
      <div class="search">
        <el-icon name="search" [size]="14" />
        <input class="input" placeholder="Buscar mensagem, logger ou exception" [value]="query()" (input)="query.set($any($event.target).value)" />
      </div>
    </div>
    <div class="table-wrap">
      <table class="table" data-testid="log-table">
        <thead>
          <tr><th>Horário</th><th>Nível</th><th>Logger</th><th>Mensagem</th></tr>
        </thead>
        <tbody>
          @for (row of filtered(); track row.key) {
            <tr [class.error-row]="row.level === 'ERROR'" [class.warn-row]="row.level === 'WARN'">
              <td class="mono small nowrap">{{ time(row.timestamp) }}</td>
              <td><span class="badge {{ tone(row.level) }}">{{ row.level }}</span></td>
              <td class="mono small logger" [title]="row.logger ?? ''">{{ shortLogger(row.logger) }}</td>
              <td>
                <div class="message">{{ row.message }}</div>
                @if (row.exceptionClass) {
                  <div class="small text-danger mono">{{ row.exceptionClass }}</div>
                }
                @if (row.correlationId && showCorrelation()) {
                  <div class="small faint mono">correlationId {{ row.correlationId }}</div>
                }
                @if (row.details) {
                  <details><summary class="small muted">detalhes / stack trace</summary><pre class="code-block">{{ row.details }}</pre></details>
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="4" class="muted">Nenhum log corresponde aos filtros.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .filters { margin-bottom: 10px; }
    .search { position: relative; min-width: 260px; flex: 0 1 360px; }
    .search el-icon { position: absolute; left: 10px; top: 11px; color: var(--el-text-faint); }
    .search .input { padding-left: 30px; }
    .nowrap { white-space: nowrap; }
    .logger { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .message { white-space: pre-wrap; overflow-wrap: anywhere; }
    tr.error-row td { background: rgba(239, 68, 68, 0.06); }
    tr.warn-row td { background: rgba(245, 176, 38, 0.05); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogTableComponent {
  readonly rows = input.required<LogRow[]>();
  readonly showCorrelation = input(false);
  readonly levels = ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'];
  readonly selected = signal(new Set(['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE']));
  readonly query = signal('');

  readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const selected = this.selected();
    return this.rows().filter(
      (row) =>
        selected.has(row.level) &&
        (!q ||
          [row.message, row.logger, row.exceptionClass, row.correlationId, row.details]
            .filter(Boolean)
            .some((v) => v!.toLowerCase().includes(q))),
    );
  });

  toggle(level: string): void {
    const next = new Set(this.selected());
    if (next.has(level)) next.delete(level);
    else next.add(level);
    this.selected.set(next);
  }

  count(level: string): number {
    return this.rows().filter((r) => r.level === level).length;
  }

  tone(level: string): string {
    return levelTone(level);
  }

  time(ts: string | null): string {
    return formatTime(ts);
  }

  shortLogger(logger: string | null): string {
    if (!logger) return '—';
    const parts = logger.split('.');
    return parts.length <= 2 ? logger : parts.slice(0, -1).map((p) => p[0]).join('.') + '.' + parts.at(-1);
  }
}
