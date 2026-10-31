import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';

import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { formatDateTime } from '../../core/format';
import { NotifyService } from '../../core/notify.service';
import { ThemeService } from '../../core/theme.service';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'el-settings-page',
  imports: [IconComponent, JsonPipe],
  template: `
    <div class="page" data-testid="settings-page">
      <div class="page-header">
        <div class="page-icon"><el-icon name="settings" [size]="22" /></div>
        <div>
          <h1>Configurações</h1>
          <p>Preferências, sessão e administração do laboratório</p>
        </div>
      </div>

      <div class="grid grid-2">
        <section class="card">
          <div class="card-header"><el-icon name="sun" [size]="16" /><h3>Aparência</h3></div>
          <div class="row">
            <span>Tema atual: <strong>{{ theme.theme() === 'dark' ? 'Escuro' : 'Claro' }}</strong></span>
            <span class="spacer"></span>
            <button class="btn" type="button" (click)="theme.toggle()"><el-icon [name]="theme.theme() === 'dark' ? 'sun' : 'moon'" [size]="15" /> Alternar tema</button>
          </div>
          <p class="small muted">Idioma: Português (Brasil). Os textos estão centralizados para futura internacionalização.</p>
        </section>

        <section class="card">
          <div class="card-header"><el-icon name="user" [size]="16" /><h3>Sessão</h3></div>
          <dl class="kv">
            <dt>Usuário</dt><dd>{{ auth.username() }}</dd>
            <dt>Roles</dt><dd>{{ auth.roles().join(', ') }}</dd>
            <dt>Expira em</dt><dd>{{ expires() }}</dd>
          </dl>
          <button class="btn" type="button" (click)="auth.logout()"><el-icon name="logout" [size]="15" /> Sair</button>
        </section>

        <section class="card">
          <div class="card-header"><el-icon name="lightbulb" [size]="16" /><h3>Diagnosis Engine</h3></div>
          <p class="small muted">Regras locais e determinísticas (sem IA). Novas regras podem ser adicionadas em <code>backend/src/main/resources/diagnosis/rules.json</code> ou como beans <code>DiagnosisRule</code>.</p>
          @if (rules(); as r) {
            <p><strong>{{ r.count }}</strong> regras ativas</p>
            <div class="chips">@for (id of r.rules; track id) { <span class="badge">{{ id }}</span> }</div>
          }
        </section>

        <section class="card">
          <div class="card-header"><el-icon name="external-link" [size]="16" /><h3>Ferramentas</h3></div>
          <ul class="links">
            <li><a href="/swagger-ui.html" target="_blank" rel="noopener"><el-icon name="book" [size]="15" /> Swagger UI (OpenAPI)</a></li>
            <li><a [href]="jaegerUrl()" target="_blank" rel="noopener"><el-icon name="telescope" [size]="15" /> Jaeger UI (traces distribuídos)</a></li>
          </ul>
        </section>

        @if (auth.isAdmin()) {
          <section class="card">
            <div class="card-header"><el-icon name="refresh" [size]="16" /><h3>Reset do laboratório</h3></div>
            <p class="small muted">Reinicia os circuit breakers, remove comportamentos configurados no dependency-simulator e limpa dados descartáveis antigos criados pelos cenários. Dados semeados e o histórico são preservados.</p>
            <button class="btn danger" type="button" [disabled]="resetting()" (click)="reset()" data-testid="lab-reset"><el-icon name="refresh" [size]="15" [class.spin]="resetting()" /> Resetar laboratório</button>
            @if (resetResult()) { <pre class="code-block small">{{ resetResult() | json }}</pre> }
          </section>
        }

        <section class="card warn">
          <div class="card-header"><el-icon name="shield-alert" [size]="16" /><h3>Laboratório × produção</h3></div>
          <p class="small">O ErrorLens é um <strong>laboratório local</strong>: provoca falhas propositalmente e usa identidades de demonstração. Não o publique na internet sem autenticação forte, autorização revisada, TLS e endurecimento adicional. No perfil <code>prod</code> os endpoints de simulação e o Swagger são desabilitados.</p>
        </section>
      </div>
    </div>
  `,
  styles: `
    .links { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
    .links a { display: inline-flex; gap: 8px; align-items: center; }
    .warn { border-color: rgba(245, 176, 38, 0.45); }
    .card { display: flex; flex-direction: column; gap: 10px; }
    p { margin: 0; line-height: 1.5; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsPage {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  protected readonly theme = inject(ThemeService);
  protected readonly auth = inject(AuthService);
  protected readonly rules = signal<{ count: number; rules: string[] } | null>(null);
  protected readonly resetting = signal(false);
  protected readonly resetResult = signal<Record<string, unknown> | null>(null);
  protected readonly jaegerUrl = signal('http://localhost:16686');

  constructor() {
    if (this.auth.isAnalyst()) {
      this.api.rules().subscribe({ next: (r) => this.rules.set(r), error: () => undefined });
    }
    this.api.health().subscribe({ next: (h) => this.jaegerUrl.set(h.jaegerPublicUrl), error: () => undefined });
  }

  protected expires(): string {
    return formatDateTime(this.auth.session()?.expiresAt);
  }

  protected reset(): void {
    this.resetting.set(true);
    this.api.resetLab().subscribe({
      next: (result) => {
        this.resetResult.set(result);
        this.resetting.set(false);
        this.notify.success('Laboratório reiniciado.');
      },
      error: () => this.resetting.set(false),
    });
  }
}
