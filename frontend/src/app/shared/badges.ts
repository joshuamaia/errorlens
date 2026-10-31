import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { httpReason, outcomeLabel, outcomeTone, statusTone } from '../core/format';

@Component({
  selector: 'el-status-badge',
  template: `<span class="badge {{ tone() }}" [attr.title]="reason()">{{ label() }}</span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusBadgeComponent {
  readonly status = input<number | null>(null);
  readonly showReason = input(true);
  readonly tone = computed(() => statusTone(this.status()));
  readonly reason = computed(() => httpReason(this.status()));
  readonly label = computed(() => {
    const status = this.status();
    if (!status) return 'sem resposta';
    return this.showReason() && this.reason() ? `${status} ${this.reason()}` : String(status);
  });
}

@Component({
  selector: 'el-outcome-badge',
  template: `<span class="badge {{ tone() }}">{{ label() }}</span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OutcomeBadgeComponent {
  readonly outcome = input<string | null>(null);
  readonly tone = computed(() => outcomeTone(this.outcome()));
  readonly label = computed(() => outcomeLabel(this.outcome()));
}

@Component({
  selector: 'el-method-badge',
  template: `<span class="badge mono" [class]="'badge ' + tone()">{{ method() }}</span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MethodBadgeComponent {
  readonly method = input<string>('GET');
  readonly tone = computed(() => {
    switch (this.method()) {
      case 'GET':
        return 'info';
      case 'POST':
        return 'success';
      case 'PUT':
      case 'PATCH':
        return 'warning';
      case 'DELETE':
        return 'danger';
      default:
        return '';
    }
  });
}
