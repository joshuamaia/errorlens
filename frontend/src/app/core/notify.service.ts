import { Injectable, signal } from '@angular/core';

export type ToastSeverity = 'success' | 'info' | 'warn' | 'error';

export interface Toast {
  id: number;
  severity: ToastSeverity;
  summary: string;
  detail: string;
}

/** Toasts padronizados, exibidos pelo ToastContainerComponent. */
@Injectable({ providedIn: 'root' })
export class NotifyService {
  private sequence = 0;
  readonly toasts = signal<Toast[]>([]);

  success(detail: string, summary = 'Sucesso'): void {
    this.push('success', summary, detail, 3500);
  }

  info(detail: string, summary = 'Informação'): void {
    this.push('info', summary, detail, 3500);
  }

  warn(detail: string, summary = 'Atenção'): void {
    this.push('warn', summary, detail, 5000);
  }

  error(detail: string, summary = 'Erro'): void {
    this.push('error', summary, detail, 6500);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private push(severity: ToastSeverity, summary: string, detail: string, life: number): void {
    const toast: Toast = { id: ++this.sequence, severity, summary, detail };
    this.toasts.update((list) => [...list.slice(-4), toast]);
    setTimeout(() => this.dismiss(toast.id), life);
  }
}
