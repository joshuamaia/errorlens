import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { describeError } from '../../core/interceptors';
import { IconComponent } from '../../shared/icon.component';
import { LogoComponent } from '../../shared/logo.component';

interface DemoIdentity {
  username: string;
  password: string;
  role: string;
  description: string;
}

@Component({
  selector: 'el-login-page',
  imports: [ReactiveFormsModule, IconComponent, LogoComponent],
  template: `
    <div class="login">
      <section class="hero">
        <el-logo [size]="64" />
        <h1>ErrorLens</h1>
        <p class="tagline">Execute. Trace. Investigate. Understand.</p>
        <ul>
          <li><el-icon name="flask" /> 25 cenários reais de sucesso e falha</li>
          <li><el-icon name="waypoints" /> Timeline instrumentada, correlationId e OpenTelemetry</li>
          <li><el-icon name="lightbulb" /> Diagnosis Engine com regras locais e determinísticas</li>
          <li><el-icon name="compare" /> Comparação entre execuções e Guided Tour</li>
        </ul>
      </section>

      <form class="card form" [formGroup]="form" (ngSubmit)="submit()" data-testid="login-form">
        <h2>Entrar no laboratório</h2>
        @if (expired()) {
          <div class="alert warning small"><el-icon name="clock" [size]="16" /> Sua sessão expirou. Entre novamente.</div>
        }
        <div class="field">
          <label for="username">Usuário</label>
          <input id="username" class="input" formControlName="username" autocomplete="username" data-testid="login-username" />
        </div>
        <div class="field">
          <label for="password">Senha</label>
          <input id="password" class="input" type="password" formControlName="password" autocomplete="current-password" data-testid="login-password" />
        </div>
        @if (error()) {
          <div class="alert danger small" data-testid="login-error"><el-icon name="x-circle" [size]="16" /> {{ error() }}</div>
        }
        <button class="btn primary" type="submit" [disabled]="form.invalid || loading()" data-testid="login-submit">
          @if (loading()) { <el-icon name="loader" class="spin" [size]="16" /> }
          Entrar
        </button>

        <div class="demo">
          <p class="small muted"><el-icon name="key" [size]="14" /> Identidades locais de demonstração (somente laboratório):</p>
          @for (id of identities; track id.username) {
            <button type="button" class="identity" (click)="use(id)">
              <strong class="mono">{{ id.username }}</strong>
              <span class="badge primary">{{ id.role }}</span>
              <span class="small muted">{{ id.description }}</span>
            </button>
          }
        </div>
      </form>
    </div>
  `,
  styles: `
    .login { min-height: 100vh; display: grid; grid-template-columns: 1.1fr 1fr; align-items: center; gap: 48px; padding: 48px; max-width: 1200px; margin: 0 auto; }
    .hero h1 { font-size: 44px; margin-top: 12px; }
    .tagline { color: var(--el-primary); font-weight: 600; margin: 6px 0 24px; }
    .hero ul { list-style: none; padding: 0; display: flex; flex-direction: column; gap: 12px; color: var(--el-text-muted); }
    .hero li { display: flex; gap: 10px; align-items: center; }
    .form { display: flex; flex-direction: column; gap: 14px; padding: 28px; }
    .form h2 { font-size: 20px; }
    .demo { border-top: 1px solid var(--el-border); padding-top: 14px; display: flex; flex-direction: column; gap: 6px; }
    .demo p { display: flex; align-items: center; gap: 6px; margin: 0 0 4px; }
    .identity { display: grid; grid-template-columns: 90px auto 1fr; gap: 10px; align-items: center; text-align: left; padding: 8px 10px; border-radius: 8px; border: 1px solid var(--el-border); background: var(--el-surface-2); color: var(--el-text); cursor: pointer; }
    .identity:hover { border-color: var(--el-primary); }
    @media (max-width: 900px) { .login { grid-template-columns: 1fr; padding: 20px; gap: 24px; } .hero ul { display: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  readonly expired = input<string>();
  readonly returnUrl = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly identities: DemoIdentity[] = [
    { username: 'admin', password: 'errorlens', role: 'ADMIN', description: 'Acesso completo e reset do laboratório' },
    { username: 'analyst', password: 'analyst-lab', role: 'ANALYST', description: 'Executa cenários e investiga' },
    { username: 'viewer', password: 'viewer-lab', role: 'VIEWER', description: 'Somente dashboard e catálogo' },
  ];

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['admin', [Validators.required, Validators.maxLength(60)]],
    password: ['errorlens', [Validators.required, Validators.maxLength(200)]],
  });

  protected use(identity: DemoIdentity): void {
    this.form.setValue({ username: identity.username, password: identity.password });
  }

  protected submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => {
        this.loading.set(false);
        const target = this.returnUrl();
        this.router.navigateByUrl(target && target.startsWith('/') && !target.startsWith('//') ? target : '/dashboard');
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(err.status === 401 ? 'Usuário ou senha inválidos.' : describeError(err));
      },
    });
  }
}
