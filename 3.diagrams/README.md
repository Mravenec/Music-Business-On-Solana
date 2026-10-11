# 3.diagrams

Visual twins of the three product layers. **One location per tool — no copies under `0.database/`, `1.backend/`, or `2.frontend/`.**

| Folder | Tool | Source of truth it mirrors |
|---|---|---|
| `0.database/dbdiagram/` | [dbdiagram.io](https://dbdiagram.io) (DBML) | `0.database/canonical_sql/` |
| `1.backend/` | PlantUML (`.puml`) | Real Spring Boot + JOOQ tree under `1.backend/<module_name>/` |
| `2.frontend/` | Mermaid (`.mmd`) | Real React tree under `2.frontend/<module_name>/` |

Day zero: DBML is an empty stub; PlantUML and Mermaid show the **neutral layer shape** (no product domain). When agents add SQL or module code, they update the matching diagram in the **same** checklist item.

Rules: `.ai/rules/diagrams.md` · `.ai/rules/dbdiagram.md`  
Skills: `.ai/skills/diagrams/SKILL.md` · `.ai/skills/dbdiagram/SKILL.md`
