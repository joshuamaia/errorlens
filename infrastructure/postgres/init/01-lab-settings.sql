-- Executado apenas na primeira inicialização do volume do PostgreSQL.
-- O esquema e os dados demonstrativos são criados pelas migrations Flyway do backend.
ALTER DATABASE errorlens SET timezone TO 'UTC';
-- Proteção contra consultas presas no laboratório
ALTER DATABASE errorlens SET statement_timeout TO '30s';
ALTER DATABASE errorlens SET idle_in_transaction_session_timeout TO '60s';
