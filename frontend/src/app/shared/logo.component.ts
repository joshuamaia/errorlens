import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Logotipo ErrorLens: lente de investigação com um sinal de pulso (rastreamento/diagnóstico). */
@Component({
  selector: 'el-logo',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 48 48" aria-label="ErrorLens" role="img">
      <defs>
        <linearGradient id="elLens" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#9b82ff" />
          <stop offset="1" stop-color="#5b3df5" />
        </linearGradient>
      </defs>
      <circle cx="21" cy="21" r="15" fill="rgba(124,92,255,0.12)" stroke="url(#elLens)" stroke-width="3.5" />
      <path d="M9.5 22h5l2.5-6 4 12 3-9 2 3h6" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M32.5 32.5 42 42" stroke="url(#elLens)" stroke-width="5" stroke-linecap="round" />
    </svg>
  `,
  styles: ":host { display: inline-flex; color: var(--el-text); }",
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogoComponent {
  readonly size = input(36);
}
