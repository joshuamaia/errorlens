# Arquitetura do ErrorLens

## Visão de containers

```mermaid
flowchart LR
    subgraph Host
      B[Navegador]
    end
    subgraph errorlens-network
      FE[errorlens-frontend<br/>Nginx 1.29 + Angular 22]
      BE[errorlens-backend<br/>Spring Boot 4.1 / Java 25]
      SIM[errorlens-dependency-simulator<br/>Spring Boot 4.1]
      PG[(errorlens-postgres<br/>PostgreSQL 17)]
      OC[errorlens-otel-collector]
      JG[errorlens-jaeger 2.12]
    end
    B -- ":4200" --> FE
    B -- ":16686" --> JG
    B -. ":8080 (testes locais)" .-> BE
    FE -- "/api, /swagger-ui, /v3/api-docs" --> BE
    BE -- "JDBC (Flyway, JPA, JdbcTemplate)" --> PG
    BE -- "HTTP + X-Correlation-Id + traceparent" --> SIM
    BE -- "OTLP/HTTP :4318" --> OC
    SIM -- "OTLP/HTTP :4318" --> OC
    OC -- "OTLP/gRPC :4317" --> JG
```

## Fluxo de uma execução de cenário

```mermaid
sequenceDiagram
    autonumber
    participant UI as Angular
    participant API as ScenarioController
    participant R as ScenarioRunner
    participant SIM as Dependency Simulator
    participant LAB as /api/v1/lab/** (mesmo backend)
    participant DB as PostgreSQL
    UI->>API: POST /scenarios/D01/execute
    API->>R: execute(D01, DEFAULT)
    R->>R: ExecutionRecorder.start() + MDC
    R->>DB: preparação (dados isolados)
    opt cenários F
      R->>SIM: PUT /internal/control/behaviors/{chave}
    end
    R->>LAB: requisição HTTP real (Basic, X-Correlation-Id, X-ErrorLens-Execution)
    Note over LAB: ExecutionCaptureFilter → Spring Security (etapas) → Controller/Service/Repository (AOP)<br/>→ datasource-proxy (SQL, BEGIN/COMMIT/ROLLBACK) → Resilience4j (eventos) → @RestControllerAdvice
    LAB->>DB: INSERT customers
    DB-->>LAB: 23505 unique_violation
    LAB-->>R: 409 Problem Details
    R->>DB: verificação automática (quando aplicável)
    R->>R: DiagnosisEngine.diagnose()
    R->>DB: persiste execução, etapas, logs, tentativas e diagnósticos (sanitizados)
    API-->>UI: ExecutionDetail
```

## Camadas do backend

| Pacote | Responsabilidade |
|---|---|
| `domain` | Entidades JPA (`Customer`, `Order`, `Scenario`, `Execution`, `ExecutionStep`, `ExecutionLog`, `DiagnosisRecord`, `ScenarioAttempt`), enums e exceptions de domínio. |
| `application.lab` | Serviços do domínio de laboratório (clientes, pedidos, fidelidade, arquivamento, auditoria, integrações com Resilience4j). |
| `application.scenario` | Catálogo, handlers dos cenários, `ScenarioRunner`, `LabRequestDispatcher` (loopback HTTP). |
| `application.execution` | Persistência, consultas, timeline, comparação (LCS), retenção. |
| `application.diagnosis` | `DiagnosisEngine`, regras declarativas (`rules.json`) e programáticas. |
| `application.analyzer` | Stack trace parser/analyzer, HTTP error analyzer, log analyzer, exemplos. |
| `application.dashboard`, `health`, `report`, `auth` | Indicadores, saúde dos serviços, relatórios (JSON/MD/PDF), tokens. |
| `infrastructure.observability` | CorrelationId, captura de execução, contexto por thread, aspecto AOP, listener JDBC, appender Logback, sanitização, propagação de contexto assíncrono. |
| `infrastructure.security` | Filtros de token e de etapas de segurança, handlers 401/403 em Problem Details, identidades. |
| `infrastructure.simulator`, `resilience`, `persistence` | Clientes HTTP do simulador, eventos Resilience4j, repositórios Spring Data. |
| `interfaces.rest`, `interfaces.lab`, `interfaces.error` | API da plataforma, API de laboratório e tratamento global de exceptions. |

## Modelo de dados

```mermaid
erDiagram
    customers ||--o{ orders : "fk_orders_customer (RESTRICT)"
    scenarios ||--o{ executions : "fk_executions_scenario"
    executions ||--o{ execution_steps : "cascade"
    executions ||--o{ execution_logs : "cascade"
    executions ||--o{ diagnoses : "cascade"
    executions ||--o{ scenario_attempts : "cascade"
    customers { bigint id PK
      varchar email UK
      varchar origin "SEED | SCENARIO"
      bigint version }
    executions { bigint id PK
      varchar correlation_id UK
      varchar trace_id
      int http_status
      varchar outcome "SUCCESS | FAILURE | DEGRADED" }
```

## Propagação de contexto

- **HTTP de entrada:** `CorrelationIdFilter` reutiliza/gera `X-Correlation-Id`, publica no MDC e no header
  de resposta e limpa o MDC ao final.
- **Execução:** `ExecutionCaptureFilter` só instrumenta requisições cujo header de execução corresponde a um
  trace registrado pelo próprio backend (impede injeção externa).
- **Assíncrono:** `ContextPropagatingTaskDecorator` copia MDC, contexto de execução e `ContextSnapshot` do
  Micrometer para as threads do executor usado pelo TimeLimiter e restaura o estado anterior ao final.
- **HTTP de saída:** `OutboundCallInterceptor` envia `X-Correlation-Id`; o RestClient observado envia
  `traceparent` (W3C) — o simulador reutiliza ambos.
