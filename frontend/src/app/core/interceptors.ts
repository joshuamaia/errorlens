import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { NotifyService } from './notify.service';
import { ProblemDetail } from './models';

/** Gera um correlationId por requisição (o backend reutiliza o valor recebido). */
export function newCorrelationId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return 'el-' + Math.random().toString(16).slice(2) + Date.now().toString(16);
}

export const correlationInterceptor: HttpInterceptorFn = (req, next) =>
  next(req.headers.has('X-Correlation-Id') ? req : req.clone({ setHeaders: { 'X-Correlation-Id': newCorrelationId() } }));

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const isLogin = req.url.endsWith('/auth/login');
  const request = token && !isLogin ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isLogin) {
        auth.expire();
      }
      return throwError(() => error);
    }),
  );
};

/**
 * Exibe toasts amigáveis para falhas da API da plataforma (Problem Details), incluindo o
 * correlationId para investigação. Telas podem tratar seus próprios erros com o header
 * X-ErrorLens-Silent.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notify = inject(NotifyService);
  const silent = req.headers.has('X-ErrorLens-Silent');
  const request = silent ? req.clone({ headers: req.headers.delete('X-ErrorLens-Silent') }) : req;
  return next(request).pipe(
    catchError((error: unknown) => {
      if (!silent && error instanceof HttpErrorResponse && error.status !== 401) {
        notify.error(describeError(error));
      }
      return throwError(() => error);
    }),
  );
};

export function describeError(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'Não foi possível conectar ao backend. Verifique se os serviços estão em execução.';
  }
  const problem = error.error as ProblemDetail | null;
  if (problem && typeof problem === 'object' && 'detail' in problem && problem.detail) {
    return problem.detail + (problem.correlationId ? ` (correlationId ${problem.correlationId})` : '');
  }
  return `Erro HTTP ${error.status}: ${error.statusText || 'falha na requisição'}`;
}
