# EH8S DevNet Anchor program

Program id (the program keypair is kept outside the repo; upgrades only need this address plus the upgrade-authority wallet):

`GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG` — **live on DevNet at v0.10.0** (upgradeable; first deployed at slot 504604312; v0.10.0 at slot 504863281).

```text
solana program show GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG --url devnet
```

| On-chain account | Address (DevNet) |
|---|---|
| `Eh8sConfig` PDA `["eh8s","config"]` | `GgpAKj9FZppC8gckF6SzevirNbgkfhpEbqiz1GE6oKLF` |
| Owner | `7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC` |
| Treasury PDA `["treasury"]` (v0.5.0) | `DxGjk32dH8Jh5Xodx2syERsZJ6RPPaULxKsNmFmVWYWY` |
| Treasury USDC ATA (`config.treasury_usdc` after `init_treasury`) | `9ptwWxWj5YQKP3pD5b4UEDksARBzkSQPtS3Kyvjnff4Z` |
| Previous owner treasury ATA (v0.3–v0.4, still the owner's) | `CoP8dydvHXTDUoxxB6tAQk6yu3Q6YRkqNj8SY3vtR53o` |
| Vault USDC ATA (PDA `["vault"]`) | `U9mzuiHMVUsCw9BLKGibq55BPZuLaxipnvKFvn76Vjq` |
| Protocol fee | 1500 bps |
| Governance PDA `["governance"]` (v0.8.0; not initialized, so direct owner paths stay open) | `3ZH4igyjF1jFPhyRTAczh1Za8bgjaTfXe2cB9v2v49XY` |

## Instructions

- `initialize_config(owner, protocol_fee_bps)` — upgrade authority only; pins USDC mint, owner treasury ATA, vault ATA
- `init_treasury()` — upgrade authority only, idempotent; creates the `["treasury"]` PDA USDC ATA and points `config.treasury_usdc` at it (config layout unchanged)
- `withdraw_treasury(amount)` — `config.owner` only; treasury PDA signs treasury ATA → owner USDC ATA (mint = config mint, token owner = config owner); `InsufficientTreasury` when amount > balance; trailing `["governance"]` account (v0.8.0) must still be empty, otherwise `GovernanceActive`
- `authorize_agent(agent_wallet, permissions)` (v0.6.0) — `config.owner` only; creates/updates `AgentAuthority` PDA `["agent", agent_wallet]`; bits `0x01` pedagogical (NEXUS) · `0x02` HARMONY · `0x04` STAGE · `0x08` VAULT · `0x10` WAVE; `0` revokes; unknown bits → `InvalidPermissions`; trailing `["governance"]` account (v0.8.0) must still be empty, otherwise `GovernanceActive`
- `init_governance(signers, threshold)` (v0.8.0) — `config.owner`, once; `Governance` PDA `["governance"]` with 1..5 distinct signers and 1 ≤ threshold ≤ signers (`InvalidGovernance`); from then on the two direct paths above are refused
- `propose(kind, amount, target, permissions, new_signers, new_threshold)` (v0.8.0) — a governance signer (`NotGovernanceSigner`); `Proposal` PDA `["proposal", id LE]` with `id = governance.proposal_count`; the proposer's approval is recorded. Kinds: `0` withdraw (`amount` > 0, `target` = destination USDC account), `1` authorize agent (`target` = agent wallet, `permissions`), `2` update signers (`new_signers`, `new_threshold`)
- `approve_proposal()` (v0.8.0) — each current signer once (`AlreadyApproved`); not after execute (`ProposalExecuted`) or a signer-set change (`StaleProposal`)
- `execute_withdraw_proposal()` / `execute_agent_proposal()` / `execute_signers_proposal()` (v0.8.0) — anyone, once, when approvals from current signers ≥ threshold (`ThresholdNotMet`); the kind must match the instruction (`InvalidProposal`). Withdraw destination must be the proposal target, a config-mint USDC account owned by a governance signer (`DestinationNotSigner`). A signer-set change bumps `governance.epoch`, so older open proposals go stale
- `update_musician_level(new_level)` (v0.6.0) — `config.owner` or an agent with `0x01` (pass its `AgentAuthority` PDA); level 0..5 (`InvalidLevel`); the musician cannot set their own level
- `upsert_musician_profile(instrument, country[3])` — the musician's own profile; no level parameter (new profiles start at 0); keeps level and pending claims on refresh; country = uppercase ISO-3166 alpha-3 (`InvalidCountry`); grows v0.5 (51-byte) profiles to the new layout in place
- `subscribe_academy(plan_type, months)` (v0.9.0) — plan `1` Basic $30 · `2` Band $55 · `3` Pro $90 per month, months 1..12 (`InvalidPlan`); amount = price × months computed on-chain; USDC 85% treasury / 15% instructor; `AcademySubscription` PDA `["academy_sub", wallet]` stores plan, months paid, `started_at`, `expires_at`; renewal extends from max(now, expires_at). Pre-v0.9.0 accounts at `["academy", wallet]` are left untouched
- `create_band(band_id, members)` — owner or a `0x02` HARMONY agent (optional trailing `AgentAuthority` account); BandVault PDA `["band", band_id LE]` with 1..8 member wallets, equal weights (dust to the last member)
- `update_spp_weights(weights_bps)` — owner or a `0x02` HARMONY agent; one weight per member, sum = 10000
- `register_venue(venue_id, name, country[3], capacity, contract_type)` (v0.7.0) — venue wallet signs; `VenueListing` PDA `["venue", venue_wallet, venue_id LE]`, status pending; name 1..64 bytes, capacity > 0, contract type 1..4 (fixed / door / versus / promoter) → `InvalidVenue`; country → `InvalidCountry`
- `approve_venue()` (v0.7.0) — owner or a `0x04` STAGE agent; status approved (unapproved listings cannot book → `VenueNotApproved`)
- `propose_booking(concert_id, band_id, date_ymd, gross)` (v0.7.0) — approved venue signs; creates `VenueAccessToken` PDA `["access", venue_wallet, band_id LE, date_ymd LE]` and escrow token account `["escrow", access_token]` (authority = the token PDA), then moves `gross` venue → escrow; the BandVault must exist; date `yyyymmdd` (`InvalidBooking`)
- `confirm_booking(contract_hash[32])` (v0.7.0) — owner or STAGE agent; stores the SHA-256 of the backend contract text (zero → `InvalidBooking`); proposed → confirmed
- `cancel_booking()` (v0.7.0) — the booking venue, only while proposed (`InvalidBookingStatus`); refunds the escrow and closes escrow + token back to the venue
- `settle_booking(gross, expenses)` (v0.7.0) — owner or a `0x08` VAULT agent; needs a confirmed token; `gross` must equal the booked gross still in escrow (`EscrowMismatch`); expenses → venue, fee = net × config bps → treasury, pool → vault, member pending += pool × weight (remaining accounts = member profiles in vault order); writes `["concert", venue, concert_id]`; runs once (`AlreadySettled`)
- `settle_concert(concert_id, gross, expenses)` — legacy venue-funded settle kept wire-compatible for older clients: net = gross − expenses (expenses stay with the venue); fee = net × config bps → treasury; pool → vault; each member pending += pool × weight (remaining accounts = member profiles in vault order); one settlement per `["concert", venue, concert_id]`
- `claim_royalties(amount)` — vault PDA signs vault → musician
- `create_royalty_pool(track_id, members, splits_bps)` (v0.10.0) — owner or a `0x10` WAVE agent; `RoyaltyPool` PDA `["royalty", track_id LE]` with 1..8 distinct member wallets (`InvalidMembers`) and one non-zero split per member summing to 10000 (`InvalidWeights`); once per song
- `deposit_royalties(track_id, amount)` (v0.10.0) — owner or a WAVE agent; depositor USDC → vault; each member pending += amount × split, dust to the last member (remaining accounts = member profiles in pool order, `MemberMismatch`); pool `total_usdc` grows. Replaces the pre-v0.10.0 single-musician deposit into `["royalty","pool"]` (credits already in profiles stay claimable)
- `pay_sync_license(track_id, deal_id, amount)` (v0.10.0) — licensee signs; 20% → treasury, 80% (plus dust) → vault credited through the song's splits; `SyncLicense` PDA `["sync", track_id LE, deal_id LE]` makes each deal payable once; pool `sync_total_usdc` grows
- `subscribe_geographic(geo_code, tier, months)` (v0.9.0) — tier `1` Local $5 · `2` City $15 · `3` Country $35 · `4` Regional $65 · `5` Global $99 per month, months 1..12, geo code 2..24 of `A-Z 0-9 _` (`InvalidGeoSubscription`); USDC 100% → treasury; `GeographicSubscription` PDA `["geo_sub", wallet, geo_code]` with tier and `expires_at` (renewal as academy)

Every token account is constrained: mint = config USDC mint, treasury = config treasury ATA, vault = config vault ATA, musician profile = `["musician", authority]` PDA.

## Build (WSL Ubuntu, Solana 2.0 CLI, platform-tools v1.41)

The Windows toolchain cannot unpack platform-tools without symlink privilege; build inside WSL. `Cargo.lock` pins crates that still support Rust 1.75 (for example `blake3 1.5.5`).

```bash
cd 4.anchor/eh8s-devnet
cargo-build-sbf --manifest-path programs/eh8s_devnet/Cargo.toml --sbf-out-dir target/deploy
```

## Tests (solana-program-test, loads the built `.so`)

```bash
cd 4.anchor/eh8s-devnet/tests-program
SBF_OUT_DIR=$PWD/../target/deploy cargo test
```

Scenarios: spec SPP example (875 / 200 / 15% / 38-35.2-20-6.8 → 218.025 / 201.96 / 114.75 / 39.015), owner-only band + weights, weight sum / member count, member order guard, expenses ≥ gross, upgrade-authority-only init, fee from config bps, double-settle guard, foreign treasury / vault / mint rejected, non-profile account rejected, pending kept on upsert, claim amount + signer guards, deposit vault guard, per-song pool splits (sum / zero split / duplicate / 9 members / stranger / once per song), deposit credits by split with dust to the last member (wrong order / missing member rejected), only owner or WAVE deposits (HARMONY / stranger / revoked WAVE rejected), sync license 80/20 with dust, paid once per deal, treasury redirect rejected, academy 85/15 + redirect guards, geographic treasury guard, `init_treasury` upgrade-authority gate + repoint, fees landing in the treasury PDA ATA (old owner ATA rejected), owner withdraw, non-owner / foreign destination / over-withdraw / zero / pre-init withdraw rejected, `authorize_agent` owner-only + unknown bits + revoke, musician cannot self-level, NEXUS sets level / HARMONY cannot / level > 5 / revoked agent / borrowed agent PDA rejected, owner keeps every agent power, HARMONY agent creates band + updates weights (NEXUS rejected), country validation, v0.5 profile migration keeps level + pending, venue listing pending → only owner / STAGE approve (HARMONY rejected), bad name / capacity / contract type / country, unapproved venue cannot book, booking needs a BandVault, escrow holds the gross, STAGE confirms with a non-zero hash (double confirm rejected), VAULT agent settles from escrow (expenses / fee / pool / member split exact, escrow emptied, token settled, replay → `AlreadySettled`), settle before confirm / STAGE or venue signer / gross mismatch / expenses > gross / expenses = gross rejected, venue cancels before confirm (full refund, accounts closed, stranger rejected) and cannot cancel after confirm, governance init owner-only / once / bad signer sets, direct withdraw + authorize refused after governance, withdraw proposal threshold not met → approve → execute (double execute rejected), non-signer propose / approve and double approve rejected, destination not a signer's USDC account rejected, agent proposal grants powers, signer-set update (3-of-3 → 2-of-4) makes older proposals stale.

Keep the default release `opt-level`: `"s"` / `"z"` shrink the `.so` ~12% but crash booking instructions at runtime (access violation) on platform-tools v1.41.

## Deploy / upgrade + one-time config

When the new `.so` is larger than the program data account, extend it first (v0.7.0: 600000 → 750000 bytes; v0.8.0: 750000 → 830000 bytes, slot 504844157; v0.10.0: 830000 → 900000 bytes, slot 504863281):

```bash
solana program extend GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG 150000 --url devnet
# upgrade in place: the default keypair (~/.config/solana/id.json) must be the upgrade authority
solana program deploy target/deploy/eh8s_devnet.so --program-id GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG --url devnet
cd tests-program
cargo run --example init_config -- ~/.config/solana/id.json 7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC 4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU 1500
cargo run --example init_treasury -- ~/.config/solana/id.json
```

`init_config` and `init_treasury` are idempotent (they print the existing config). MariaDB `chain_config` carries the program id, instruction registry, and `program_deployed_at` (`0.database/canonical_sql/eh8s_onchain/eh8s_onchain.sql`).

DevNet USDC mint used by the stack: `4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU`.

## Judge smoke (three wallets, no mocks)

```powershell
.\hackathon-smoke.ps1 -Preflight   # 25 automated checks: stack, 401 gates (incl. Slack signature, courses, teaching), program, config, treasury PDA ATA, vault, governance PDA, accounts by type
.\hackathon-smoke.ps1              # preflight + guided wallet steps
```

Judge path: academy pay (85% → treasury PDA) → owner activates the band vault (sync SPP weights) → venue settles with expenses (fee → treasury PDA, pool → vault, split by weight) → each member claims → owner withdraws the treasury at `/owner/treasury` → owner AI digest at `/owner/digest`. Steps 12–20 add `register_venue` / `approve_venue`, the booking escrow (`propose_booking` → `confirm_booking` → `settle_booking`), subscriptions with expiry, `create_royalty_pool` / `deposit_royalties` / `pay_sync_license`, NEXUS / HARMONY / ATLAS, Slack approvals, the academy video library and the earned teaching path (off-chain, gated by the academy subscription and the musician's Enigma level) and, last and optional, the multisig (`init_governance` disables the direct withdraw).

See `HACKATHON_SMOKE.md`.
