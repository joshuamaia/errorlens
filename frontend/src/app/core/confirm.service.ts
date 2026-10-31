import { Injectable, signal } from '@angular/core';

export interface ConfirmRequest {
  header: string;
  message: string;
  acceptLabel?: string;
  rejectLabel?: string;
  resolve: (accepted: boolean) => void;
}

/** Diálogo de confirmação global (renderizado pelo ConfirmDialogComponent). */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly request = signal<ConfirmRequest | null>(null);

  ask(header: string, message: string, acceptLabel = 'Confirmar', rejectLabel = 'Cancelar'): Promise<boolean> {
    return new Promise((resolve) => this.request.set({ header, message, acceptLabel, rejectLabel, resolve }));
  }

  answer(accepted: boolean): void {
    const current = this.request();
    this.request.set(null);
    current?.resolve(accepted);
  }
}
