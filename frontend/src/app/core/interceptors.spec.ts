import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from './auth.service';
import { authInterceptor, correlationInterceptor, errorInterceptor } from './interceptors';
import { NotifyService } from './notify.service';

describe('interceptors HTTP', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([correlationInterceptor, authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  it('adiciona X-Correlation-Id e o token Bearer', () => {
    auth.login('admin', 'errorlens').subscribe();
    controller
      .expectOne('/api/v1/auth/login')
      .flush({ token: 'abc', username: 'admin', roles: ['ROLE_ADMIN'], expiresAt: new Date(Date.now() + 60_000).toISOString() });

    http.get('/api/v1/scenarios').subscribe();
    const request = controller.expectOne('/api/v1/scenarios');
    expect(request.request.headers.get('Authorization')).toBe('Bearer abc');
    expect(request.request.headers.get('X-Correlation-Id')).toMatch(/[0-9a-f-]{8,}/);
    request.flush([]);
  });

  it('não envia token na chamada de login', () => {
    auth.login('admin', 'x').subscribe({ error: () => undefined });
    const request = controller.expectOne('/api/v1/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({}, { status: 401, statusText: 'Unauthorized' });
  });

  it('expira a sessão em respostas 401', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    http.get('/api/v1/executions').subscribe({ error: () => undefined });
    controller.expectOne('/api/v1/executions').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: 1 } });
  });

  it('exibe toast com detail e correlationId do Problem Details', () => {
    const notify = TestBed.inject(NotifyService);
    http.get('/api/v1/scenarios/X/execute').subscribe({ error: () => undefined });
    controller
      .expectOne('/api/v1/scenarios/X/execute')
      .flush({ detail: 'Cenário X não encontrado.', correlationId: 'cid-12345678' }, { status: 404, statusText: 'Not Found' });
    expect(notify.toasts()[0].detail).toBe('Cenário X não encontrado. (correlationId cid-12345678)');
  });
});
