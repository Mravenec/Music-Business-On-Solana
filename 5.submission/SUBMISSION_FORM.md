# Colosseum submission form — answers

Every field the portal asks for ([colosseum.com/hackathon](https://colosseum.com/hackathon) FAQ, "What is the required information in the submission portal?"). Product and technical answers are written from the code on `main`. Fields marked **FOUNDER:** can only be answered by you; a draft from the product manual is given where it helps. Do not submit a draft line you cannot stand behind.

## Product name

EH8S — Enigma H8 Studios

## One-line description

The music studio network on Solana: musicians learn, form bands, play venues and get paid in USDC the night they play.

## Brief description (≈ 120 words)

EH8S connects the four things a working musician needs — an academy, a band, venues and fair payouts — in one system settled on Solana. Students pay tuition in USDC and the program sends 15% straight to their instructor. Venues book bands from a live Stage Map; the show's gross sits in an on-chain escrow with the contract's SHA-256 pinned to it. After the show one signature pays the venue's expenses, takes a 15% fee into a program-owned treasury and splits the rest among band members by a performance score (SPP). Songs have on-chain royalty splits, and sync licenses pay artists 80% automatically. AI agents score practice (NEXUS), match musicians to bands (HARMONY) and plan tours (ATLAS); the owner approves their decisions from Slack. The tuition also opens a video academy: the studio publishes courses, and musicians who reach Enigma level 5 earn the right to teach, with every course reviewed by the studio before students see it.

## Blockchains and tools integrated

- **Solana DevNet** — custom Anchor program `GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG` (v0.10.0, 27 instructions): academy and zone subscriptions with on-chain expiry, band vaults with SPP weights, venue listings, booking escrow (`VenueAccessToken`), concert settlement, member claims, per-song royalty pools, sync licensing, treasury PDA, agent authority PDAs, native owner multisig.
- **USDC** (Circle DevNet mint `4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU`) via SPL Token.
- **Phantom / Solflare** through `@solana/wallet-adapter`; `@solana/web3.js` transaction builders.
- **Backend verification** — Spring Boot decodes every submitted signature from Solana RPC and checks program id, instruction, signer, accounts and amounts before recording it.
- **Stack** — MariaDB · Spring Boot 3.5 + JOOQ · React + Vite · Anchor/Rust with `solana-program-test` (58 program tests) · 159 backend unit tests.
- **AI** — Anthropic Claude (NEXUS Score Enigma, owner digest, optional HARMONY rationale).
- **Slack** — signed Block Kit Approve / Reject buttons for agent decisions.

## Team (every member needs a Colosseum account; the leader adds them)

**FOUNDER:** name, role, background, previous experience, links (X / LinkedIn / GitHub `Mravenec`). Judges look for founder–market fit: why *you* understand musicians, studios or venues. Say what you have done in music or education, and what you have shipped before.

## Location

**FOUNDER:** city and country. (The manual's first markets are Mexico and Latin America, with Colombia, Argentina, Brazil, Peru and Spain named; say where the first studio really is.)

## Logo

`5.submission/logo.png` · `5.submission/logo.svg`. The original EH monogram: white strokes, open parts left clear so they take the background. In the app those openings sit on navy `#0b1f3a`.

## GitHub repository

`https://github.com/Mravenec/eh8s_harness` — **private today**. Either make it public, or in GitHub → Settings → Collaborators add `hackathon@colosseum.com` with read access (Colosseum allows private repos with that access). Branch to judge: `main` (tag `colosseum-worldsfair-2026` after the final refresh).

## Presentation video (2–3 min)

Script: `5.submission/PITCH_VIDEO_SCRIPT.md`. Paste the unlisted YouTube link here.

## Product demo video (≤ 3 min)

Script: `5.submission/DEMO_VIDEO_SCRIPT.md`. Paste the unlisted YouTube link here.

## Go-to-market strategy

Draft from the manual — **FOUNDER: replace the bracketed parts with your real plan.**

1. **Start with one studio and its students** in [first city]. The academy is the entry point: level 0 is a free one-week orientation, then plans at $30 / $55 / $90 per month.
2. **Free lessons as the funnel.** One or two free lessons a week on YouTube lead to the paid academy. The academy video library (in the app) keeps paying students engaged between classes.
3. **Bands and venues follow students.** From level 3 students can join bands; bands play [first venues]; every show settles on-chain and pays members instantly — the proof we show the next venue.
4. **Tour passes grow reach.** Geographic subscriptions ($5 local → $99 global) let bands book outside their city; ATLAS plans the route.
5. **Channel last.** The manual's rule: no channel launch before 100 active students and 4 bands — ecosystem first, audience second.

## Demand validation

**FOUNDER:** real evidence only — conversations with musicians, instructors or venues; a waitlist; students already paying you off-chain; letters of intent from venues. If you have none yet, say what you will validate first (e.g. "10 venue interviews in October") rather than inventing numbers.

## Distribution plan

- YouTube free lessons (funnel) → academy subscriptions.
- Instructors bring their students (they earn 15% of each student's plan automatically).
- Venues bring audiences; every settled show is a public, auditable payout story.
- Level-5 musicians with an instructor role draft video courses in the app; the studio reviews and publishes them (teaching path, epic 51).
- **FOUNDER:** partners, communities, schools or scenes you already have access to.

## Market size

**FOUNDER:** add sourced figures (the manual cites IFPI Global Music Report 2026, IMS Business Report 2026 and MIDiA Research 2026 — take the numbers from those reports, with links). Bottom-up plan from the manual: Year 1 ≈ $53.7K revenue (40 Basic + 30 Band + 10 Pro students, 15 zone passes, 18 concerts); Year 3 $280K+ (400 musicians, 18 bands, 120 zone passes).

## Business model / viability

| Revenue line | Price / take |
|---|---|
| Academy plans | $30 / $55 / $90 per month (85% treasury, 15% instructor) |
| Concert settlement | 15% of net (gross − expenses) |
| Sync licensing | 20% of each license |
| Geographic tour passes | $5 / $15 / $35 / $65 / $99 per month |
| Channel (later) | livestream/VOD 30%, masterclasses 40%, podcast 50% |

On-chain settlement removes processor fees (Stripe 2.9% + $0.30) and chargebacks (1–3%); the treasury is a program-owned account anyone can audit.

## Traction

**FOUNDER:** honest current state. What is true from the repo: the full money loop runs on Solana DevNet with faucet USDC, verified by a 25-check preflight; no Mainnet users or revenue yet.

## Prior development disclosure (required)

Paste `4.anchor/eh8s-devnet/COLOSSEUM_DISCLOSURE.md` (one-line disclosure + master split + timeline answers). Colosseum judges only work done 14 Sep – 12 Oct 2026 and can disqualify teams that do not disclose earlier work.
