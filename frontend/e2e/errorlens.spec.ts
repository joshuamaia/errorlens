import { expect, Page, test } from '@playwright/test';

/**
 * Fluxo de demonstração completo contra o backend real:
 * Dashboard → Scenario Lab (sucesso e falha) → diagnóstico → timeline → comparação → histórico.
 */

async function login(page: Page, username = 'admin', password = 'errorlens') {
  await page.goto('/login');
  await page.getByTestId('login-username').fill(username);
  await page.getByTestId('login-password').fill(password);
  await page.getByTestId('login-submit').click();
  await page.waitForURL('**/dashboard');
}

async function apiToken(page: Page): Promise<string> {
  const response = await page.request.post('/api/v1/auth/login', { data: { username: 'admin', password: 'errorlens' } });
  expect(response.ok()).toBeTruthy();
  return (await response.json()).token as string;
}

test.describe.serial('ErrorLens — fluxo principal', () => {
  let failureCorrelationId = '';

  test('dashboard exibe indicadores vindos do backend', async ({ page }) => {
    await login(page);
    const token = await apiToken(page);
    const summary = await (
      await page.request.get('/api/v1/dashboard/summary?range=24h', { headers: { Authorization: `Bearer ${token}` } })
    ).json();

    await expect(page.getByTestId('dashboard-page')).toBeVisible();
    await expect(page.getByTestId('kpi-total')).toContainText(summary.total.toLocaleString('pt-BR'));
    await expect(page.getByTestId('services-health')).toContainText('PostgreSQL');
    await expect(page.getByTestId('services-health')).toContainText('Operacional');
  });

  test('executa um cenário de sucesso no Scenario Lab', async ({ page }) => {
    await login(page);
    await page.getByTestId('nav-scenarios').click();
    await page.getByTestId('run-A01').click();

    const result = page.getByTestId('execution-result');
    await expect(result).toBeVisible();
    await expect(result.getByTestId('result-status')).toHaveText('200 OK');
    await expect(result).toContainText('Sucesso');
    await expect(result).toContainText('conforme');
  });

  test('executa um cenário de falha e visualiza o diagnóstico', async ({ page }) => {
    await login(page);
    await page.goto('/scenarios');
    await page.getByTestId('run-D01').click();

    const result = page.getByTestId('execution-result');
    await expect(result.getByTestId('result-status')).toHaveText('409 Conflict');
    failureCorrelationId = (await result.getByTestId('result-correlation').innerText()).trim();
    expect(failureCorrelationId).toMatch(/^[0-9a-f-]{36}$/);

    await page.getByTestId('open-investigation').click();
    await expect(page.getByTestId('investigation-status')).toHaveText('409 Conflict');
    await expect(page.getByTestId('correlation-id')).toContainText(failureCorrelationId);

    await page.getByTestId('tab-diagnosis').click();
    const diagnosis = page.getByTestId('diagnosis-card').first();
    await expect(diagnosis).toContainText('Violação de unicidade no banco de dados');
    await expect(diagnosis).toContainText('SQLState 23505');
    await expect(diagnosis).toContainText('Confiança ALTA');

    await page.getByTestId('tab-exception').click();
    await expect(page.getByTestId('root-cause')).toBeVisible();
    await expect(page.getByTestId('exception-tab')).toContainText('org.postgresql.util.PSQLException');
  });

  test('consulta a timeline instrumentada e os logs correlacionados', async ({ page }) => {
    await login(page);
    await page.goto(`/investigation?correlationId=${failureCorrelationId}`);
    await expect(page.getByTestId('investigation-summary')).toBeVisible();

    await page.getByTestId('tab-timeline').click();
    const steps = page.getByTestId('timeline-steps');
    await expect(steps).toContainText('Spring Security');
    await expect(steps).toContainText('CustomerService.create');
    await expect(steps).toContainText('PostgreSQL · INSERT customers');
    await expect(steps).toContainText('PostgreSQL · ROLLBACK');
    await expect(steps).toContainText('GlobalExceptionHandler.handleIntegrity');
    await expect(page.getByTestId('timeline-flow')).toContainText('sem instrumentação');

    await page.getByTestId('tab-logs').click();
    await expect(page.getByTestId('log-table')).toContainText('Violação de integridade no banco');
  });

  test('compara execução de sucesso com execução de falha', async ({ page }) => {
    await login(page);
    await page.goto('/scenarios');
    await page.getByTestId('compare-A02').click();
    await page.waitForURL('**/comparator?**');

    await expect(page.getByTestId('status-A')).toHaveText('201 Created');
    await expect(page.getByTestId('status-B')).toHaveText('409 Conflict');
    const divergence = page.getByTestId('first-divergence');
    await expect(divergence).toContainText('PostgreSQL');
    await expect(divergence).toContainText('INSERT customers');
    await expect(page.getByTestId('aligned-steps')).toContainText('ROLLBACK');
  });

  test('consulta o histórico persistido e filtra por correlationId', async ({ page }) => {
    await login(page);
    await page.getByTestId('nav-history').click();
    await page.getByTestId('filter-q').fill(failureCorrelationId);
    await page.getByTestId('history-search').click();

    const table = page.getByTestId('history-table');
    await expect(table.locator('tbody tr')).toHaveCount(1);
    await expect(table).toContainText('D01 – Registro duplicado');
    await expect(table).toContainText('409 Conflict');
    await expect(page.getByTestId('history-total')).toContainText('1 execução');
  });

  test('analisa um stack trace de exemplo', async ({ page }) => {
    await login(page);
    await page.getByTestId('nav-stacktrace').click();
    await page.getByTestId('stack-example').selectOption('st-chained-suppressed');
    await expect(page.getByTestId('primary-exception')).toHaveText('InvoiceGenerationException');
    await expect(page.getByTestId('root-cause-name')).toHaveText('NumberFormatException');
    await expect(page.getByTestId('stack-exception')).toHaveCount(4);
  });

  test('viewer não pode executar cenários', async ({ page }) => {
    await login(page, 'viewer', 'viewer-lab');
    await page.goto('/scenarios');
    await expect(page.getByTestId('run-A01')).toBeDisabled();
    await expect(page.getByTestId('nav-history')).toHaveCount(0);
  });
});

test('Guided Tour executa a demonstração completa com funcionalidades reais', async ({ page }) => {
  await login(page);
  await page.getByTestId('nav-tour').click();
  await page.getByTestId('tour-start').click();
  const panel = page.getByTestId('tour-panel');
  await expect(panel).toBeVisible();

  const action = page.getByTestId('tour-action');
  await action.click(); // 1. A02 (sucesso)
  await expect(page.getByTestId('investigation-status')).toHaveText('201 Created');
  await action.click(); // 2. D01 (falha)
  await expect(page.getByTestId('investigation-status')).toHaveText('409 Conflict');
  await action.click(); // 3. diagnóstico
  await expect(page.getByTestId('diagnosis-tab')).toContainText('Violação de unicidade');
  await action.click(); // 4. causa provável
  await expect(page.getByTestId('root-cause')).toBeVisible();
  await action.click(); // 5. logs
  await expect(page.getByTestId('log-table')).toBeVisible();
  await action.click(); // 6. comparação
  await expect(page.getByTestId('first-divergence')).toContainText('INSERT customers');
  await action.click(); // 7. rastreabilidade
  await expect(page.getByTestId('timeline-steps')).toContainText('PostgreSQL · ROLLBACK');
  await expect(panel).toContainText('passo 7 de 7');
});
