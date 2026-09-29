# 2.frontend — React app

`eh8s/` is React 18 · Vite 5 · TypeScript · Solana wallet adapter (Phantom, Solflare) · Leaflet (Stage Map).

```text
src/services/     API and on-chain transaction builders (the only code that does I/O)
src/hooks/        state and actions for each feature, built on services
src/pages/        one job per screen; pages call hooks, never services
src/components/   app shell, wallet providers, connect button
```

```bash
cd 2.frontend/eh8s
npm install
npm run dev        # http://127.0.0.1:5173, proxies /api and /health to the backend on :8080
npm run build      # type-check + production build
```

Connecting a wallet asks it to sign `Sign in to EH8S`; the session then unlocks the screens for that wallet's approved roles (owner, student, musician, instructor, venue). Every payment opens the wallet to sign the transaction.

Diagrams: [`3.diagrams/2.frontend/`](../3.diagrams/2.frontend/) (layers, money loop, catalog on-chain).
