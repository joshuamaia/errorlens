import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

import { ThemeService } from '../core/theme.service';

interface MonacoEditor {
  getValue(): string;
  setValue(value: string): void;
  dispose(): void;
  layout(): void;
  onDidChangeModelContent(listener: () => void): void;
  updateOptions(options: Record<string, unknown>): void;
}

interface MonacoApi {
  editor: {
    create(element: HTMLElement, options: Record<string, unknown>): MonacoEditor;
    setTheme(theme: string): void;
    setModelLanguage(model: unknown, language: string): void;
  };
}

type AmdRequire = ((deps: string[], cb: (...args: unknown[]) => void, err?: (e: unknown) => void) => void) & {
  config(options: Record<string, unknown>): void;
};

let monacoPromise: Promise<MonacoApi> | null = null;

/** Carrega o build AMD do Monaco servido em /assets/monaco (sem CDN, funciona offline). */
function loadMonaco(): Promise<MonacoApi> {
  if (monacoPromise) return monacoPromise;
  monacoPromise = new Promise<MonacoApi>((resolve, reject) => {
    const win = window as unknown as { require?: AmdRequire; monaco?: MonacoApi; MonacoEnvironment?: unknown };
    if (win.monaco) {
      resolve(win.monaco);
      return;
    }
    const script = document.createElement('script');
    script.src = 'assets/monaco/vs/loader.js';
    script.onload = () => {
      const amdRequire = win.require as AmdRequire;
      amdRequire.config({ paths: { vs: 'assets/monaco/vs' } });
      amdRequire(['vs/editor/editor.main'], () => resolve(win.monaco as MonacoApi), reject);
    };
    script.onerror = reject;
    document.body.appendChild(script);
  });
  monacoPromise.catch(() => (monacoPromise = null));
  return monacoPromise;
}

/**
 * Visualizador/editor de código (JSON, logs, stack traces) baseado no Monaco Editor.
 * Enquanto o Monaco carrega, ou se falhar, exibe um fallback textual equivalente.
 */
@Component({
  selector: 'el-code-viewer',
  template: `
    <div class="code-host" [style.height]="height()">
      <div #editorHost class="editor" [class.hidden]="!ready()"></div>
      @if (!ready()) {
        @if (readOnly()) {
          <pre class="code-block fallback" data-testid="code-fallback">{{ value() }}</pre>
        } @else {
          <textarea
            class="input fallback"
            [value]="value()"
            (input)="valueChange.emit($any($event.target).value)"
            [attr.placeholder]="placeholder()"
          ></textarea>
        }
      }
    </div>
  `,
  styles: `
    .code-host { position: relative; border: 1px solid var(--el-border); border-radius: var(--el-radius-sm); overflow: hidden; background: var(--el-bg-elevated); }
    .editor { position: absolute; inset: 0; }
    .hidden { visibility: hidden; }
    .fallback { position: absolute; inset: 0; height: 100%; max-height: none; border: none; border-radius: 0; resize: none; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CodeViewerComponent {
  readonly value = input<string | null>('');
  readonly language = input<string>('plaintext');
  readonly readOnly = input<boolean>(true);
  readonly height = input<string>('320px');
  readonly placeholder = input<string>('');
  readonly valueChange = output<string>();

  protected readonly ready = signal(false);
  private readonly editorHost = viewChild.required<ElementRef<HTMLElement>>('editorHost');
  private readonly theme = inject(ThemeService);
  private editor: MonacoEditor | null = null;
  private monaco: MonacoApi | null = null;
  private applying = false;

  constructor() {
    loadMonaco()
      .then((monaco) => {
        this.monaco = monaco;
        this.editor = monaco.editor.create(this.editorHost().nativeElement, {
          value: this.value() ?? '',
          language: this.language(),
          readOnly: this.readOnly(),
          automaticLayout: true,
          minimap: { enabled: false },
          fontSize: 12.5,
          fontFamily: "'JetBrains Mono', Consolas, monospace",
          scrollBeyondLastLine: false,
          wordWrap: 'on',
          folding: true,
          renderLineHighlight: 'none',
          theme: this.theme.theme() === 'dark' ? 'vs-dark' : 'vs',
        });
        this.editor.onDidChangeModelContent(() => {
          if (!this.applying) this.valueChange.emit(this.editor!.getValue());
        });
        this.ready.set(true);
      })
      .catch(() => this.ready.set(false));

    effect(() => {
      const value = this.value() ?? '';
      if (this.editor && this.editor.getValue() !== value) {
        this.applying = true;
        this.editor.setValue(value);
        this.applying = false;
      }
    });
    effect(() => {
      const dark = this.theme.theme() === 'dark';
      this.monaco?.editor.setTheme(dark ? 'vs-dark' : 'vs');
    });
    inject(DestroyRef).onDestroy(() => this.editor?.dispose());
  }
}
