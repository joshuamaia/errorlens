import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';

import { ApiService } from '../core/api.service';
import { Comparison, ExecutionSummary, Scenario } from '../core/models';
import { ComparatorPage } from './comparator/comparator.page';
import { DashboardPage } from './dashboard/dashboard.page';
import { ScenarioLabPage } from './scenarios/scenario-lab.page';

function scenario(code: string, outcome: Scenario['expectedOutcome'], tags: string[]): Scenario {
  return {
    code, name: 'Cenário ' + code, description: 'desc', category: 'HTTP', group: 'G', httpMethod: 'GET',
    endpoint: '/api', expectedStatus: outcome === 'SUCCESS' ? 200 : 409, expectedOutcome: outcome, failureType: null,
    difficulty: 'BASICO', explanation: '', tags, compareSupported: false, successVariant: null, failureVariant: null,
    supportedModes: ['DEFAULT'], executable: true, executionCount: 0, lastExecutionId: null, lastStatus: null,
    lastOutcome: null, lastExecutedAt: null,
  };
}

function summary(id: number, status: number): ExecutionSummary {
  return {
    id, scenarioCode: 'A02', scenarioName: 'Criação válida', mode: 'SUCCESS', correlationId: 'c-' + id, traceId: null,
    httpMethod: 'POST', endpoint: '/api/v1/lab/customers', httpStatus: status, outcome: status < 300 ? 'SUCCESS' : 'FAILURE',
    expectedStatus: status, matchedExpectation: true, startedAt: new Date().toISOString(), durationMs: 50, errorCode: null,
    exceptionClass: null, rootCauseClass: null, comparisonGroup: null,
  };
}

function setup<T>(component: Type<T>, api: Partial<Record<keyof ApiService, unknown>>) {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ApiService, useValue: api }] });
  return TestBed.createComponent(component);
}

describe('páginas', () => {
  it('Dashboard mostra estado de erro quando o backend falha', async () => {
    const fixture = setup(DashboardPage, {
      dashboardSummary: () => throwError(() => new Error('down')),
      dashboardMetrics: () => throwError(() => new Error('down')),
      health: () => throwError(() => new Error('down')),
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar os indicadores');
  });

  it('Scenario Lab filtra por sucesso, falha e categoria', async () => {
    const fixture = setup(ScenarioLabPage, {
      scenarios: () => of([scenario('A01', 'SUCCESS', ['sucesso']), scenario('C01', 'FAILURE', ['seguranca']), scenario('F05', 'DEGRADED', ['resiliencia'])]),
    });
    await fixture.whenStable();
    const cards = () => fixture.nativeElement.querySelectorAll('article.scenario').length;
    expect(cards()).toBe(3);

    const page = fixture.componentInstance as unknown as { activeFilter: { set(v: string): void }; query: { set(v: string): void } };
    page.activeFilter.set('FAILURE');
    await fixture.whenStable();
    expect(cards()).toBe(2);

    page.activeFilter.set('SEGURANCA');
    await fixture.whenStable();
    expect(cards()).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('C01');

    page.activeFilter.set('ALL');
    page.query.set('f05');
    await fixture.whenStable();
    expect(cards()).toBe(1);
  });

  it('Comparator exibe a primeira divergência e a nota de causalidade', async () => {
    const comparison: Comparison = {
      left: summary(1, 201), right: summary(2, 409),
      fields: [{ field: 'httpStatus', label: 'Status HTTP', left: '201', right: '409', different: true }],
      steps: [], firstDivergence: { index: 3, layer: 'DATABASE', layerLabel: 'PostgreSQL', description: 'INSERT customers terminou com ERROR' },
      requestDiff: [], responseDiff: [], headerDiff: [], logLevels: { left: {}, right: {} }, leftExceptions: [], rightExceptions: [],
      leftLogs: [], rightLogs: [], observations: ['Status HTTP diferente: 201 (A) vs 409 (B).'], causalityNote: 'não necessariamente a causa',
    };
    const fixture = setup(ComparatorPage, {
      executions: () => of({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, sort: '' }),
      compare: (): Observable<Comparison> => of(comparison),
    });
    fixture.componentRef.setInput('left', '1');
    fixture.componentRef.setInput('right', '2');
    await fixture.whenStable();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Primeira divergência observável — PostgreSQL');
    expect(text).toContain('INSERT customers terminou com ERROR');
    expect(text).toContain('não necessariamente a causa');
  });
});
