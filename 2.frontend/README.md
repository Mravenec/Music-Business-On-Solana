# 2.frontend

Day zero: **no** module folder yet. Set `module_name` in `.ai/project.yaml`, then scaffold:

```text
2.frontend/<module_name>/
```

Never create a permanent `appProject` directory.

Agents scaffold **after GATE_HTTP** (backend `.http` green). The UI must reach Spring from the Vite origin (CORS on the API and/or a Vite proxy). Do not set `build_ok` if Health shows `Network Error`. `.ai/rules/frontend-api-reach.md`.

## Order

```
services/ → hooks/ → pages/
```

## Node / Vite floor

- Prefer **Node 20.19+** (or 22.12+) if using latest `create-vite`.
- On older Node 20.x (e.g. 20.11), **do not** use latest `create-vite` (`styleText` / engine errors). Hand-scaffold **Vite 5** + React 18, or pin `create-vite@5.x`.

Do not call raw HTTP helpers from pages. See root `AGENTS.md`. Rule: `.ai/rules/module-name.md`. Product pages: one job per screen, drill-down on click (`.ai/rules/progressive-disclosure.md`).

## Diagram (Mermaid)

Keep **`3.diagrams/2.frontend/`** in sync with this tree (same checklist item). Do not put `.mmd` here. `.ai/rules/diagrams.md`.
