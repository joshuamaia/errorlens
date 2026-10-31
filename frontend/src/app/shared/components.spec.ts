import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { ParsedException } from '../core/models';
import { StatusBadgeComponent } from './badges';
import { LogTableComponent } from './log-table.component';
import { StackTraceViewComponent } from './stack-trace-view.component';

describe('componentes compartilhados', () => {
  it('StatusBadge exibe status, motivo e cor', async () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentRef.setInput('status', 409);
    await fixture.whenStable();
    const badge = fixture.nativeElement.querySelector('.badge') as HTMLElement;
    expect(badge.textContent?.trim()).toBe('409 Conflict');
    expect(badge.classList).toContain('warning');
  });

  it('LogTable filtra por nível e por texto', async () => {
    const fixture = TestBed.createComponent(LogTableComponent);
    fixture.componentRef.setInput('rows', [
      { key: 1, timestamp: null, level: 'INFO', logger: 'a.B', message: 'Criando cliente' },
      { key: 2, timestamp: null, level: 'ERROR', logger: 'a.C', message: 'Violação de integridade', exceptionClass: 'x.DataIntegrityViolationException' },
      { key: 3, timestamp: null, level: 'WARN', logger: 'a.D', message: 'Retry agendado' },
    ]);
    await fixture.whenStable();
    const rows = () => fixture.nativeElement.querySelectorAll('tbody tr').length;
    expect(rows()).toBe(3);

    fixture.componentInstance.toggle('INFO');
    await fixture.whenStable();
    expect(rows()).toBe(2);

    fixture.componentInstance.query.set('integrity');
    await fixture.whenStable();
    expect(rows()).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Violação de integridade');
  });

  it('StackTraceView destaca root cause e filtra frames da aplicação', async () => {
    const exceptions: ParsedException[] = [
      {
        index: 0, className: 'com.acme.FooException', simpleName: 'FooException', message: 'falhou', relation: 'THROWN',
        depth: 0, parentIndex: null, omittedFrames: 3,
        frames: [
          { raw: '', className: 'com.acme.Service', method: 'run', file: 'Service.java', line: 10, module: null, category: 'APPLICATION', application: true, nativeMethod: false },
          { raw: '', className: 'org.springframework.Proxy', method: 'invoke', file: 'Proxy.java', line: 5, module: null, category: 'SPRING', application: false, nativeMethod: false },
        ],
      },
    ];
    const fixture = TestBed.createComponent(StackTraceViewComponent);
    fixture.componentRef.setInput('exceptions', exceptions);
    fixture.componentRef.setInput('rootIndex', 0);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Root cause');
    expect(element.textContent).toContain('... 3 more');
    expect(element.querySelectorAll('.frame').length).toBe(2);
    expect(element.querySelectorAll('.frame.app').length).toBe(1);

    fixture.componentInstance.onlyApp.set(true);
    await fixture.whenStable();
    expect(element.querySelectorAll('.frame').length).toBe(1);
    expect(element.textContent).toContain('1 frame(s) de framework ocultos');
  });
});
