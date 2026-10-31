import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import type { EChartsCoreOption } from 'echarts/core';
import { forkJoin } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { formatDateTime, formatDuration, formatNumber, simpleName, statusTone } from '../../core/format';
import { DashboardMetrics, DashboardSummary, HealthReport } from '../../core/models';
import { TourService } from '../../core/tour.service';
import { StatusBadgeComponent } from '../../shared/badges';
import { ChartDirective, chartPalette } from '../../shared/chart.directive';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { IconComponent } from '../../shared/icon.component';
import { KpiCardComponent } from './kpi-card.component';

const STATUS_COLORS: Record<string, string> = { success: 'success', info: 'info', warning: 'warning', danger: 'danger' };

@Component({
  selector: 'el-dashboard-page',
  imports: [FormsModule, RouterLink, IconComponent, KpiCardComponent, ChartDirective, StatusBadgeComponent, EmptyStateComponent],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly tour = inject(TourService);

  protected readonly range = signal('24h');
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly summary = signal<DashboardSummary | null>(null);
  protected readonly metrics = signal<DashboardMetrics | null>(null);
  protected readonly health = signal<HealthReport | null>(null);

  protected readonly ranges = [
    { value: '1h', label: 'Última hora' },
    { value: '24h', label: 'Últimas 24 horas' },
    { value: '7d', label: 'Últimos 7 dias' },
    { value: '30d', label: 'Últimos 30 dias' },
  ];

  protected readonly fmtDuration = formatDuration;
  protected readonly fmtNumber = formatNumber;
  protected readonly fmtDate = formatDateTime;
  protected readonly simple = simpleName;

  protected readonly maxException = computed(() => Math.max(1, ...(this.metrics()?.topExceptions ?? []).map((e) => e.count)));

  protected readonly evolutionChart = computed(() => {
    const series = this.metrics()?.timeseries ?? [];
    const range = this.range();
    const label = (iso: string) => {
      const d = new Date(iso);
      return range === '7d' || range === '30d'
        ? d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + (range === '7d' ? ' ' + d.getHours() + 'h' : '')
        : d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    };
    return (p: ReturnType<typeof chartPalette>): EChartsCoreOption => ({
      tooltip: { trigger: 'axis', backgroundColor: p.surface, borderColor: p.border, textStyle: { color: p.text } },
      legend: { top: 0, right: 0, textStyle: { color: p.muted }, icon: 'circle', itemWidth: 8 },
      grid: { left: 36, right: 12, top: 32, bottom: 24 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: series.map((b) => label(b.start)),
        axisLine: { lineStyle: { color: p.border } },
        axisLabel: { color: p.muted },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        splitLine: { lineStyle: { color: p.border, type: 'dashed' } },
        axisLabel: { color: p.muted },
      },
      series: [
        this.area('Sucesso', series.map((b) => b.success), p.success),
        this.area('Falha', series.map((b) => b.failure), p.danger),
        this.area('Degradada', series.map((b) => b.degraded), p.degraded),
      ],
    });
  });

  protected readonly statusChart = computed(() => {
    const data = this.metrics()?.statusDistribution ?? [];
    const total = data.reduce((sum, s) => sum + s.count, 0);
    return (p: ReturnType<typeof chartPalette>): EChartsCoreOption => ({
      tooltip: { trigger: 'item', backgroundColor: p.surface, borderColor: p.border, textStyle: { color: p.text } },
      series: [
        {
          type: 'pie',
          radius: ['62%', '86%'],
          padAngle: 2,
          itemStyle: { borderRadius: 4 },
          label: {
            show: true,
            position: 'center',
            formatter: () => `{a|${total}}\n{b|Execuções}`,
            rich: { a: { fontSize: 26, fontWeight: 700, color: p.text }, b: { fontSize: 12, color: p.muted } },
          },
          data: data.map((s) => ({
            name: s.status ? String(s.status) : 'sem resposta',
            value: s.count,
            itemStyle: { color: this.statusColor(s.status, p) },
          })),
        },
      ],
    });
  });

  protected readonly durationChart = computed(() => {
    const bins = this.metrics()?.durationHistogram ?? [];
    return (p: ReturnType<typeof chartPalette>): EChartsCoreOption => ({
      tooltip: { trigger: 'axis', backgroundColor: p.surface, borderColor: p.border, textStyle: { color: p.text } },
      grid: { left: 32, right: 8, top: 10, bottom: 24 },
      xAxis: { type: 'category', data: bins.map((b) => b.label), axisLabel: { color: p.muted, fontSize: 10 }, axisLine: { lineStyle: { color: p.border } } },
      yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: p.border, type: 'dashed' } }, axisLabel: { color: p.muted } },
      series: [{ type: 'bar', data: bins.map((b) => b.count), barWidth: '55%', itemStyle: { color: p.primary, borderRadius: [4, 4, 0, 0] } }],
    });
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.failed.set(false);
    forkJoin({
      summary: this.api.dashboardSummary(this.range()),
      metrics: this.api.dashboardMetrics(this.range()),
    }).subscribe({
      next: ({ summary, metrics }) => {
        this.summary.set(summary);
        this.metrics.set(metrics);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
    this.api.health().subscribe({ next: (h) => this.health.set(h), error: () => this.health.set(null) });
  }

  protected changeRange(value: string): void {
    this.range.set(value);
    this.load();
  }

  protected open(id: number): void {
    if (this.auth.isAnalyst()) this.router.navigate(['/investigation', id]);
  }

  protected exceptionColor(index: number): string {
    return ['var(--el-danger)', 'var(--el-degraded)', 'var(--el-warning)', 'var(--el-info)', 'var(--el-primary)'][index % 5];
  }

  protected serviceIcon(key: string): string {
    return { backend: 'code', postgres: 'database', 'dependency-simulator': 'server', 'otel-collector': 'network', jaeger: 'telescope' }[key] ?? 'server';
  }

  protected statusLegendColor(status: number): string {
    return `var(--el-${STATUS_COLORS[statusTone(status)] ?? 'text-faint'})`;
  }

  private statusColor(status: number, p: ReturnType<typeof chartPalette>): string {
    if (!status) return p.muted;
    if (status === 201) return p.info;
    if (status === 204) return p.primary;
    if (status < 300) return p.success;
    if (status === 409) return p.degraded;
    if (status < 500) return p.warning;
    return p.danger;
  }

  private area(name: string, data: number[], color: string) {
    return {
      name,
      type: 'line',
      data,
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: { width: 2, color },
      itemStyle: { color },
      areaStyle: { opacity: 0.18, color },
    };
  }
}
