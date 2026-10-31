import { DOCUMENT, effect, inject, Injectable, signal } from '@angular/core';

export type Theme = 'dark' | 'light';
const KEY = 'errorlens.theme';

/** Tema escuro (padrão) e claro. Sincroniza o atributo data-theme e a classe do PrimeNG. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  readonly theme = signal<Theme>(ThemeService.initial());

  constructor() {
    effect(() => {
      const theme = this.theme();
      const root = this.document.documentElement;
      root.setAttribute('data-theme', theme);
      root.classList.toggle('el-dark', theme === 'dark');
      try {
        localStorage.setItem(KEY, theme);
      } catch {
        // preferência não persistida
      }
    });
  }

  toggle(): void {
    this.theme.update((t) => (t === 'dark' ? 'light' : 'dark'));
  }

  private static initial(): Theme {
    try {
      return localStorage.getItem(KEY) === 'light' ? 'light' : 'dark';
    } catch {
      return 'dark';
    }
  }
}
