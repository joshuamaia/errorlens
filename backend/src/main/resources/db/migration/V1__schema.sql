-- ===========================================================================
-- ErrorLens - esquema principal
-- Dados de laboratório (clientes/pedidos) + registro de execuções/diagnósticos
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Domínio de laboratório
-- ---------------------------------------------------------------------------
CREATE TABLE customers (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120)   NOT NULL,
    email         VARCHAR(160)   NOT NULL,
    document      VARCHAR(20),
    status        VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    loyalty_tier  VARCHAR(20),
    credit_limit  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    origin        VARCHAR(20)    NOT NULL DEFAULT 'SEED',
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_customers_email UNIQUE (email),
    CONSTRAINT ck_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
    CONSTRAINT ck_customers_origin CHECK (origin IN ('SEED', 'SCENARIO')),
    CONSTRAINT ck_customers_credit CHECK (credit_limit >= 0)
);

CREATE INDEX ix_customers_origin_created ON customers (origin, created_at);

CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    customer_id      BIGINT         NOT NULL,
    description      VARCHAR(200)   NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'CREATED',
    invoice_metadata VARCHAR(120),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT ck_orders_amount CHECK (amount > 0),
    CONSTRAINT ck_orders_status CHECK (status IN ('CREATED', 'PAID', 'SHIPPED', 'CANCELLED'))
);

CREATE INDEX ix_orders_customer ON orders (customer_id);

-- ---------------------------------------------------------------------------
-- Catálogo de cenários
-- ---------------------------------------------------------------------------
CREATE TABLE scenarios (
    code              VARCHAR(10)  PRIMARY KEY,
    name              VARCHAR(120) NOT NULL,
    description       TEXT         NOT NULL,
    category          VARCHAR(30)  NOT NULL,
    group_name        VARCHAR(60)  NOT NULL,
    http_method       VARCHAR(10)  NOT NULL,
    endpoint          VARCHAR(200) NOT NULL,
    expected_status   INT          NOT NULL,
    expected_outcome  VARCHAR(12)  NOT NULL,
    failure_type      VARCHAR(80),
    difficulty        VARCHAR(15)  NOT NULL,
    explanation       TEXT         NOT NULL,
    tags              VARCHAR(200) NOT NULL DEFAULT '',
    compare_supported BOOLEAN      NOT NULL DEFAULT FALSE,
    success_variant   TEXT,
    failure_variant   TEXT,
    display_order     INT          NOT NULL,
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_scenarios_outcome CHECK (expected_outcome IN ('SUCCESS', 'FAILURE', 'DEGRADED')),
    CONSTRAINT ck_scenarios_difficulty CHECK (difficulty IN ('BASICO', 'INTERMEDIARIO', 'AVANCADO'))
);

-- ---------------------------------------------------------------------------
-- Execuções e instrumentação
-- ---------------------------------------------------------------------------
CREATE TABLE executions (
    id                  BIGSERIAL PRIMARY KEY,
    scenario_code       VARCHAR(10)  NOT NULL,
    mode                VARCHAR(10)  NOT NULL,
    correlation_id      VARCHAR(64)  NOT NULL,
    trace_id            VARCHAR(32),
    span_id             VARCHAR(16),
    http_method         VARCHAR(10)  NOT NULL,
    endpoint            VARCHAR(300) NOT NULL,
    http_status         INT,
    outcome             VARCHAR(12)  NOT NULL,
    expected_status     INT,
    matched_expectation BOOLEAN      NOT NULL DEFAULT FALSE,
    started_at          TIMESTAMPTZ  NOT NULL,
    finished_at         TIMESTAMPTZ,
    duration_ms         BIGINT,
    request_headers     TEXT,
    request_body        TEXT,
    response_headers    TEXT,
    response_body       TEXT,
    error_code          VARCHAR(80),
    exception_class     VARCHAR(255),
    exception_message   TEXT,
    root_cause_class    VARCHAR(255),
    root_cause_message  TEXT,
    exception_chain     TEXT,
    stack_trace         TEXT,
    verification        TEXT,
    comparison_group    VARCHAR(40),
    triggered_by        VARCHAR(60),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_executions_correlation UNIQUE (correlation_id),
    CONSTRAINT fk_executions_scenario FOREIGN KEY (scenario_code) REFERENCES scenarios (code),
    CONSTRAINT ck_executions_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DEGRADED')),
    CONSTRAINT ck_executions_mode CHECK (mode IN ('DEFAULT', 'SUCCESS', 'FAILURE'))
);

