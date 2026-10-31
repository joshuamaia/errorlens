import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { ParsedException } from '../core/models';
import { IconComponent } from './icon.component';

const CATEGORY_LABELS: Record<string, string> = {
  APPLICATION: 'Aplicação',
  SPRING: 'Spring',
  HIBERNATE: 'Hibernate',
  JDK: 'JDK',
  JAKARTA: 'Jakarta',
  SERVLET_CONTAINER: 'Tomcat',
  DATABASE_DRIVER: 'Driver JDBC',
  JACKSON: 'Jackson',
  RESILIENCE4J: 'Resilience4j',
  LIBRARY: 'Biblioteca',
};

/**
 * Stack trace estruturado: cadeia de exceptions (Caused by / Suppressed) com frames
 * classificados e frames da aplicação destacados. Frames de framework podem ser recolhidos.
 */
@Component({
  selector: 'el-stack-trace-view',
  imports: [IconComponent],
  template: `
    <div class="row wrap toolbar">
      <label class="row small"><input type="checkbox" [checked]="onlyApp()" (change)="onlyApp.set(!onlyApp())" /> Somente frames da aplicação</label>
      <span class="spacer"></span>
      @for (cat of categories(); track cat) {
        <span class="badge cat-{{ cat }}">{{ label(cat) }}</span>
      }
    </div>
    @for (ex of exceptions(); track ex.index) {
      <section class="exception" [style.margin-left.px]="ex.depth * 20" [class.root]="ex.index === rootIndex()" data-testid="stack-exception">
        <header class="row wrap">
          @switch (ex.relation) {
            @case ('CAUSED_BY') { <span class="badge warning">Caused by</span> }
            @case ('SUPPRESSED') { <span class="badge info">Suppressed</span> }
            @default { <span class="badge danger">Exception</span> }
          }
          @if (ex.index === rootIndex()) {
            <span class="badge danger"><el-icon name="crosshair" [size]="12" /> Root cause</span>
          }
          <strong class="mono">{{ ex.className }}</strong>
        </header>
        @if (ex.message) {
          <pre class="msg">{{ ex.message }}</pre>
        }
        <ol class="frames">
          @for (frame of visibleFrames(ex); track $index) {
            <li class="frame cat-{{ frame.category }}" [class.app]="frame.application">
              <span class="mono cls">{{ frame.className }}.<b>{{ frame.method }}</b></span>
              <span class="mono loc">
                @if (frame.nativeMethod) { (Native Method) } @else { ({{ frame.file ?? 'Unknown Source' }}{{ frame.line ? ':' + frame.line : '' }}) }
              </span>
              <span class="badge cat-{{ frame.category }}">{{ label(frame.category) }}</span>
            </li>
          }
          @if (hiddenCount(ex) > 0) {
            <li class="faint small">… {{ hiddenCount(ex) }} frame(s) de framework ocultos</li>
          }
          @if (ex.omittedFrames > 0) {
            <li class="faint small mono">... {{ ex.omittedFrames }} more (frames comuns omitidos)</li>
          }
        </ol>
      </section>
    }
  `,
  styles: `
    .toolbar { margin-bottom: 10px; gap: 6px; }
    .exception { border: 1px solid var(--el-border); border-radius: 10px; padding: 10px 12px; margin-bottom: 10px; background: var(--el-surface-2); }
    .exception.root { border-color: rgba(239, 68, 68, 0.55); }
    .msg { margin: 8px 0 0; white-space: pre-wrap; font-size: 12.5px; color: var(--el-text); }
    .frames { margin: 8px 0 0; padding-left: 22px; display: flex; flex-direction: column; gap: 3px; }
    .frame { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; color: var(--el-text-muted); font-size: 12px; }
    .frame.app { color: var(--el-text); background: var(--el-primary-soft); border-radius: 4px; padding: 1px 4px; }
    .frame .badge { height: 18px; font-size: 10.5px; }
    .cls { overflow-wrap: anywhere; }
    .loc { color: var(--el-text-faint); }
    .badge.cat-APPLICATION { background: var(--el-primary-soft); color: var(--el-primary); }
    .badge.cat-SPRING { background: var(--el-success-soft); color: var(--el-success); }
    .badge.cat-HIBERNATE { background: var(--el-warning-soft); color: var(--el-warning); }
    .badge.cat-DATABASE_DRIVER { background: var(--el-info-soft); color: var(--el-info); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StackTraceViewComponent {
  readonly exceptions = input.required<ParsedException[]>();
  readonly rootIndex = input<number | null>(null);
  readonly onlyApp = signal(false);
  readonly categories = computed(() => [
    ...new Set(this.exceptions().flatMap((e) => e.frames.map((f) => f.category))),
  ]);

  label(category: string): string {
    return CATEGORY_LABELS[category] ?? category;
  }

  visibleFrames(ex: ParsedException) {
    return this.onlyApp() ? ex.frames.filter((f) => f.application) : ex.frames;
  }

  hiddenCount(ex: ParsedException): number {
    return this.onlyApp() ? ex.frames.length - ex.frames.filter((f) => f.application).length : 0;
  }
}
