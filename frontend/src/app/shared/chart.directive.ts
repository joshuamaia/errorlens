import { DestroyRef, Directive, ElementRef, effect, inject, input } from '@angular/core';
import { BarChart, LineChart, PieChart, ScatterChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import type { EChartsCoreOption } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

import { ThemeService } from '../core/theme.service';

echarts.use([LineChart, PieChart, BarChart, ScatterChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer]);

/** Cores do tema atual (lidas das variáveis CSS) para montar opções dos gráficos. */
export function chartPalette() {
  const style = getComputedStyle(document.documentElement);
  const v = (name: string) => style.getPropertyValue(name).trim();
  return {
    text: v('--el-text'),
    muted: v('--el-text-muted'),
    border: v('--el-border'),
    surface: v('--el-surface-2'),
    primary: v('--el-primary'),
    success: v('--el-success'),
    danger: v('--el-danger'),
    warning: v('--el-warning'),
    info: v('--el-info'),
    degraded: v('--el-degraded'),
  };
}

/**
 * Wrapper mínimo do ECharts (importação modular): recria o gráfico ao trocar o tema e
 * redimensiona com ResizeObserver.
 */
@Directive({ selector: '[elChart]' })
export class ChartDirective {
  readonly elChart = input.required<(palette: ReturnType<typeof chartPalette>) => EChartsCoreOption>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly theme = inject(ThemeService);
  private chart: echarts.ECharts | null = null;

  constructor() {
    const observer = new ResizeObserver(() => this.chart?.resize());
    observer.observe(this.host.nativeElement);
    effect(() => {
      this.theme.theme();
      const build = this.elChart();
      queueMicrotask(() => this.render(build));
    });
    inject(DestroyRef).onDestroy(() => {
      observer.disconnect();
      this.chart?.dispose();
    });
  }

  private render(build: (palette: ReturnType<typeof chartPalette>) => EChartsCoreOption): void {
    if (!this.chart) {
      this.chart = echarts.init(this.host.nativeElement, undefined, { renderer: 'canvas' });
    }
    this.chart.setOption(build(chartPalette()), true);
  }
}
