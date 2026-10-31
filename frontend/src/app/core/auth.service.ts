import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { ApiService } from './api.service';
import { Session } from './models';

const STORAGE_KEY = 'errorlens.session';

/**
 * Sessão do usuário. O token opaco fica em sessionStorage (escopo da aba) e expira no servidor.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly sessionState = signal<Session | null>(AuthService.restore());

  readonly session = this.sessionState.asReadonly();
  readonly isAuthenticated = computed(() => this.sessionState() !== null);
  readonly username = computed(() => this.sessionState()?.username ?? '');
  readonly roles = computed(() => this.sessionState()?.roles ?? []);
  readonly isAnalyst = computed(() => this.roles().some((r) => r === 'ROLE_ANALYST' || r === 'ROLE_ADMIN'));
  readonly isAdmin = computed(() => this.roles().includes('ROLE_ADMIN'));
  readonly primaryRole = computed(() => {
    const roles = this.roles();
    if (roles.includes('ROLE_ADMIN')) return 'ADMIN';
    if (roles.includes('ROLE_ANALYST')) return 'ANALYST';
    return roles[0]?.replace('ROLE_', '') ?? '';
  });

  token(): string | null {
    const session = this.sessionState();
    if (session && new Date(session.expiresAt).getTime() < Date.now()) {
      this.clear();
      return null;
    }
    return session?.token ?? null;
  }

  login(username: string, password: string): Observable<Session> {
    return this.api.login(username, password).pipe(
      tap((session) => {
        this.sessionState.set(session);
        try {
          sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        } catch {
          // armazenamento indisponível: a sessão continua apenas em memória
        }
      }),
    );
  }

  logout(): void {
    if (this.sessionState()) {
      this.api.logout().subscribe({ error: () => undefined });
    }
    this.clear();
    this.router.navigate(['/login']);
  }

  /** Sessão expirada/inválida detectada por uma resposta 401. */
  expire(): void {
    this.clear();
    this.router.navigate(['/login'], { queryParams: { expired: 1 } });
  }

  private clear(): void {
    this.sessionState.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignorado
    }
  }

  private static restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const session = JSON.parse(raw) as Session;
      return new Date(session.expiresAt).getTime() > Date.now() ? session : null;
    } catch {
      return null;
    }
  }
}
