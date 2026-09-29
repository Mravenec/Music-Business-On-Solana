# 3.diagrams

Visual twins of the product layers, kept in sync with the code.

| File | Tool | Shows |
|---|---|---|
| [`0.database/dbdiagram/schema.dbml`](0.database/dbdiagram/schema.dbml) | [dbdiagram.io](https://dbdiagram.io) | Every table, key and cross-schema foreign key in `0.database/canonical_sql/` |
| [`1.backend/layers.puml`](1.backend/layers.puml) | [PlantUML](https://www.plantuml.com/plantuml/uml) | Spring Boot layers: JOOQ → repositories → services → controllers |
| [`2.frontend/layers.mmd`](2.frontend/layers.mmd) | [Mermaid](https://mermaid.live) | React layers: services → hooks → pages |
| [`2.frontend/hackathon-money-loop.mmd`](2.frontend/hackathon-money-loop.mmd) | Mermaid | The closed USDC loop: tuition, escrow, settlement, claims, treasury |
| [`2.frontend/catalog-onchain.mmd`](2.frontend/catalog-onchain.mmd) | Mermaid | Per-song royalty pools and sync licenses |

To view one, paste the file into the linked editor.
