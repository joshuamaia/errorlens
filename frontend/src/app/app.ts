import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ThemeService } from './core/theme.service';
import { ConfirmDialogComponent, ToastContainerComponent } from './shared/overlays';

@Component({
  selector: 'el-root',
  imports: [RouterOutlet, ToastContainerComponent, ConfirmDialogComponent],
  template: `
    <router-outlet />
    <el-toasts />
    <el-confirm-dialog />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  // inicializa o tema (efeito que aplica data-theme no documento)
  protected readonly theme = inject(ThemeService);
}
