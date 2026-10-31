# ErrorLens — Frontend

Angular 22 (standalone, Signals, zoneless). Veja o [README principal](../README.md).

```bash
npm ci
npm start          # http://localhost:4200 (proxy /api -> http://localhost:8080)
npm test           # testes unitários (Vitest)
npm run build      # build de produção em dist/errorlens-frontend/browser
npm run e2e        # Playwright contra E2E_BASE_URL (padrão http://localhost:4200)
```

Estrutura: `src/app/core` (API, autenticação, interceptors, tema, tour), `src/app/layout` (shell),
`src/app/shared` (componentes reutilizáveis), `src/app/features` (telas com lazy loading), `e2e/` (Playwright).
