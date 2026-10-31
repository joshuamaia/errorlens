import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { ApiService } from './api.service';
import { NotifyService } from './notify.service';

export interface TourStep {
  title: string;
  description: string;
  action: string;
  icon: string;
}

interface TourState {
  active: boolean;
  step: number;
  successId: number | null;
  failureId: number | null;
}

const KEY = 'errorlens.tour';

export const TOUR_STEPS: TourStep[] = [
  {
    title: 'Executar uma operação válida',
    description:
      'Executa o cenário A02 (criação válida de cliente) no backend real: Spring Security, controller, service, JPA e PostgreSQL.',
    action: 'Executar A02',
    icon: 'play',
  },
  {
    title: 'Executar uma operação com falha',
    description:
      'Executa o cenário D01: o mesmo endpoint com e-mail duplicado. O PostgreSQL rejeita o INSERT pela unique constraint.',
    action: 'Executar D01',
    icon: 'bug',
  },
  {
    title: 'Abrir o diagnóstico',
    description: 'Abre o Investigation Center da execução com falha, na aba Diagnosis gerada pelo Diagnosis Engine.',
    action: 'Abrir diagnóstico',
    icon: 'lightbulb',
  },
  {
    title: 'Visualizar a causa provável',
    description: 'Mostra a cadeia de exceptions e a root cause (PSQLException com SQLState 23505).',
    action: 'Ver exception',
    icon: 'crosshair',
  },
  {
    title: 'Consultar os logs',
    description: 'Exibe os logs correlacionados pelo correlationId da execução, capturados via MDC.',
    action: 'Ver logs',
    icon: 'file-text',
  },
  {
    title: 'Comparar as execuções',
    description: 'Compara a execução válida com a de falha e destaca a primeira divergência observável.',
    action: 'Comparar',
    icon: 'compare',
  },
  {
    title: 'Consultar a rastreabilidade',
    description: 'Abre a timeline instrumentada e o link do trace distribuído no Jaeger (traceId da execução).',
    action: 'Ver timeline',
    icon: 'waypoints',
  },
];

/** Estado do Guided Tour; cada passo executa uma funcionalidade real da aplicação. */
@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);
  private readonly state = signal<TourState>(TourService.restore());

  readonly steps = TOUR_STEPS;
  readonly active = computed(() => this.state().active);
  readonly stepIndex = computed(() => this.state().step);
  readonly current = computed(() => TOUR_STEPS[this.state().step]);
  readonly successId = computed(() => this.state().successId);
  readonly failureId = computed(() => this.state().failureId);
  readonly busy = signal(false);

  start(): void {
    this.update({ active: true, step: 0, successId: null, failureId: null });
  }

  close(): void {
    this.update({ ...this.state(), active: false });
  }

  go(step: number): void {
    this.update({ ...this.state(), step: Math.max(0, Math.min(TOUR_STEPS.length - 1, step)) });
  }

  /** O passo exige execuções anteriores? */
  canRun(step: number): boolean {
    const s = this.state();
    if (step === 0 || step === 1) return true;
    if (step === 5) return s.successId !== null && s.failureId !== null;
    return s.failureId !== null;
  }

  async run(): Promise<void> {
    const step = this.state().step;
    this.busy.set(true);
    try {
      switch (step) {
        case 0: {
          const detail = await firstValueFrom(this.api.execute('A02', 'SUCCESS'));
          this.update({ ...this.state(), successId: detail.summary.id });
          this.notify.success(`A02 executado: HTTP ${detail.summary.httpStatus}`, 'Guided Tour');
          await this.router.navigate(['/investigation', detail.summary.id], { queryParams: { tab: 'overview' } });
          break;
        }
        case 1: {
          const detail = await firstValueFrom(this.api.execute('D01', 'DEFAULT'));
          this.update({ ...this.state(), failureId: detail.summary.id });
          this.notify.warn(`D01 executado: HTTP ${detail.summary.httpStatus}`, 'Guided Tour');
          await this.router.navigate(['/investigation', detail.summary.id], { queryParams: { tab: 'response' } });
          break;
        }
        case 2:
          await this.openFailure('diagnosis');
          break;
        case 3:
          await this.openFailure('exception');
          break;
        case 4:
          await this.openFailure('logs');
          break;
        case 5:
          await this.router.navigate(['/comparator'], {
            queryParams: { left: this.state().successId, right: this.state().failureId },
          });
          break;
        case 6:
          await this.openFailure('timeline');
          break;
      }
      if (step < TOUR_STEPS.length - 1) {
        this.go(step + 1);
      } else {
        this.notify.success('Tour concluído! Explore os demais cenários no Scenario Lab.', 'Guided Tour');
      }
    } finally {
      this.busy.set(false);
    }
  }

  private openFailure(tab: string): Promise<boolean> {
    return this.router.navigate(['/investigation', this.state().failureId], { queryParams: { tab } });
  }

  private update(state: TourState): void {
    this.state.set(state);
    try {
      sessionStorage.setItem(KEY, JSON.stringify(state));
    } catch {
      // estado apenas em memória
    }
  }

  private static restore(): TourState {
    try {
      const raw = sessionStorage.getItem(KEY);
      if (raw) return JSON.parse(raw) as TourState;
    } catch {
      // ignorado
    }
    return { active: false, step: 0, successId: null, failureId: null };
  }
}
