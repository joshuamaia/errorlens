import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';

import { ConfirmService } from '../core/confirm.service';
import { NotifyService } from '../core/notify.service';
import { IconComponent } from './icon.component';

const TOAST_ICONS = { success: 'circle-check', info: 'info', warn: 'alert', error: 'x-circle' } as const;

/** Pilha de toasts (canto inferior esquerdo). */
@Component({
  selector: 'el-toasts',
  imports: [IconComponent],
  template: `
    <div class="toasts" aria-live="polite">
      @for (t of notify.toasts(); track t.id) {
        <div class="toast {{ t.severity }}" role="status" data-testid="toast">
          <el-icon [name]="icons[t.severity]" [size]="18" />
          <div class="text"><strong>{{ t.summary }}</strong><span>{{ t.detail }}</span></div>
          <button type="button" (click)="notify.dismiss(t.id)" aria-label="Fechar"><el-icon name="x" [size]="14" /></button>
        </div>
      }
    </div>
  `,
  styles: `
    .toasts { position: fixed; left: 20px; bottom: 20px; z-index: 1000; display: flex; flex-direction: column; gap: 8px; max-width: min(420px, calc(100vw - 40px)); }
    .toast { display: flex; gap: 10px; align-items: flex-start; padding: 12px; border-radius: 10px; background: var(--el-surface-2); border: 1px solid var(--el-border-strong); border-left-width: 4px; box-shadow: var(--el-shadow); animation: in .18s ease-out; }
    .toast.success { border-left-color: var(--el-success); el-icon { color: var(--el-success); } }
    .toast.info { border-left-color: var(--el-info); el-icon { color: var(--el-info); } }
    .toast.warn { border-left-color: var(--el-warning); el-icon { color: var(--el-warning); } }
    .toast.error { border-left-color: var(--el-danger); el-icon { color: var(--el-danger); } }
    .text { display: flex; flex-direction: column; gap: 2px; flex: 1; font-size: 13px; overflow-wrap: anywhere; }
    .text span { color: var(--el-text-muted); }
    button { background: none; border: none; color: var(--el-text-muted); cursor: pointer; padding: 0; }
    @keyframes in { from { transform: translateY(8px); opacity: 0; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastContainerComponent {
  protected readonly notify = inject(NotifyService);
  protected readonly icons = TOAST_ICONS;
}

/** Diálogo modal acessível (fecha com Esc ou clique no fundo). */
@Component({
  selector: 'el-dialog',
  imports: [IconComponent],
  host: { '(document:keydown.escape)': 'visible() && closed.emit()' },
  template: `
    @if (visible()) {
      <div class="mask" (click)="closed.emit()">
        <div class="dialog card" role="dialog" aria-modal="true" [attr.aria-label]="header()" [style.width]="width()" (click)="$event.stopPropagation()">
          <header class="row">
            <h3>{{ header() }}</h3>
            <span class="spacer"></span>
            <button class="btn ghost sm icon" type="button" (click)="closed.emit()" aria-label="Fechar"><el-icon name="x" [size]="16" /></button>
          </header>
          <div class="body"><ng-content /></div>
        </div>
      </div>
    }
  `,
  styles: `
    .mask { position: fixed; inset: 0; z-index: 900; background: rgba(3, 6, 15, 0.65); display: grid; place-items: center; padding: 16px; }
    .dialog { max-width: 100%; max-height: calc(100vh - 32px); display: flex; flex-direction: column; gap: 12px; padding: 18px 20px; }
    h3 { font-size: 16px; }
    .body { overflow: auto; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogComponent {
  readonly visible = input(false);
  readonly header = input('');
  readonly width = input('560px');
  readonly closed = output<void>();
}

/** Diálogo de confirmação global. */
@Component({
  selector: 'el-confirm-dialog',
  imports: [DialogComponent],
  template: `
    @if (confirm.request(); as r) {
      <el-dialog [visible]="true" [header]="r.header" width="440px" (closed)="confirm.answer(false)">
        <p class="msg">{{ r.message }}</p>
        <div class="row actions">
          <button class="btn" type="button" (click)="confirm.answer(false)">{{ r.rejectLabel }}</button>
          <button class="btn danger" type="button" (click)="confirm.answer(true)" data-testid="confirm-accept">{{ r.acceptLabel }}</button>
        </div>
      </el-dialog>
    }
  `,
  styles: `.msg { margin: 0 0 16px; line-height: 1.5; } .actions { justify-content: flex-end; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent {
  protected readonly confirm = inject(ConfirmService);
}
