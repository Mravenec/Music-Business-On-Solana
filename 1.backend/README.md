# 1.backend

Day zero: **no** module folder yet. Set `module_name` in `.ai/project.yaml`, then scaffold a **[start.spring.io](https://start.spring.io/)** Maven project into:

```text
1.backend/<module_name>/
```

That folder **is** the Initializr unzip (`pom.xml` lives here). Never create a permanent `appProject` directory.

Settings: Maven · Java **21** · Spring Boot **3.5.x** · Jar. Dependencies: **web**, **jooq**, **mariadb**, **jdbc**. No JPA. Add **CORS** for Vite `5173`/`4173` with the first controller (`.ai/rules/frontend-api-reach.md`).

Full tree, `.http` path, and Javadoc: `.ai/rules/spring-initializr.md`.

## Mandatory layer order

```
JOOQ POJOs (generated …database.jooq)
  → I*Repository → *Repository
  → I*Service → *Service
  → I*Controller → *Controller
     (English /** */ on every public type and method)
  → STOP old spring-boot:run (if any) → START fresh
  → src/test/java/<package>/http/*.http          ← GATE_HTTP for frontend
     (if not 2xx: mvn test / unit tests → fix → restart → retry .http)
```

Inject **interfaces** across layers. **No Java DTOs** — use generated JOOQ POJOs only (`.ai/rules/jooq-pojos-no-dto.md`).

`.http` files do **not** sit next to `pom.xml`. They sit under `src/test/java/<package>/http/` (same as the example apps).

Before proving `.http`: see `.ai/rules/backend-http-gate.md`. JOOQ: `.ai/rules/backend-jooq.md`. No DTOs: `.ai/rules/jooq-pojos-no-dto.md`.

See `project-commands.txt` and root `AGENTS.md`. Rule: `.ai/rules/module-name.md`.

## Diagram (PlantUML)

Keep **`3.diagrams/1.backend/`** in sync with this tree (same checklist item). Do not put `.puml` here. `.ai/rules/diagrams.md`.
