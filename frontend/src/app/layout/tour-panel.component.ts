import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { TourService } from '../core/tour.service';
import { IconComponent } from '../shared/icon.component';

/** Painel flutuante do Guided Tour, visível em todas as telas enquanto o tour está ativo. */
@Component({
  selector: 'el-tour-panel',
  imports: [IconComponent],
  template: `
    <aside class="tour card" data-testid="tour-panel">
      <header class="row">
        <el-icon name="map" class="text-info" />
        <strong>Guided Tour</strong>
        <span class="small muted">passo {{ tour.stepIndex() + 1 }} de {{ tour.steps.length }}</span>
        <span class="spacer"></span>
        <button class="btn ghost sm icon" type="button" (click)="tour.close()" aria-label="Fechar tour"><el-icon name="x" [size]="15" /></button>
      </header>
      <div class="progress">
        @for (s of tour.steps; track $index) {
          <span [class.done]="$index < tour.stepIndex()" [class.current]="$index === tour.stepIndex()"></span>
        }
      </div>
      <h4><el-icon [name]="tour.current().icon" [size]="16" /> {{ tour.current().title }}</h4>
      <p class="small muted">{{ tour.current().description }}</p>
      <footer class="row">
        <button class="btn sm" type="button" [disabled]="tour.stepIndex() === 0 || tour.busy()" (click)="tour.go(tour.stepIndex() - 1)">
          <el-icon name="chevron-left" [size]="14" /> Anterior
        </button>
        <span class="spacer"></span>
        <button class="btn primary sm" type="button" [disabled]="!tour.canRun(tour.stepIndex()) || tour.busy()" (click)="tour.run()" data-testid="tour-action">
          @if (tour.busy()) {
            <el-icon name="loader" class="spin" [size]="14" />
          } @else {
            <el-icon name="play" [size]="14" />
          }
          {{ tour.current().action }}
        </button>
      </footer>
    </aside>
  `,
  styles: `
    .tour { position: fixed; right: 20px; bottom: 20px; width: 360px; max-width: calc(100vw - 32px); z-index: 50; display: flex; flex-direction: column; gap: 10px; border-color: rgba(124, 92, 255, 0.55); }
    h4 { display: flex; align-items: center; gap: 6px; font-size: 14px; }
    p { margin: 0; line-height: 1.5; }
    .progress { display: flex; gap: 4px; }
    .progress span { flex: 1; height: 4px; border-radius: 2px; background: var(--el-surface-3); }
    .progress .done { background: var(--el-success); }
    .progress .current { background: var(--el-primary); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TourPanelComponent {
  protected readonly tour = inject(TourService);
}
