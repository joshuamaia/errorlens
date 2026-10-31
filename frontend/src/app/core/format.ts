/** Utilitários de formatação (pt-BR). */

export function simpleName(className: string | null | undefined): string {
  if (!className) return '—';
  const simple = className.substring(className.lastIndexOf('.') + 1);
  return simple.includes('$') ? simple.substring(simple.lastIndexOf('$') + 1) : simple;
}

export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return '—';
  if (ms < 1) return `${ms.toFixed(2)} ms`;
  if (ms < 1000) return `${Math.round(ms)} ms`;
  return `${(ms / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} s`;
}

export function formatMicros(us: number | null | undefined): string {
  if (us === null || us === undefined) return 'sem medição';
  if (us < 1000) return `${us} µs`;
  return formatDuration(us / 1000);
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  return date.toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  return (
    date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) +
    '.' +
    String(date.getMilliseconds()).padStart(3, '0')
  );
}

export function formatNumber(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined) return '—';
  return value.toLocaleString('pt-BR', { maximumFractionDigits: digits, minimumFractionDigits: digits });
}

/** Classe visual por status HTTP: verde 2xx, azul 3xx, amarelo 4xx, vermelho 5xx. */
export function statusTone(status: number | null | undefined): string {
  if (!status) return '';
  if (status < 300) return 'success';
  if (status < 400) return 'info';
  if (status < 500) return 'warning';
  return 'danger';
}

export function outcomeTone(outcome: string | null | undefined): string {
  switch (outcome) {
    case 'SUCCESS':
      return 'success';
    case 'DEGRADED':
      return 'degraded';
    case 'FAILURE':
      return 'danger';
    default:
      return '';
  }
}

export function outcomeLabel(outcome: string | null | undefined): string {
  switch (outcome) {
    case 'SUCCESS':
      return 'Sucesso';
    case 'DEGRADED':
      return 'Degradada';
    case 'FAILURE':
      return 'Falha';
    default:
      return '—';
  }
}

export function stepTone(status: string): string {
  switch (status) {
    case 'OK':
      return 'success';
    case 'ERROR':
      return 'danger';
    case 'WARN':
      return 'warning';
    case 'REJECTED':
      return 'degraded';
    default:
      return 'info';
  }
}

export function levelTone(level: string): string {
  switch (level) {
    case 'ERROR':
      return 'danger';
    case 'WARN':
      return 'warning';
    case 'INFO':
      return 'info';
    default:
      return '';
  }
}

const HTTP_REASONS: Record<number, string> = {
  200: 'OK',
  201: 'Created',
  202: 'Accepted',
  204: 'No Content',
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  405: 'Method Not Allowed',
  409: 'Conflict',
  413: 'Content Too Large',
  415: 'Unsupported Media Type',
  422: 'Unprocessable Content',
  429: 'Too Many Requests',
  500: 'Internal Server Error',
  502: 'Bad Gateway',
  503: 'Service Unavailable',
  504: 'Gateway Timeout',
};

export function httpReason(status: number | null | undefined): string {
  return status ? (HTTP_REASONS[status] ?? '') : '';
}

export function prettyJson(text: string | null | undefined): string {
  if (!text) return '';
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function filenameFrom(disposition: string | null, fallback: string): string {
  const match = disposition?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
  return match ? decodeURIComponent(match[1]) : fallback;
}

export const CATEGORY_LABELS: Record<string, string> = {
  SUCESSO: 'Sucesso',
  HTTP: 'HTTP',
  VALIDACAO: 'Validação',
  SEGURANCA: 'Segurança',
  BANCO_DE_DADOS: 'Banco de dados',
  EXCEPTIONS: 'Exceptions',
  RESILIENCIA: 'Resiliência',
  INTEGRACAO: 'Integração',
};

export const DIFFICULTY_LABELS: Record<string, string> = {
  BASICO: 'Básico',
  INTERMEDIARIO: 'Intermediário',
  AVANCADO: 'Avançado',
};
