import { ChangeDetectionStrategy, Component, computed, input, linkedSignal } from '@angular/core';

import { Diagnosis } from '../core/models';
import { IconComponent } from './icon.component';

@Component({
  selector: 'el-diagnosis-card',
  imports: [IconComponent],
  template: `
    <article class="dx card flat" [class.top]="index() === 0" data-testid="diagnosis-card">
      <header class="row wrap">
        <el-icon name="lightbulb" class="text-warning" />
        <h3>{{ diagnosis().title }}</h3>
        <span class="badge">{{ diagnosis().category }}</span>
        <span class="spacer"></span>
        <span class="badge {{ tone() }}" [title]="'Pontuação de evidências: ' + diagnosis().score + '/100'">
          Confiança {{ diagnosis().confidence }} · {{ diagnosis().score }}
        </span>
      </header>
      <div class="meter"><span [style.width.%]="diagnosis().score" [class]="tone()"></span></div>
      <p class="desc">{{ diagnosis().description }}</p>

      <div class="grid grid-2">
        <section>
          <h4><el-icon name="crosshair" [size]="14" /> Evidências encontradas</h4>
          <ul>
            @for (item of diagnosis().evidences; track $index) {
              <li>{{ item }}</li>
            } @empty {
              <li class="muted">Sem evidências específicas.</li>
            }
          </ul>
        </section>
        <section>
          <h4><el-icon name="help" [size]="14" /> Possíveis causas (hipóteses)</h4>
          <ul>
            @for (item of diagnosis().possibleCauses; track $index) {
              <li>{{ item }}</li>
            }
          </ul>
        </section>
      </div>

      @if (expanded()) {
        <div class="grid grid-2">
          <section>
            <h4><el-icon name="search" [size]="14" /> Sugestões de investigação</h4>
            <ul>
              @for (item of diagnosis().suggestions; track $index) {
                <li>{{ item }}</li>
              }
            </ul>
          </section>
          <section>
            <h4><el-icon name="wrench" [size]="14" /> Possíveis correções</h4>
            <ul>
              @for (item of diagnosis().fixes; track $index) {
                <li>{{ item }}</li>
              }
            </ul>
          </section>
        </div>
        @if (diagnosis().codeExample) {
          <h4><el-icon name="code" [size]="14" /> Exemplo</h4>
          <pre class="code-block">{{ diagnosis().codeExample }}</pre>
        }
      }
      <button class="btn ghost sm" type="button" (click)="expanded.set(!expanded())">
        <el-icon [name]="expanded() ? 'chevron-down' : 'chevron-right'" [size]="14" />
        {{ expanded() ? 'Ocultar sugestões e correções' : 'Ver sugestões, correções e exemplo' }}
      </button>
    </article>
  `,
  styles: `
    .dx { display: flex; flex-direction: column; gap: 10px; background: var(--el-surface-2); }
    .dx.top { border-color: rgba(124, 92, 255, 0.45); }
    h3 { font-size: 15px; }
    h4 { font-size: 12.5px; color: var(--el-text-muted); display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
    ul { margin: 0; padding-left: 18px; display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
    .desc { margin: 0; color: var(--el-text-muted); line-height: 1.5; }
    .meter { height: 4px; background: var(--el-surface-3); border-radius: 4px; overflow: hidden; }
    .meter span { display: block; height: 100%; background: var(--el-info); }
    .meter .success { background: var(--el-success); }
    .meter .warning { background: var(--el-warning); }
    .btn { align-self: flex-start; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DiagnosisCardComponent {
  readonly diagnosis = input.required<Diagnosis>();
  readonly index = input(0);
  /** O diagnóstico mais provável começa expandido. */
  readonly expanded = linkedSignal(() => this.index() === 0);
  readonly tone = computed(() => {
    switch (this.diagnosis().confidence) {
      case 'ALTA':
        return 'success';
      case 'MEDIA':
        return 'warning';
      default:
        return 'info';
    }
  });
}
