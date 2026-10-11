# 3.diagrams / 2.frontend — Mermaid

Mermaid of the **real** React app (`services → hooks → pages`). Not a second copy of JS/TSX.

Day zero: [`layers.mmd`](./layers.mmd) is the frontend contract. Judge money loop: [`hackathon-money-loop.mmd`](./hackathon-money-loop.mmd) — closed cycle: venue settle fee→owner + pool→vault → musician claim (no mocks). After `module_name` exists, keep these files in sync with `2.frontend/<module_name>/`.

## Preview

1. Copy all of `layers.mmd`
2. Paste into [Mermaid Live Editor](https://mermaid.live) (or a Markdown preview that renders Mermaid)
3. Confirm boxes match the live React folders

Do **not** invent pages or hooks that are not in the frontend tree. Do **not** put `.mmd` under `2.frontend/` itself. Frontend work still waits **GATE_HTTP**.

Skill: `.ai/skills/diagrams/SKILL.md` · Rule: `.ai/rules/diagrams.md`
