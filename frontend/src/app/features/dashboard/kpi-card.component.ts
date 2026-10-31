import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';

import { ChartDirective, chartPalette } from '../../shared/chart.directive';
import { IconComponent } from '../../shared/icon.component';

type Tone = 'primary' | 'success' | 'danger' | 'warning' | 'info';

/** Card de indicador com tendência vs. período anterior e sparkline. */
@Component({
  selector: 'el-kpi-card',
  imports: [IconComponent, ChartDirective],
  template: `
    <div class="kpi card" [attr.data-testid]="testId()">
      <div class="icon {{ tone() }}"><el-icon [name]="icon()" [size]="22" /></div>
      <div class="body">
        <span class="label">{{ label() }}</span>
        <div class="row">
          <strong class="value">{{ value() }}</strong>
          @if (trendLabel()) {
            <span class="trend small" [class.good]="trendGood()" [class.bad]="!trendGood()">{{ trendLabel() }}</span>
          }
        </div>
      </div>
      <div class="spark" [elChart]="spark()"></div>
    </div>
  `,
  styles: `
    .kpi { display: grid; grid-template-columns: auto 1fr; grid-template-rows: auto auto; gap: 4px 12px; padding: 14px; }
    .icon { grid-row: span 2; width: 44px; height: 44px; border-radius: 10px; display: grid; place-items: center; }
    .icon.primary { background: var(--el-primary-soft); color: var(--el-primary); }
    .icon.success { background: var(--el-success-soft); color: var(--el-success); }
    .icon.danger { background: var(--el-danger-soft); color: var(--el-danger); }
    .icon.warning { background: var(--el-warning-soft); color: var(--el-warning); }
    .icon.info { background: var(--el-info-soft); color: var(--el-info); }
    .label { font-size: 12px; color: var(--el-text-muted); }
    .value { font-size: 24px; letter-spacing: -0.02em; }
    .trend { font-weight: 600; }
    .good { color: var(--el-success); }
    .bad { color: var(--el-danger); }
    .spark { grid-column: 2; height: 28px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KpiCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly icon = input.required<string>();
  readonly tone = input<Tone>('primary');
  readonly trend = input<number | null>(null);
  readonly trendUnit = input<'%' | 'p.p.'>('%');
  /** Para métricas em que aumento é ruim (falhas, taxa de erro, latência). */
  readonly higherIsBad = input(false);
  readonly series = input<number[]>([]);
  readonly testId = input('kpi');

  readonly trendLabel = computed(() => {
    const t = this.trend();
    if (t === null || t === undefined) return '';
    const arrow = this.trendUnit() === 'p.p.' ? (t >= 0 ? '↑ ' : '↓ ') : t >= 0 ? '+' : '';
    return `${arrow}${t.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}${this.trendUnit() === '%' ? '%' : ' p.p.'}`;
  });
  readonly trendGood = computed(() => {
    const t = this.trend() ?? 0;
    return this.higherIsBad() ? t <= 0 : t >= 0;
  });

  readonly spark = computed(() => {
    const data = this.series();
    const tone = this.tone();
    return (p: ReturnType<typeof chartPalette>): EChartsCoreOption => ({
      grid: { left: 0, right: 0, top: 2, bottom: 2 },
      xAxis: { type: 'category', show: false, data: data.map((_, i) => i) },
      yAxis: { type: 'value', show: false, min: 'dataMin' },
      series: [
        {
          type: 'line',
          data,
          smooth: true,
          symbol: 'none',
          lineStyle: { width: 1.6, color: p[tone] },
          areaStyle: { opacity: 0.12, color: p[tone] },
        },
      ],
      animation: false,
    });
  });
}
