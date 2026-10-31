import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { formatDateTime, formatDuration, formatMicros, simpleName, stepTone } from '../../core/format';
import { Comparison, ExecutionSummary } from '../../core/models';
import { OutcomeBadgeComponent, StatusBadgeComponent } from '../../shared/badges';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { IconComponent } from '../../shared/icon.component';

type Section = 'flow' | 'request' | 'response' | 'exceptions' | 'logs';

@Component({
  selector: 'el-comparator-page',
  imports: [FormsModule, RouterLink, IconComponent, StatusBadgeComponent, OutcomeBadgeComponent, EmptyStateComponent],
  templateUrl: './comparator.page.html',
  styleUrl: './comparator.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparatorPage {
  readonly left = input<string>();
  readonly right = input<string>();

  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly recent = signal<ExecutionSummary[]>([]);
  protected readonly comparison = signal<Comparison | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly section = signal<Section>('flow');
  protected readonly onlyDifferences = signal(false);
  protected leftId: number | null = null;
  protected rightId: number | null = null;

  protected readonly steps = computed(() => {
    const c = this.comparison();
    if (!c) return [];
    return c.steps
      .map((row, index) => ({ ...row, index }))
      .filter((row) => !this.onlyDifferences() || row.relation !== 'SAME');
  });
  protected readonly fieldDiffCount = computed(() => this.comparison()?.fields.filter((f) => f.different).length ?? 0);

  protected readonly fmtDuration = formatDuration;
  protected readonly fmtMicros = formatMicros;
  protected readonly fmtDate = formatDateTime;
  protected readonly simple = simpleName;
  protected readonly tone = stepTone;

  constructor() {
    this.api.executions({ size: 50 }).subscribe({ next: (page) => this.recent.set(page.content), error: () => undefined });
    effect(() => {
      const left = Number(this.left());
      const right = Number(this.right());
      untracked(() => {
        if (left && right) {
          this.leftId = left;
          this.rightId = right;
          this.load(left, right);
        }
      });
    });
  }

  protected submit(): void {
    if (this.leftId && this.rightId) {
      this.router.navigate([], { queryParams: { left: this.leftId, right: this.rightId } });
    }
  }

  protected swap(): void {
    [this.leftId, this.rightId] = [this.rightId, this.leftId];
    this.submit();
  }

  protected relationLabel(relation: string): string {
    return { SAME: 'igual', STATUS_DIFFERENT: 'status diferente', LEFT_ONLY: 'somente A', RIGHT_ONLY: 'somente B' }[relation] ?? relation;
  }

  private load(left: number, right: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.compare(left, right).subscribe({
      next: (comparison) => {
        this.comparison.set(comparison);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.comparison.set(null);
        this.error.set('Não foi possível comparar as execuções selecionadas.');
      },
    });
  }
}