CREATE INDEX ix_executions_started ON executions (started_at DESC);
CREATE INDEX ix_executions_scenario ON executions (scenario_code);
CREATE INDEX ix_executions_status ON executions (http_status);
CREATE INDEX ix_executions_outcome ON executions (outcome);
CREATE INDEX ix_executions_exception ON executions (exception_class);
CREATE INDEX ix_executions_trace ON executions (trace_id);
CREATE INDEX ix_executions_group ON executions (comparison_group);

CREATE TABLE execution_steps (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    BIGINT       NOT NULL,
    seq             INT          NOT NULL,
    layer           VARCHAR(30)  NOT NULL,
    name            VARCHAR(160) NOT NULL,
    component       VARCHAR(255),
    depth           INT          NOT NULL DEFAULT 0,
    status          VARCHAR(12)  NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL,
    offset_micros   BIGINT       NOT NULL,
    duration_micros BIGINT,
    detail          TEXT,
    error_class     VARCHAR(255),
    error_message   TEXT,
    CONSTRAINT fk_steps_execution FOREIGN KEY (execution_id) REFERENCES executions (id) ON DELETE CASCADE,
    CONSTRAINT uk_steps_seq UNIQUE (execution_id, seq),
    CONSTRAINT ck_steps_status CHECK (status IN ('OK', 'ERROR', 'WARN', 'INFO', 'REJECTED'))
);

CREATE TABLE execution_logs (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    BIGINT       NOT NULL,
    seq             INT          NOT NULL,
    logged_at       TIMESTAMPTZ  NOT NULL,
    level           VARCHAR(8)   NOT NULL,
    logger          VARCHAR(255) NOT NULL,
    thread          VARCHAR(120),
    message         TEXT         NOT NULL,
    exception_class VARCHAR(255),
    CONSTRAINT fk_logs_execution FOREIGN KEY (execution_id) REFERENCES executions (id) ON DELETE CASCADE,
    CONSTRAINT uk_logs_seq UNIQUE (execution_id, seq)
);

CREATE TABLE diagnoses (
    id               BIGSERIAL PRIMARY KEY,
    execution_id     BIGINT       NOT NULL,
    rank             INT          NOT NULL,
    rule_id          VARCHAR(80)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    category         VARCHAR(40)  NOT NULL,
    description      TEXT         NOT NULL,
    confidence       VARCHAR(10)  NOT NULL,
    confidence_score INT          NOT NULL,
    possible_causes  TEXT         NOT NULL,
    evidences        TEXT         NOT NULL,
    suggestions      TEXT         NOT NULL,
    fixes            TEXT         NOT NULL,
    code_example     TEXT,
    CONSTRAINT fk_diagnoses_execution FOREIGN KEY (execution_id) REFERENCES executions (id) ON DELETE CASCADE,
    CONSTRAINT ck_diagnoses_confidence CHECK (confidence IN ('ALTA', 'MEDIA', 'BAIXA'))
);

CREATE INDEX ix_diagnoses_execution ON diagnoses (execution_id);

CREATE TABLE scenario_attempts (
    id             BIGSERIAL PRIMARY KEY,
    execution_id   BIGINT       NOT NULL,
    attempt_number INT          NOT NULL,
    target         VARCHAR(120) NOT NULL,
    operation      VARCHAR(120) NOT NULL,
    started_at     TIMESTAMPTZ  NOT NULL,
    duration_ms    BIGINT,
    outcome        VARCHAR(20)  NOT NULL,
    http_status    INT,
    error_class    VARCHAR(255),
    circuit_state  VARCHAR(20),
    detail         TEXT,
    CONSTRAINT fk_attempts_execution FOREIGN KEY (execution_id) REFERENCES executions (id) ON DELETE CASCADE
);

CREATE INDEX ix_attempts_execution ON scenario_attempts (execution_id);
