import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CompareModesResult,
  Comparison,
  DashboardMetrics,
  DashboardSummary,
  Example,
  ExampleDescriptor,
  ExecutionDetail,
  ExecutionMode,
  ExecutionSummary,
  HealthReport,
  HistoryFilter,
  HttpErrorAnalysis,
  HttpErrorInput,
  LogAnalysis,
  LogLine,
  Page,
  Scenario,
  Session,
  StackTraceAnalysis,
  Timeline,
} from './models';

const API = '/api/v1';

/** Cliente único da API REST do ErrorLens. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ---- Autenticação ----
  login(username: string, password: string): Observable<Session> {
    return this.http.post<Session>(`${API}/auth/login`, { username, password });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${API}/auth/logout`, {});
  }

  // ---- Cenários ----
  scenarios(): Observable<Scenario[]> {
    return this.http.get<Scenario[]>(`${API}/scenarios`);
  }

  scenario(code: string): Observable<Scenario> {
    return this.http.get<Scenario>(`${API}/scenarios/${code}`);
  }

  execute(code: string, mode: ExecutionMode = 'DEFAULT'): Observable<ExecutionDetail> {
    return this.http.post<ExecutionDetail>(`${API}/scenarios/${code}/execute`, { mode });
  }

  compareModes(code: string): Observable<CompareModesResult> {
    return this.http.post<CompareModesResult>(`${API}/scenarios/${code}/compare-modes`, {});
  }

  // ---- Execuções ----
  executions(filter: HistoryFilter): Observable<Page<ExecutionSummary>> {
    let params = new HttpParams();
    Object.entries(filter).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return this.http.get<Page<ExecutionSummary>>(`${API}/executions`, { params });
  }

  execution(id: number): Observable<ExecutionDetail> {
    return this.http.get<ExecutionDetail>(`${API}/executions/${id}`);
  }

  executionByCorrelation(correlationId: string): Observable<ExecutionDetail> {
    return this.http.get<ExecutionDetail>(`${API}/executions/by-correlation/${encodeURIComponent(correlationId)}`);
  }

  logs(id: number): Observable<LogLine[]> {
    return this.http.get<LogLine[]>(`${API}/executions/${id}/logs`);
  }

  timeline(id: number): Observable<Timeline> {
    return this.http.get<Timeline>(`${API}/executions/${id}/timeline`);
  }

  compare(leftId: number, rightId: number): Observable<Comparison> {
    return this.http.post<Comparison>(`${API}/executions/compare`, { leftId, rightId });
  }

  report(id: number, format: 'json' | 'markdown' | 'pdf'): Observable<HttpResponse<Blob>> {
    return this.http.get(`${API}/executions/${id}/report`, {
      params: { format },
      responseType: 'blob',
      observe: 'response',
    });
  }

  deleteExecution(id: number): Observable<void> {
    return this.http.delete<void>(`${API}/executions/${id}`);
  }

  purge(olderThanDays: number): Observable<{ removed: number }> {
    return this.http.delete<{ removed: number }>(`${API}/executions`, { params: { olderThanDays } });
  }

  // ---- Dashboard / saúde ----
  dashboardSummary(range: string): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${API}/dashboard/summary`, { params: { range } });
  }

  dashboardMetrics(range: string): Observable<DashboardMetrics> {
    return this.http.get<DashboardMetrics>(`${API}/dashboard/metrics`, { params: { range } });
  }

  health(): Observable<HealthReport> {
    return this.http.get<HealthReport>(`${API}/health/services`);
  }

  // ---- Analisadores ----
  analyzeStackTrace(content: string, applicationPackages: string[]): Observable<StackTraceAnalysis> {
    return this.http.post<StackTraceAnalysis>(`${API}/analyzer/stacktrace`, { content, applicationPackages });
  }

  analyzeHttpError(input: HttpErrorInput): Observable<HttpErrorAnalysis> {
    return this.http.post<HttpErrorAnalysis>(`${API}/analyzer/http-error`, input);
  }

  analyzeLogFile(file: File): Observable<LogAnalysis> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<LogAnalysis>(`${API}/analyzer/logs`, form);
  }

  analyzeLogText(content: string, source?: string): Observable<LogAnalysis> {
    return this.http.post<LogAnalysis>(`${API}/analyzer/logs/text`, { content, source });
  }

  examples(type?: string): Observable<ExampleDescriptor[]> {
    return this.http.get<ExampleDescriptor[]>(`${API}/analyzer/examples`, { params: type ? { type } : {} });
  }

  example(id: string): Observable<Example> {
    return this.http.get<Example>(`${API}/analyzer/examples/${id}`);
  }

  rules(): Observable<{ count: number; rules: string[] }> {
    return this.http.get<{ count: number; rules: string[] }>(`${API}/analyzer/rules`);
  }

  // ---- Administração do laboratório ----
  resetLab(): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${API}/lab-admin/reset`, {});
  }
}
