import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { formatMicros, simpleName, stepTone } from '../core/format';
import { Stage, Step, Timeline } from '../core/models';
import { IconComponent } from './icon.component';

const STAGE_ICONS: Record<string, string> = {
  ORIGIN: 'globe',
  RUNNER: 'play',
  HTTP: 'arrow-right',
  SECURITY: 'shield',
  CONTROLLER: 'route',
  SERVICE: 'cpu',
  REPOSITORY: 'boxes',
  DATABASE: 'database',
  EXTERNAL: 'network',
  RESILIENCE: 'zap',
  EXCEPTION_HANDLER: 'alert',
  RESPONSE: 'arrow-left-right',
  VERIFICATION: 'list-checks',
};

/**
 * Timeline real de uma execução: diagrama de fluxo das camadas canônicas (com estado de
 * instrumentação) e cascata de etapas com início/duração medidos.
 */
@Component({
  selector: 'el-timeline-view',
  imports: [IconComponent],
  template: `
    <div class="flow" data-testid="timeline-flow">
      @for (stage of timeline().stages; track stage.key; let last = $last) {
        <div class="stage {{ stageTone(stage) }}" [class.unreached]="!stage.reached" [title]="stage.note ?? ''">
          <el-icon [name]="icon(stage.key)" [size]="16" />
          <div class="stage-text">
            <strong>{{ stage.label }}</strong>
            <span class="small muted">
              @if (!stage.instrumented) {
                sem instrumentação
              } @else if (!stage.reached) {
                não registrada
              } @else {
                {{ stage.stepCount }} evento(s) · {{ fmt(stage.durationMicros) }}
              }
            </span>
          </div>
        </div>
        @if (!last) {
          <el-icon class="arrow" name="chevron-right" [size]="14" />
        }
      }
    </div>

    <div class="row wrap legend small muted">
      <span><span class="dot success"></span> OK</span>
      <span><span class="dot warning"></span> Atenção</span>
      <span><span class="dot danger"></span> Erro</span>
      <span class="spacer"></span>
      <label class="row"><input type="checkbox" [checked]="showDetails()" (change)="showDetails.set(!showDetails())" /> detalhes</label>
    </div>

    <div class="waterfall" data-testid="timeline-steps">
      @for (step of timeline().steps; track step.seq) {
        <div class="step {{ tone(step.status) }}" [style.padding-left.px]="8 + step.depth * 18">
          <div class="step-head">
            <span class="badge {{ tone(step.status) }}">{{ step.status }}</span>
            <span class="layer small muted">{{ step.layerLabel }}</span>
            <strong class="name ellipsis" [title]="step.name">{{ step.name }}</strong>
            <span class="spacer"></span>
            <span class="small muted mono">+{{ ms(step.offsetMicros) }}</span>
            <span class="dur small mono">{{ fmt(step.durationMicros) }}</span>
          </div>
          <div class="bar-track">
            <span
              class="bar {{ tone(step.status) }}"
              [class.point]="step.durationMicros === null"
              [style.left.%]="left(step)"
              [style.width.%]="width(step)"
            ></span>
          </div>
          @if (showDetails() && (step.detail || step.errorClass)) {
            <div class="step-detail small">
              @if (step.detail) {
                <div class="mono muted">{{ step.detail }}</div>
              }
              @if (step.errorClass) {
                <div class="text-danger mono err" [title]="step.errorMessage ?? ''">{{ simple(step.errorClass) }}: {{ step.errorMessage }}</div>
              }
            </div>
          }
        </div>
      }
    </div>
    <p class="small faint">{{ timeline().note }}</p>
  `,
  styles: `
    .flow { display: flex; align-items: stretch; gap: 6px; overflow-x: auto; padding-bottom: 6px; }
    .stage { display: flex; gap: 8px; align-items: center; padding: 8px 10px; border-radius: 10px; border: 1px solid var(--el-border-strong); background: var(--el-surface-2); min-width: 128px; flex-shrink: 0; }
    .stage.success { border-color: rgba(34,197,94,.5); }
    .stage.danger { border-color: rgba(239,68,68,.6); background: var(--el-danger-soft); }
    .stage.warning { border-color: rgba(245,176,38,.5); }
    .stage.degraded { border-color: rgba(249,115,22,.5); }
    .stage.unreached { opacity: .45; border-style: dashed; }
    .stage-text { display: flex; flex-direction: column; font-size: 12px; white-space: nowrap; }
    .arrow { color: var(--el-text-faint); align-self: center; }
    .legend { margin: 12px 0 8px; gap: 14px; }
    .waterfall { display: flex; flex-direction: column; gap: 2px; }
    .step { border-left: 2px solid var(--el-border); padding: 6px 8px; border-radius: 4px; }
    .step:hover { background: var(--el-surface-2); }
    .step.danger { border-left-color: var(--el-danger); }
    .step.warning { border-left-color: var(--el-warning); }
    .step.success { border-left-color: var(--el-success); }
    .step-head { display: flex; align-items: center; gap: 8px; min-width: 0; }
    .layer { width: 110px; flex-shrink: 0; }
    .name { font-weight: 500; font-size: 13px; min-width: 0; }
    .dur { width: 84px; text-align: right; }
    .bar-track { position: relative; height: 6px; margin-top: 5px; background: var(--el-surface-3); border-radius: 3px; }
    .bar { position: absolute; top: 0; height: 100%; min-width: 2px; border-radius: 3px; background: var(--el-info); }
    .bar.success { background: var(--el-success); }
    .bar.danger { background: var(--el-danger); }
    .bar.warning { background: var(--el-warning); }
    .bar.degraded { background: var(--el-degraded); }
    .bar.point { width: 6px !important; height: 6px; border-radius: 50%; }
    .err { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .step-detail { margin-top: 6px; display: flex; flex-direction: column; gap: 3px; overflow-wrap: anywhere; }
    @media (max-width: 760px) { .layer { display: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TimelineViewComponent {
  readonly timeline = input.required<Timeline>();
  readonly showDetails = signal(true);

  private readonly totalMicros = computed(() => {
    const steps = this.timeline().steps;
    return Math.max(1, ...steps.map((s) => s.offsetMicros + (s.durationMicros ?? 0)));
  });

  icon(key: string): string {
    return STAGE_ICONS[key] ?? 'circle-dot';
  }

  stageTone(stage: Stage): string {
    if (!stage.reached) return '';
    return stepTone(stage.status);
  }

  tone(status: string): string {
    return stepTone(status);
  }

  fmt(us: number | null): string {
    return formatMicros(us);
  }

  ms(us: number): string {
    return (us / 1000).toFixed(1) + ' ms';
  }

  simple(name: string): string {
    return simpleName(name);
  }

  left(step: Step): number {
    return Math.min(99, (step.offsetMicros / this.totalMicros()) * 100);
  }

  width(step: Step): number {
    if (step.durationMicros === null) return 0;
    return Math.max(0.4, Math.min(100 - this.left(step), (step.durationMicros / this.totalMicros()) * 100));
  }
}
