import { describe, expect, it } from 'vitest';

import { filenameFrom, formatDuration, formatMicros, httpReason, outcomeTone, prettyJson, simpleName, statusTone } from './format';

describe('format', () => {
  it('extrai o nome simples de classes, inclusive internas', () => {
    expect(simpleName('org.springframework.dao.DataIntegrityViolationException')).toBe('DataIntegrityViolationException');
    expect(simpleName('org.springframework.web.client.HttpServerErrorException$ServiceUnavailable')).toBe('ServiceUnavailable');
    expect(simpleName(null)).toBe('—');
  });

  it('formata durações', () => {
    expect(formatDuration(412)).toBe('412 ms');
    expect(formatDuration(1203)).toBe('1,2 s');
    expect(formatDuration(null)).toBe('—');
    expect(formatMicros(850)).toBe('850 µs');
    expect(formatMicros(null)).toBe('sem medição');
  });

  it('usa cores consistentes por status e resultado', () => {
    expect(statusTone(201)).toBe('success');
    expect(statusTone(409)).toBe('warning');
    expect(statusTone(503)).toBe('danger');
    expect(outcomeTone('DEGRADED')).toBe('degraded');
    expect(httpReason(405)).toBe('Method Not Allowed');
  });

  it('formata JSON e preserva texto inválido', () => {
    expect(prettyJson('{"a":1}')).toBe('{\n  "a": 1\n}');
    expect(prettyJson('{ inválido')).toBe('{ inválido');
  });

  it('lê o nome do arquivo do Content-Disposition', () => {
    expect(filenameFrom('attachment; filename="errorlens-execucao-7.pdf"', 'x')).toBe('errorlens-execucao-7.pdf');
    expect(filenameFrom(null, 'fallback.json')).toBe('fallback.json');
  });
});
