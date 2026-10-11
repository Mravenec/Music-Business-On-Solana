# 3.diagrams / 1.backend — PlantUML

PlantUML of the **real** Spring Boot + JOOQ layout (not a second copy of Java).

Day zero: [`layers.puml`](./layers.puml) is the neutral layer contract (`JOOQ → IREPO → REPO → ISVC → SVC → ICTRL → CTRL`). After `module_name` exists, keep this file (and extra `.puml` files if needed) in sync with `1.backend/<module_name>/`.

## Preview

1. Copy all of `layers.puml`
2. Paste into [PlantUML Web Server](https://www.plantuml.com/plantuml/uml) or a PlantUML editor extension
3. Confirm packages match the live backend folders

Do **not** invent controllers or services that are not in the Java tree. Do **not** put `.puml` under `1.backend/` itself.

Skill: `.ai/skills/diagrams/SKILL.md` · Rule: `.ai/rules/diagrams.md`
