# Product demo video — script (max 3:00)

Colosseum asks for "a product-demo video of no more than three minutes explaining how the product works". Judges will not run the stack, so this video **is** the product for them. Show real DevNet signatures, not slides.

**Rules for this cut**

- Everything on screen is real: Phantom pop-ups, confirmed signatures, Solana Explorer on `?cluster=devnet`.
- Cut every wait (wallet confirm, RPC). Speed up typing 2×. Never fake a balance or a transaction.
- One sentence of voice-over per shot. Say the money, not the tech: "85% goes to the studio treasury, 15% straight to the instructor."
- 1080p, browser zoom 110%, hide bookmarks, dark Phantom theme, mouse highlight on.

## Before recording (one hour, once)

1. Stack up from `main`: `python 0.database/scripts/setup_db.py` · `mvn spring-boot:run` · `npm run dev`. Run `hackathon-smoke.ps1 -Preflight` and keep the "Preflight PASS" line for the first shot.
2. Three browser profiles, each with its own Phantom on DevNet: **A Owner** (`7QVKJ…DUuC`), **B Musician**, **C Venue**. Fund SOL and Circle DevNet USDC (B and C).
3. Walk steps 1–7 of `4.anchor/eh8s-devnet/HACKATHON_SMOKE.md` off camera (apply, approve, enroll, band, vault) so the takes below start warm. Record one take per shot; stitch in the editor.
4. Set `ANTHROPIC_API_KEY` in the backend shell (env only) so NEXUS and the digest answer live.

## Shot list

| Time | Screen | Action | Voice-over |
|---|---|---|---|
| 0:00–0:12 | Terminal → `http://127.0.0.1:5173` | "Preflight PASS (25 checks)", then the app header "Studio online" | "EH8S is a music studio network on Solana. Every dollar in this demo is real DevNet USDC, signed in Phantom." |
| 0:12–0:35 | **B Musician** → `/academy/plans/:id` → **Pay with wallet** → Explorer | Phantom approve; Explorer shows two transfers | "A student pays tuition. The program splits it on-chain: 85% to the studio treasury, 15% straight to the instructor. No invoice, no waiting." |
| 0:35–0:50 | `/academy/nexus` → **Request Score Enigma** → result | Score 0–100, strengths, exercises, recommended level | "Our pedagogical agent, NEXUS, scores practice every week. Only NEXUS or the owner can change a musician's level on-chain." |
| 0:50–1:20 | **C Venue** → `/stage-map` pin → **Request slot**; venue page → **Escrow with wallet**; **A Owner** → **Confirm and pin contract** | Pins colored by availability; gross goes into escrow; contract SHA-256 pinned | "Venues book bands from the Stage Map. The show's gross sits in an on-chain escrow, and the contract's fingerprint is pinned to the booking." |
| 1:20–1:45 | **A Owner** → Inbox → **Escrows to settle** → **Settle from escrow** → Explorer | Preview: gross · expenses · net · 15% fee; three transfers | "After the show one signature settles it: expenses back to the venue, 15% to the studio, and the rest into the band vault — split by each member's performance score." |
| 1:45–2:00 | **B Musician** → `/stage-map/claim` → Explorer | Claim lands in the member's own wallet | "Each musician claims their own share. Nobody can move another member's money." |
| 2:00–2:15 | Catalog track → **Deposit royalties**; **Sync licenses** → **Pay with wallet** | Members credited by song split; license 20/80 | "Streaming royalties are split per song, and a sync license sends 80% to the artists automatically." |
| 2:15–2:30 | **A Owner** → `/owner/treasury` → **Withdraw to my wallet**; `/owner/digest` | Treasury balance → owner ATA; Claude digest | "The studio's income lands in a program-owned treasury. The owner withdraws with a signature and gets an AI summary of the day." |
| 2:30–2:45 | `/owner/decisions` → **Slack approvals** · Slack Approve click | "Approved in Slack by …" | "Twelve AI agents report to the owner. When one needs a human decision, the owner answers from Slack — once, with a verified signature." |
| 2:45–2:55 | **B Musician** → `/academy/courses` → a lesson playing → **Mark complete and continue**; cut to **A Owner** → `/owner/courses/review` → **Approve and publish** | Course percentage rises; an instructor's course leaves the review queue | "The tuition buys a video library. Musicians who reach level 5 can teach — and the studio approves every course before students see it." |
| 2:55–3:00 | Logo `5.submission/logo.png` + program id | — | "EH8S. Learn, play, get paid — on Solana." |

Before the 2:45 take, publish one course with a free preview lesson (smoke step 18) and have one level-5 instructor course waiting in the review queue (smoke step 19).

## Explorer checks to keep in frame

- Program: `GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG`
- Treasury USDC ATA: `9ptwWxWj5YQKP3pD5b4UEDksARBzkSQPtS3Kyvjnff4Z` · Vault USDC ATA: `U9mzuiHMVUsCw9BLKGibq55BPZuLaxipnvKFvn76Vjq`
- USDC mint (Circle DevNet): `4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU`

## Upload

Unlisted YouTube (or Loom) link, title "EH8S — product demo (Solana DevNet)", captions on. Paste the link in the form field "Product demo video".
