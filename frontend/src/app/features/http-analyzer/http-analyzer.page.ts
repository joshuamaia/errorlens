import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiService } from '../../core/api.service';
import { ExampleDescriptor, HttpErrorAnalysis, HttpErrorInput } from '../../core/models';
import { StatusBadgeComponent } from '../../shared/badges';
import { DiagnosisCardComponent } from '../../shared/diagnosis-card.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'el-http-analyzer-page',
  imports: [ReactiveFormsModule, FormsModule, IconComponent, StatusBadgeComponent, DiagnosisCardComponent, EmptyStateComponent],
  template: `
    <div class="page" data-testid="http-analyzer-page">
      <div class="page-header">
        <div class="page-icon"><el-icon name="globe" [size]="22" /></div>
        <div>
          <h1>HTTP Error Analyzer</h1>
          <p>Interpreta Problem Details (RFC 9457), erros do Spring Boot e JSON comuns com regras determinísticas</p>
        </div>
      </div>

      <div class="grid layout">
        <form class="card form" [formGroup]="form" (ngSubmit)="analyze()">
          <div class="row wrap">
            <select class="input" [ngModel]="''" [ngModelOptions]="{ standalone: true }" (ngModelChange)="loadExample($event)" data-testid="http-example">
              <option value="">Carregar exemplo…</option>
              @for (e of examples(); track e.id) { <option [value]="e.id">{{ e.title }}</option> }
            </select>
          </div>
          <div class="grid req">
            <div class="field">
              <label>Método</label>
              <select class="input" formControlName="method">
                @for (m of methods; track m) { <option [value]="m">{{ m }}</option> }
              </select>
            </div>
            <div class="field">
              <label>URL</label>
              <input class="input mono" formControlName="url" placeholder="https://api.exemplo/recurso" />
            </div>
            <div class="field">
              <label>Status</label>
              <input class="input" type="number" formControlName="status" data-testid="http-status" />
            </div>
          </div>
          @if (form.controls.status.invalid && form.controls.status.touched) {
            <span class="form-error">Informe um status HTTP entre 100 e 599.</span>
          }
          <div class="field">
            <label>Headers de resposta (um por linha: Nome: valor)</label>
            <textarea class="input" rows="4" formControlName="headers" placeholder="Content-Type: application/problem+json"></textarea>
          </div>
          <div class="field">
            <label>Corpo da resposta</label>
            <textarea class="input" rows="12" formControlName="body" data-testid="http-body"></textarea>
          </div>
          <div class="row">
            <span class="small muted">A URL é apenas interpretada; nenhuma requisição é enviada.</span>
            <span class="spacer"></span>
            <button class="btn primary" type="submit" [disabled]="form.invalid || loading()" data-testid="analyze-http">
              @if (loading()) { <el-icon name="loader" class="spin" [size]="15" /> } @else { <el-icon name="scan-search" [size]="15" /> }
              Analisar
            </button>
          </div>
        </form>

        <div class="results">
          @if (analysis(); as a) {
            <section class="card">
              <div class="row wrap">
                <el-status-badge [status]="a.status" />
                <span class="badge">{{ a.statusClass }}</span>
                <span class="badge primary" data-testid="http-format">{{ formatLabel(a.format) }}</span>
                <span class="spacer"></span>
                <span class="mono small muted">{{ a.request.method }} {{ a.request.path ?? '' }}</span>
              </div>
              @if (extracted().length) {
                <dl class="kv extracted">
                  @for (e of extracted(); track e[0]) { <dt>{{ e[0] }}</dt><dd class="mono small">{{ e[1] }}</dd> }
                </dl>
              }
            </section>
            <section class="card">
              <div class="card-header"><el-icon name="list-checks" [size]="16" /><h3>Achados</h3></div>
              <div class="findings" data-testid="http-findings">
                @for (f of a.findings; track $index) {
                  <div class="alert small" [class.danger]="f.severity === 'ERROR'" [class.warning]="f.severity === 'WARN'" [class.info]="f.severity === 'INFO'">
                    <el-icon [name]="f.severity === 'ERROR' ? 'x-circle' : f.severity === 'WARN' ? 'alert' : 'info'" [size]="16" />
                    <div><strong>{{ f.title }}</strong><div>{{ f.description }}</div><div class="mono faint">{{ f.evidence }}</div><div class="muted">→ {{ f.suggestion }}</div></div>
                  </div>
                }
              </div>
            </section>
            @for (d of a.diagnoses; track d.ruleId; let i = $index) {
              <el-diagnosis-card [diagnosis]="d" [index]="i" />
            }
          } @else {
            <section class="card"><el-empty-state icon="globe" title="Nenhuma resposta analisada" message="Preencha os dados à esquerda ou carregue um exemplo." /></section>
          }
        </div>
      </div>
    </div>
  `,
  styles: `
    .layout { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); align-items: start; }
    @media (max-width: 1100px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .form { display: flex; flex-direction: column; gap: 12px; }
    .req { grid-template-columns: 110px 1fr 100px; gap: 10px; }
    .results { display: flex; flex-direction: column; gap: 12px; }
    .extracted { margin-top: 12px; }
    .findings { display: flex; flex-direction: column; gap: 8px; }
    .findings .alert div { line-height: 1.45; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HttpAnalyzerPage {
  private readonly api = inject(ApiService);

  protected readonly methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];
  protected readonly loading = signal(false);
  protected readonly analysis = signal<HttpErrorAnalysis | null>(null);
  protected readonly examples = signal<ExampleDescriptor[]>([]);

  protected readonly form = inject(NonNullableFormBuilder).group({
    method: ['POST'],
    url: ['http://localhost:8080/api/v1/lab/customers', [Validators.maxLength(2000)]],
    status: [409, [Validators.required, Validators.min(100), Validators.max(599)]],
    headers: ['Content-Type: application/problem+json'],
    body: ['', [Validators.maxLength(262144)]],
  });

  protected readonly extracted = computed(() =>
    Object.entries(this.analysis()?.extracted ?? {})
      .filter(([key]) => key !== 'errors')
      .map(([key, value]) => [key, typeof value === 'object' ? JSON.stringify(value) : String(value)]),
  );

  constructor() {
    this.api.examples('http').subscribe({ next: (list) => this.examples.set(list), error: () => undefined });
  }

  protected loadExample(id: string): void {
    if (!id) return;
    this.api.example(id).subscribe((example) => {
      const input = JSON.parse(example.content) as HttpErrorInput;
      this.form.setValue({ method: input.method, url: input.url, status: input.status, headers: input.headers, body: input.body });
      this.analyze();
    });
  }

  protected analyze(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.api.analyzeHttpError(this.form.getRawValue()).subscribe({
      next: (analysis) => {
        this.analysis.set(analysis);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected formatLabel(format: string): string {
    return (
      { PROBLEM_DETAILS: 'Problem Details (RFC 9457)', SPRING_BOOT_ERROR: 'Erro padrão Spring Boot', GENERIC_JSON: 'JSON genérico', HTML: 'HTML', TEXT: 'Texto', EMPTY: 'Sem corpo' }[
        format
      ] ?? format
    );
  }
}
