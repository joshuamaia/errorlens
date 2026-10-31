import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { IconComponent } from './icon.component';

@Component({
  selector: 'el-empty-state',
  imports: [IconComponent],
  template: `
    <div class="empty-state" [attr.data-testid]="testId()">
      <el-icon class="empty-icon" [name]="icon()" [size]="36" [strokeWidth]="1.5" />
      <h4>{{ title() }}</h4>
      @if (message()) {
        <p class="small">{{ message() }}</p>
      }
      <ng-content />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  readonly icon = input('inbox');
  readonly title = input.required<string>();
  readonly message = input<string>('');
  readonly testId = input<string>('empty-state');
}
