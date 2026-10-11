# Weekly updates — one-minute scripts

Colosseum: "Each update should be a concise, one-minute video highlighting progress and notable challenges from the previous week." Not required, strongly recommended. Record face + screen (Loom works), post in the hackathon dashboard.

Format for every update (≈ 150 spoken words): **shipped** (3 bullets, show one on screen) → **one hard problem and how we solved it** → **next week**.

## Week of 14–20 Sep

- Shipped: JWT login for every protected API (Health stays public), progressive-disclosure UI (one job per screen), stricter backend rules (JOOQ POJOs only).
- Challenge: the app had grown kitchen-sink pages; we split them into small pages so a first-time musician always sees one clear action.
- Next: a no-mock judge path — every payment must be a real DevNet signature.

## Week of 21–27 Sep

- Shipped: the full money loop on DevNet — tuition split 85/15, concert settle with expenses and a 15% fee, band vault split by performance score, member claims, program treasury with owner withdraw. Then venue escrow with a pinned contract hash, native owner multisig, subscriptions with on-chain expiry, per-song royalties and sync licensing, live AI agents (NEXUS, HARMONY, ATLAS) and Slack approvals. Last, the academy video library (owner-published courses, progress, server-side access) and an earned teaching path: only level-5 musicians with an instructor role draft courses, and the studio approves each one. Program went v0.6.0 → v0.10.0. We closed the week by removing every Java DTO: generated JOOQ POJOs now flow from the database through each Spring layer to the React services. 159 backend tests, 25-check preflight.
- Challenge: security review of our own money path — accounts were unchecked, so anyone could pass a fake vault. We pinned every account to the on-chain config and made the backend decode and verify each signature before recording it.
- Next: record the demo and pitch videos and publish the first real courses.

## Week of 28 Sep – 4 Oct

- Shipped (fill in): demo and pitch videos; first courses recorded and published in the academy.
- Challenge (fill in).
- Next (fill in): first students and instructors on the platform.

## Week of 5–11 Oct

- Shipped (fill in).
- Challenge (fill in).
- Next: submit before 12 Oct; first real students (fill in).
