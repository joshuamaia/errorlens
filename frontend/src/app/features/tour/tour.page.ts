import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { AuthService } from '../../core/auth.service';
import { TourService } from '../../core/tour.service';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'el-tour-page',
  imports: [IconComponent],
  template: `
    <div class="page" data-testid="tour-page">
      <div class="page-header">
        <div class="page-icon"><el-icon name="map" [size]="22" /></div>
        <div>
          <h1>Guided Tour</h1>
          <p>Uma demonstração completa usando as funcionalidades reais do ErrorLens</p>
        </div>
        <span class="spacer"></span>
        @if (tour.active()) {
          <button class="btn" type="button" (click)="tour.close()"><el-icon name="x" [size]="15" /> Encerrar tour</button>
        }
        <button class="btn primary" type="button" (click)="tour.start()" [disabled]="!auth.isAnalyst()" data-testid="tour-start">
          <el-icon name="rocket" [size]="15" /> {{ tour.active() ? 'Reiniciar tour' : 'Iniciar tour' }}
        </button>
      </div>

      @if (!auth.isAnalyst()) {
        <div class="alert info small"><el-icon name="lock" [size]="16" /> O tour executa cenários reais e exige ROLE_ANALYST ou ROLE_ADMIN.</div>
      }

      <ol class="steps">
        @for (step of tour.steps; track $index; let i = $index) {
          <li class="card" [class.current]="tour.active() && tour.stepIndex() === i" [class.done]="tour.active() && tour.stepIndex() > i">
            <span class="num">{{ i + 1 }}</span>
            <el-icon [name]="step.icon" [size]="20" />
            <div>
              <strong>{{ step.title }}</strong>
              <p class="small muted">{{ step.description }}</p>
            </div>
          </li>
        }
      </ol>
      <p class="small muted">Durante o tour, um painel fixo no canto inferior direito conduz cada passo. Os resultados exibidos vêm do backend, do PostgreSQL e da instrumentação real.</p>
    </div>
  `,
  styles: `
    .steps { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
    .steps li { display: flex; gap: 14px; align-items: center; }
    .steps li.current { border-color: var(--el-primary); box-shadow: 0 0 0 3px var(--el-primary-soft); }
    .steps li.done { opacity: 0.7; }
    .steps li.done .num { background: var(--el-success); }
    .num { width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center; background: var(--el-primary); color: #fff; font-weight: 700; flex-shrink: 0; }
    p { margin: 4px 0 0; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TourPage {
  protected readonly tour = inject(TourService);
  protected readonly auth = inject(AuthService);
}
