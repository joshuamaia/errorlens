import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { ExampleDescriptor, StackTraceAnalysis } from '../../core/models';
import { NotifyService } from '../../core/notify.service';
import { CodeViewerComponent } from '../../shared/code-viewer.component';
import { DiagnosisCardComponent } from '../../shared/diagnosis-card.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { IconComponent } from '../../shared/icon.component';
import { StackTraceViewComponent } from '../../shared/stack-trace-view.component';

const MAX_FILE_BYTES = 512 * 1024;

@Component({
  selector: 'el-stacktrace-page',
  imports: [FormsModule, IconComponent, CodeViewerComponent, StackTraceViewComponent, DiagnosisCardComponent, EmptyStateComponent],
  template: `
    <div class="page" data-testid="stacktrace-page">
      <div class="page-header">
        <div class="page-icon"><el-icon name="code" [size]="22" /></div>
        <div>
          <h1>Stack Trace Analyzer</h1>
          <p>Cole ou importe um stack trace Java: cadeia de causas, root cause, frames da aplicação e diagnóstico</p>
        </div>
      </div>

      <section class="card input-card">
        <div class="row wrap">
          <select class="input example" [ngModel]="''" (ngModelChange)="loadExample($event)" data-testid="stack-example">
            <option value="">Carregar exemplo…</option>
            @for (e of examples(); track e.id) { <option [value]="e.id">{{ e.title }}</option> }
          </select>
          <label class="btn">
            <el-icon name="upload" [size]="15" /> Importar arquivo
            <input type="file" accept=".txt,.log" hidden (change)="importFile($event)" />
          </label>
          <span class="spacer"></span>
          <input class="input packages" [(ngModel)]="packages" placeholder="Pacotes da aplicação (ex.: com.acme, dev.errorlens)" title="Opcional: define quais frames são da aplicação" />
        </div>
        <el-code-viewer [value]="content()" (valueChange)="content.set($event)" [readOnly]="false" language="java" height="300px" placeholder="Cole aqui o stack trace…" />
        <div class="row">
          <span class="small muted">{{ content().length.toLocaleString('pt-BR') }} caracteres (limite 512 KB)</span>
          <span class="spacer"></span>
          <button class="btn" type="button" (click)="content.set(''); analysis.set(null)">Limpar</button>
          <button class="btn primary" type="button" [disabled]="!content().trim() || loading()" (click)="analyze()" data-testid="analyze-stack">
            @if (loading()) { <el-icon name="loader" class="spin" [size]="15" /> } @else { <el-icon name="scan-search" [size]="15" /> }
            Analisar
          </button>
        </div>
      </section>

      @if (analysis(); as a) {
        <div class="grid grid-4">
          <div class="card stat"><span class="small muted">Exception principal</span><strong class="mono" data-testid="primary-exception">{{ a.primary?.simpleName ?? '—' }}</strong></div>
          <div class="card stat root"><span class="small muted">Root cause</span><strong class="mono" data-testid="root-cause-name">{{ a.rootCause?.simpleName ?? '—' }}</strong><span class="small muted ellipsis" [title]="a.rootCause?.message ?? ''">{{ a.rootCause?.message }}</span></div>
          <div class="card stat"><span class="small muted">Cadeia / suprimidas</span><strong>{{ a.chainLength }} níveis · {{ a.stats.suppressedCount }} suppressed</strong></div>
          <div class="card stat"><span class="small muted">Frames (aplicação / total / omitidos)</span><strong>{{ a.stats.applicationFrames }} / {{ a.stats.totalFrames }} / {{ a.stats.omittedFrames }}</strong></div>
        </div>

        @for (w of a.warnings; track $index) {
          <div class="alert warning small"><el-icon name="alert" [size]="16" /> {{ w }}</div>
        }

        @if (a.firstApplicationFrame; as f) {
          <div class="alert info" data-testid="first-app-frame">
            <el-icon name="crosshair" [size]="18" />
            <div><strong>Primeiro frame da aplicação próximo à root cause:</strong>
              <div class="mono small">{{ f.frame.className }}.{{ f.frame.method }}({{ f.frame.file }}{{ f.frame.line ? ':' + f.frame.line : '' }})</div></div>
          </div>
        }

        <div class="grid two">
          <section class="card">
            <div class="card-header"><el-icon name="list-tree" [size]="16" /><h3>Stack trace estruturado</h3></div>
            <el-stack-trace-view [exceptions]="a.exceptions" [rootIndex]="a.rootCause?.index ?? null" />
          </section>
          <div class="side">
            <section class="card">
              <div class="card-header"><el-icon name="chart-bar" [size]="16" /><h3>Frames por origem</h3></div>
              @for (entry of categories(); track entry[0]) {
                <div class="cat-row"><span>{{ entry[0] }}</span><span class="bar"><span [style.width.%]="(entry[1] / a.stats.totalFrames) * 100"></span></span><span class="small muted">{{ entry[1] }}</span></div>
              }
            </section>
            <section class="card">
              <div class="card-header"><el-icon name="boxes" [size]="16" /><h3>Classes envolvidas</h3></div>
              <ul class="classes">
                @for (c of a.involvedClasses.slice(0, 15); track c.className) {
                  <li [class.app]="c.category === 'APPLICATION'"><span class="mono small">{{ c.className }}</span><span class="small faint">{{ c.methods.join(', ') }}</span></li>
                }
              </ul>
            </section>
          </div>
        </div>

        <section class="diagnoses">
          <h2 class="section-title"><el-icon name="lightbulb" [size]="18" /> Diagnóstico</h2>
          <div class="alert info small"><el-icon name="info" [size]="16" /> {{ a.disclaimer }}</div>
          @for (d of a.diagnoses; track d.ruleId; let i = $index) {
            <el-diagnosis-card [diagnosis]="d" [index]="i" />
          } @empty {
            <el-empty-state icon="help" title="Nenhuma regra correspondente" message="As exceptions encontradas não correspondem às regras atuais do Diagnosis Engine." />
          }
        </section>
      }
    </div>
  `,
  styles: `
    .input-card { display: flex; flex-direction: column; gap: 12px; }
    .example { width: 340px; }
    .packages { width: 340px; }
    .stat { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
    .stat.root { border-color: rgba(239, 68, 68, 0.5); }
    .two { grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); }
    @media (max-width: 1100px) { .two { grid-template-columns: minmax(0, 1fr); } }
    .side { display: flex; flex-direction: column; gap: 16px; }
    .cat-row { display: grid; grid-template-columns: 130px 1fr 32px; gap: 8px; align-items: center; font-size: 12.5px; padding: 4px 0; }
    .bar { height: 8px; background: var(--el-surface-3); border-radius: 4px; overflow: hidden; }
    .bar span { display: block; height: 100%; background: var(--el-primary); }
    .classes { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
    .classes li { display: flex; flex-direction: column; overflow-wrap: anywhere; }
    .classes li.app .mono { color: var(--el-primary); font-weight: 600; }
    .diagnoses { display: flex; flex-direction: column; gap: 12px; }
    .section-title { display: flex; gap: 8px; align-items: center; font-size: 17px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StackTracePage {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);

  protected readonly content = signal<string>('');
  protected readonly analysis = signal<StackTraceAnalysis | null>(null);
  protected readonly loading = signal(false);
  protected readonly examples = signal<ExampleDescriptor[]>([]);
  protected packages = '';

  protected readonly categories = computed(() =>
    Object.entries(this.analysis()?.frameCategories ?? {}).sort((a, b) => b[1] - a[1]),
  );

  constructor() {
    const state = inject(Router).currentNavigation()?.extras.state as { content?: string } | undefined;
    const fromHistory = (history.state as { content?: string } | null)?.content;
    const initial = state?.content ?? fromHistory;
    if (initial) {
      this.content.set(initial);
      this.analyze();
    }
    this.api.examples('stacktrace').subscribe({ next: (list) => this.examples.set(list), error: () => undefined });
  }

  protected loadExample(id: string): void {
    if (!id) return;
    this.api.example(id).subscribe((example) => {
      this.content.set(example.content);
      this.analyze();
    });
  }

  protected importFile(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (file.size > MAX_FILE_BYTES) {
      this.notify.error('O arquivo excede o limite de 512 KB.');
      return;
    }
    file.text().then((text) => {
      this.content.set(text);
      this.analyze();
    });
    (event.target as HTMLInputElement).value = '';
  }

  protected analyze(): void {
    const content = this.content();
    if (!content.trim()) return;
    this.loading.set(true);
    const packages = this.packages.split(',').map((p) => p.trim()).filter(Boolean);
    this.api.analyzeStackTrace(content, packages).subscribe({
      next: (analysis) => {
        this.analysis.set(analysis);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
