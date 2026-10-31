/** Modelos espelhando os DTOs da API ErrorLens (/api/v1). */

export type Outcome = 'SUCCESS' | 'FAILURE' | 'DEGRADED';
export type ExecutionMode = 'DEFAULT' | 'SUCCESS' | 'FAILURE';
export type Confidence = 'ALTA' | 'MEDIA' | 'BAIXA';

export interface Session {
  token: string;
  username: string;
  roles: string[];
  expiresAt: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errorCode?: string;
  correlationId?: string;
  timestamp?: string;
  errors?: { field: string; message: string; code?: string }[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: string;
}

export interface Scenario {
  code: string;
  name: string;
  description: string;
  category: string;
  group: string;
  httpMethod: string;
  endpoint: string;
  expectedStatus: number;
  expectedOutcome: Outcome;
  failureType: string | null;
  difficulty: 'BASICO' | 'INTERMEDIARIO' | 'AVANCADO';
  explanation: string;
  tags: string[];
  compareSupported: boolean;
  successVariant: string | null;
  failureVariant: string | null;
  supportedModes: ExecutionMode[];
  executable: boolean;
  executionCount: number;
  lastExecutionId: number | null;
  lastStatus: number | null;
  lastOutcome: Outcome | null;
  lastExecutedAt: string | null;
}

export interface ExecutionSummary {
  id: number;
  scenarioCode: string;
  scenarioName: string;
  mode: ExecutionMode;
  correlationId: string;
  traceId: string | null;
  httpMethod: string;
  endpoint: string;
  httpStatus: number | null;
  outcome: Outcome;
  expectedStatus: number | null;
  matchedExpectation: boolean;
  startedAt: string;
  durationMs: number | null;
  errorCode: string | null;
  exceptionClass: string | null;
  rootCauseClass: string | null;
  comparisonGroup: string | null;
}

export interface Diagnosis {
  rank?: number;
  ruleId: string;
  title: string;
  category: string;
  description: string;
  confidence: Confidence;
  score: number;
  possibleCauses: string[];
  evidences: string[];
  suggestions: string[];
  fixes: string[];
  codeExample: string | null;
}

export interface Attempt {
  number: number;
  target: string;
  operation: string;
  startedAt: string;
  durationMs: number | null;
  outcome: string;
  httpStatus: number | null;
  errorClass: string | null;
  circuitState: string | null;
  detail: string | null;
}

export interface VerificationCheck {
  description: string;
  expected: string;
  actual: string;
  passed: boolean;
}

export interface Verification {
  name: string;
  conclusion: string;
  passed: boolean;
  checks: VerificationCheck[];
}

export interface ExceptionLink {
  className: string;
  message: string | null;
}

export interface ExecutionDetail {
  summary: ExecutionSummary;
  scenarioCategory: string | null;
  scenarioDescription: string | null;
  requestHeaders: Record<string, string>;
  requestBody: string | null;
  responseHeaders: Record<string, string>;
  responseBody: string | null;
  exceptionMessage: string | null;
  rootCauseMessage: string | null;
  exceptionChain: ExceptionLink[];
  stackTrace: string | null;
  verification: Verification | null;
  diagnoses: Diagnosis[];
  attempts: Attempt[];
  components: string[];
  stepCount: number;
  logCount: number;
  triggeredBy: string | null;
  finishedAt: string | null;
  jaegerTraceUrl: string | null;
  diagnosisDisclaimer: string;
}

export interface Step {
  seq: number;
  layer: string;
  layerLabel: string;
  name: string;
  component: string | null;
  depth: number;
  status: 'OK' | 'ERROR' | 'WARN' | 'INFO' | 'REJECTED';
  startedAt: string;
  offsetMicros: number;
  durationMicros: number | null;
  detail: string | null;
  errorClass: string | null;
  errorMessage: string | null;
}

export interface Stage {
  key: string;
  label: string;
  instrumented: boolean;
  reached: boolean;
  status: string;
  durationMicros: number | null;
  stepCount: number;
  note: string | null;
}

export interface Timeline {
  executionId: number;
  startedAt: string;
  durationMs: number | null;
  stages: Stage[];
  steps: Step[];
  note: string;
}

export interface LogLine {
  seq: number;
  timestamp: string;
  level: string;
  logger: string;
  thread: string | null;
  message: string;
  exceptionClass: string | null;
}

export interface FieldDiff {
  field: string;
  label: string;
  left: string | null;
  right: string | null;
  different: boolean;
}

export interface AlignedStep {
  left: Step | null;
  right: Step | null;
  relation: 'SAME' | 'STATUS_DIFFERENT' | 'LEFT_ONLY' | 'RIGHT_ONLY';
}

export interface JsonDiff {
  path: string;
  left: string | null;
  right: string | null;
  kind: 'CHANGED' | 'ADDED' | 'REMOVED';
}

export interface Comparison {
  left: ExecutionSummary;
  right: ExecutionSummary;
  fields: FieldDiff[];
  steps: AlignedStep[];
  firstDivergence: { index: number; layer: string; layerLabel: string; description: string } | null;
  requestDiff: JsonDiff[];
  responseDiff: JsonDiff[];
  headerDiff: FieldDiff[];
  logLevels: { left: Record<string, number>; right: Record<string, number> };
  leftExceptions: ExceptionLink[];
  rightExceptions: ExceptionLink[];
  leftLogs: LogLine[];
  rightLogs: LogLine[];
  observations: string[];
  causalityNote: string;
}

export interface CompareModesResult {
  comparisonGroup: string;
  successExecutionId: number;
  failureExecutionId: number;
  comparison: Comparison;
}

export interface DashboardSummary {
  range: string;
  from: string;
  to: string;
  total: number;
  success: number;
  failure: number;
  degraded: number;
  errorRate: number;
  avgDurationMs: number | null;
  p95DurationMs: number | null;
  trends: {
    totalChangePct: number | null;
    successChangePct: number | null;
    failureChangePct: number | null;
    errorRateDeltaPp: number | null;
    avgDurationChangePct: number | null;
  };
  sparklines: { total: number[]; success: number[]; failure: number[]; errorRate: number[]; avgDuration: number[] };
}

export interface DashboardMetrics {
  range: string;
  timeseries: { start: string; total: number; success: number; failure: number; degraded: number; avgDurationMs: number | null }[];
  statusDistribution: { status: number; count: number; percent: number }[];
  topExceptions: { exception: string; className: string; rootCause: string | null; count: number; percent: number }[];
  topScenarios: { code: string; name: string; count: number; failures: number }[];
  durationHistogram: { bin: number; label: string; count: number }[];
  latestExecutions: ExecutionSummary[];
}

export interface ServiceStatus {
  key: string;
  name: string;
  status: 'UP' | 'DOWN' | 'DEGRADED';
  latencyMs: number | null;
  detail: string;
  publicUrl: string | null;
}

export interface HealthReport {
  overall: 'UP' | 'DOWN' | 'DEGRADED';
  services: ServiceStatus[];
  checkedAt: string;
  jaegerPublicUrl: string;
}

export interface Frame {
  raw: string;
  className: string;
  method: string;
  file: string | null;
  line: number | null;
  module: string | null;
  category: string;
  application: boolean;
  nativeMethod: boolean;
}

export interface ParsedException {
  index: number;
  className: string;
  simpleName: string;
  message: string | null;
  relation: 'THROWN' | 'CAUSED_BY' | 'SUPPRESSED';
  depth: number;
  parentIndex: number | null;
  frames: Frame[];
  omittedFrames: number;
}

export interface StackTraceAnalysis {
  exceptions: ParsedException[];
  primary: ParsedException | null;
  rootCause: ParsedException | null;
  chainLength: number;
  firstApplicationFrame: { exceptionIndex: number; frameIndex: number; frame: Frame } | null;
  frameCategories: Record<string, number>;
  involvedClasses: { className: string; category: string; methods: string[]; file: string | null }[];
  stats: { totalLines: number; totalFrames: number; applicationFrames: number; omittedFrames: number; unparsedLines: number; suppressedCount: number };
  warnings: string[];
  diagnoses: Diagnosis[];
  disclaimer: string;
}

export interface HttpErrorInput {
  method: string;
  url: string;
  status: number;
  headers: string;
  body: string;
}

export interface HttpErrorAnalysis {
  request: { method: string; host: string | null; path: string | null; queryParameters: string[] };
  status: number;
  reason: string;
  statusClass: string;
  format: string;
  extracted: Record<string, unknown>;
  headers: Record<string, string>;
  findings: { severity: 'INFO' | 'WARN' | 'ERROR'; title: string; description: string; evidence: string; suggestion: string }[];
  diagnoses: Diagnosis[];
  disclaimer: string;
}

export interface LogEntry {
  index: number;
  timestamp: string | null;
  level: string;
  logger: string | null;
  thread: string | null;
  message: string | null;
  correlationId: string | null;
  traceId: string | null;
  exceptionClass: string | null;
  details: string | null;
}

export interface LogAnalysis {
  source: string;
  format: string;
  totalLines: number;
  totalEntries: number;
  truncated: boolean;
  levelCounts: Record<string, number>;
  groups: {
    correlationId: string;
    count: number;
    errorCount: number;
    warnCount: number;
    first: string | null;
    last: string | null;
    durationMs: number | null;
    exceptions: string[];
    loggers: string[];
    traceIds: string[];
    entryIndexes: number[];
  }[];
  exceptions: { className: string; count: number; firstSeen: string | null; correlationIds: string[] }[];
  from: string | null;
  to: string | null;
  entries: LogEntry[];
  warnings: string[];
}

export interface ExampleDescriptor {
  id: string;
  type: 'stacktrace' | 'http' | 'log';
  title: string;
  description: string;
  file: string;
}

export interface Example {
  descriptor: ExampleDescriptor;
  content: string;
}

export interface HistoryFilter {
  from?: string;
  to?: string;
  outcome?: string;
  httpStatus?: number | null;
  scenario?: string;
  correlationId?: string;
  exception?: string;
  q?: string;
  group?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: 'asc' | 'desc';
}
