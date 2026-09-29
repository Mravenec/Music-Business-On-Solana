//! EH8S DevNet Anchor program — real instructions for wallet testing on DevNet.
//! Value movement uses SPL Token transfers; PDAs mirror EH8S_DEFINITIVO names.
//! Every token account is pinned to the config USDC mint, the treasury ATA,
//! or the program vault ATA recorded in `Eh8sConfig`. After `init_treasury` the treasury
//! ATA belongs to the `["treasury"]` PDA and only the config owner can `withdraw_treasury`.
//! AI agents act through `AgentAuthority` PDAs (`authorize_agent`); the owner keeps every power.
//! Venues list through `VenueListing`; bookings hold the gross in a `VenueAccessToken` escrow
//! that only the owner or a VAULT agent can settle.
//! Once the owner runs `init_governance`, treasury withdrawals and agent permissions only move
//! through M-of-N `Proposal`s (up to 5 signers); the direct owner paths are refused.
//! Each song has a `RoyaltyPool` (`["royalty", track_id]`) with member splits: WAVE deposits and
//! sync-license payments (80% artist / 20% EH8S) credit every member's pending claim by split.
use anchor_lang::prelude::*;
use anchor_lang::system_program;
use anchor_spl::associated_token::{get_associated_token_address, AssociatedToken};
use anchor_spl::token::{self, CloseAccount, Mint, Token, TokenAccount, Transfer};

declare_id!("GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG");

pub const CONFIG_SEED: &[u8] = b"config";
pub const VAULT_SEED: &[u8] = b"vault";
pub const TREASURY_SEED: &[u8] = b"treasury";
pub const MUSICIAN_SEED: &[u8] = b"musician";
pub const CONCERT_SEED: &[u8] = b"concert";
pub const BAND_SEED: &[u8] = b"band";
pub const AGENT_SEED: &[u8] = b"agent";
pub const VENUE_SEED: &[u8] = b"venue";
pub const ACCESS_SEED: &[u8] = b"access";
pub const ESCROW_SEED: &[u8] = b"escrow";
pub const GOVERNANCE_SEED: &[u8] = b"governance";
pub const PROPOSAL_SEED: &[u8] = b"proposal";
pub const MAX_GOVERNANCE_SIGNERS: usize = 5;
pub const PROPOSAL_WITHDRAW: u8 = 0;
pub const PROPOSAL_AUTHORIZE_AGENT: u8 = 1;
pub const PROPOSAL_UPDATE_SIGNERS: u8 = 2;
pub const ACADEMY_SUB_SEED: &[u8] = b"academy_sub";
pub const GEO_SUB_SEED: &[u8] = b"geo_sub";
pub const ROYALTY_SEED: &[u8] = b"royalty";
pub const SYNC_SEED: &[u8] = b"sync";
/// EH8S share of a sync-license payment; the artist side (80%, plus dust) goes to the song pool.
pub const SYNC_FEE_BPS: u16 = 2_000;
pub const USDC_UNIT: u64 = 1_000_000;
pub const MONTH_SECONDS: i64 = 30 * 24 * 60 * 60;
pub const MAX_SUB_MONTHS: u8 = 12;
pub const MAX_GEO_CODE: usize = 24;
pub const BPS_DENOMINATOR: u64 = 10_000;
pub const MAX_VENUE_NAME: usize = 64;
/// Contract types match the `contract_type` catalog (1 fixed, 2 door, 3 versus, 4 promoter).
pub const MAX_CONTRACT_TYPE: u8 = 4;
pub const VENUE_PENDING: u8 = 0;
pub const VENUE_APPROVED: u8 = 1;
pub const BOOKING_PROPOSED: u8 = 0;
pub const BOOKING_CONFIRMED: u8 = 1;
pub const BOOKING_SETTLED: u8 = 2;
pub const MAX_BAND_MEMBERS: usize = 8;
pub const MAX_ENIGMA_LEVEL: u8 = 5;

/// Agent permission bits (EH8S_DEFINITIVO `authorize_agent`).
pub const PERM_PEDAGOGICAL: u8 = 0x01;
pub const PERM_HARMONY: u8 = 0x02;
pub const PERM_STAGE: u8 = 0x04;
pub const PERM_VAULT: u8 = 0x08;
pub const PERM_WAVE: u8 = 0x10;
pub const PERM_ALL: u8 = PERM_PEDAGOGICAL | PERM_HARMONY | PERM_STAGE | PERM_VAULT | PERM_WAVE;

#[program]
pub mod eh8s_devnet {
    use super::*;

    /// Initialize platform config PDA and the vault USDC ATA.
    /// Only the program upgrade authority may call it; `owner` receives protocol fees.
    pub fn initialize_config(
        ctx: Context<InitializeConfig>,
        owner: Pubkey,
        protocol_fee_bps: u16,
    ) -> Result<()> {
        require!(u64::from(protocol_fee_bps) <= BPS_DENOMINATOR, Eh8sError::InvalidFee);
        let mint = ctx.accounts.usdc_mint.key();
        let cfg = &mut ctx.accounts.config;
        cfg.owner = owner;
        cfg.usdc_mint = mint;
        cfg.treasury_usdc = get_associated_token_address(&owner, &mint);
        cfg.vault_usdc = ctx.accounts.vault_usdc.key();
        cfg.protocol_fee_bps = protocol_fee_bps;
        cfg.vault_bump = ctx.bumps.vault_authority;
        cfg.bump = ctx.bumps.config;
        Ok(())
    }

    /// Point `config.treasury_usdc` at the USDC ATA of the `["treasury"]` PDA (created if missing).
    /// Upgrade authority only, like `initialize_config`; the config layout does not change.
    pub fn init_treasury(ctx: Context<InitTreasury>) -> Result<()> {
        ctx.accounts.config.treasury_usdc = ctx.accounts.treasury_usdc.key();
        Ok(())
    }

    /// Owner moves `amount_usdc` from the treasury PDA ATA to the owner's own USDC ATA.
    /// Refused once governance exists (use a withdraw proposal instead).
    pub fn withdraw_treasury(ctx: Context<WithdrawTreasury>, amount_usdc: u64) -> Result<()> {
        require!(ctx.accounts.governance.data_is_empty(), Eh8sError::GovernanceActive);
        require!(amount_usdc > 0, Eh8sError::ZeroAmount);
        treasury_out(
            &ctx.accounts.token_program,
            &ctx.accounts.treasury_usdc,
            &ctx.accounts.owner_usdc,
            &ctx.accounts.treasury_authority,
            ctx.bumps.treasury_authority,
            amount_usdc,
        )
    }

    /// Owner grants (or with `permissions = 0` revokes) agent powers to a wallet.
    /// Refused once governance exists (use an agent proposal instead).
    pub fn authorize_agent(
        ctx: Context<AuthorizeAgent>,
        agent_wallet: Pubkey,
        permissions: u8,
    ) -> Result<()> {
        require!(ctx.accounts.governance.data_is_empty(), Eh8sError::GovernanceActive);
        set_agent(&mut ctx.accounts.agent_authority, agent_wallet, permissions, ctx.bumps.agent_authority)
    }

    /// Config owner creates the `["governance"]` PDA once: 1..5 distinct signers and
    /// 1 ≤ threshold ≤ signers. Signer changes afterwards go through proposals.
    pub fn init_governance(
        ctx: Context<InitGovernance>,
        signers: Vec<Pubkey>,
        threshold: u8,
    ) -> Result<()> {
        validate_signers(&signers, threshold)?;
        let gov = &mut ctx.accounts.governance;
        gov.signers = signers;
        gov.threshold = threshold;
        gov.epoch = 0;
        gov.proposal_count = 0;
        gov.bump = ctx.bumps.governance;
        Ok(())
    }

    /// A governance signer opens proposal `governance.proposal_count` and approves it.
    /// Withdraw uses `amount_usdc` + `target` (destination USDC account of a signer);
    /// authorize agent uses `target` (agent wallet) + `permissions`;
    /// update signers uses `new_signers` + `new_threshold`. Unused fields are stored empty.
    #[allow(clippy::too_many_arguments)]
    pub fn propose(
        ctx: Context<Propose>,
        kind: u8,
        amount_usdc: u64,
        target: Pubkey,
        permissions: u8,
        new_signers: Vec<Pubkey>,
        new_threshold: u8,
    ) -> Result<()> {
        let proposer = ctx.accounts.proposer.key();
        let gov = &mut ctx.accounts.governance;
        require!(gov.signers.contains(&proposer), Eh8sError::NotGovernanceSigner);
        let p = &mut ctx.accounts.proposal;
        p.kind = kind;
        p.amount_usdc = 0;
        p.target = Pubkey::default();
        p.permissions = 0;
        p.new_signers = Vec::new();
        p.new_threshold = 0;
        match kind {
            PROPOSAL_WITHDRAW => {
                require!(amount_usdc > 0, Eh8sError::ZeroAmount);
                require!(target != Pubkey::default(), Eh8sError::InvalidProposal);
                p.amount_usdc = amount_usdc;
                p.target = target;
            }
            PROPOSAL_AUTHORIZE_AGENT => {
                require!(permissions & !PERM_ALL == 0, Eh8sError::InvalidPermissions);
                require!(target != Pubkey::default(), Eh8sError::InvalidPermissions);
                p.target = target;
                p.permissions = permissions;
            }
            PROPOSAL_UPDATE_SIGNERS => {
                validate_signers(&new_signers, new_threshold)?;
                p.new_signers = new_signers;
                p.new_threshold = new_threshold;
            }
            _ => return err!(Eh8sError::InvalidProposal),
        }
        p.id = gov.proposal_count;
        p.epoch = gov.epoch;
        p.proposer = proposer;
        p.approvals = vec![proposer];
        p.executed = false;
        p.bump = ctx.bumps.proposal;
        gov.proposal_count = gov.proposal_count.checked_add(1).ok_or(Eh8sError::MathOverflow)?;
        Ok(())
    }

    /// A current governance signer approves an open proposal once.
    pub fn approve_proposal(ctx: Context<ApproveProposal>) -> Result<()> {
        let approver = ctx.accounts.approver.key();
        let gov = &ctx.accounts.governance;
        let p = &mut ctx.accounts.proposal;
        require!(gov.signers.contains(&approver), Eh8sError::NotGovernanceSigner);
        require!(!p.executed, Eh8sError::ProposalExecuted);
        require!(p.epoch == gov.epoch, Eh8sError::StaleProposal);
        require!(!p.approvals.contains(&approver), Eh8sError::AlreadyApproved);
        p.approvals.push(approver);
        Ok(())
    }

    /// Anyone runs an approved withdraw proposal once: treasury PDA ATA → the signer's USDC account.
    pub fn execute_withdraw_proposal(ctx: Context<ExecuteWithdrawProposal>) -> Result<()> {
        ready_to_execute(&ctx.accounts.governance, &ctx.accounts.proposal, PROPOSAL_WITHDRAW)?;
        let amount = ctx.accounts.proposal.amount_usdc;
        ctx.accounts.proposal.executed = true;
        treasury_out(
            &ctx.accounts.token_program,
            &ctx.accounts.treasury_usdc,
            &ctx.accounts.destination_usdc,
            &ctx.accounts.treasury_authority,
            ctx.bumps.treasury_authority,
            amount,
        )
    }

    /// Anyone runs an approved agent proposal once (grant, change, or `0` revoke).
    pub fn execute_agent_proposal(ctx: Context<ExecuteAgentProposal>) -> Result<()> {
        ready_to_execute(&ctx.accounts.governance, &ctx.accounts.proposal, PROPOSAL_AUTHORIZE_AGENT)?;
        let (wallet, permissions) = (ctx.accounts.proposal.target, ctx.accounts.proposal.permissions);
        ctx.accounts.proposal.executed = true;
        set_agent(&mut ctx.accounts.agent_authority, wallet, permissions, ctx.bumps.agent_authority)
    }

    /// Anyone runs an approved signer-set proposal once; older open proposals become stale.
    pub fn execute_signers_proposal(ctx: Context<ExecuteSignersProposal>) -> Result<()> {
        ready_to_execute(&ctx.accounts.governance, &ctx.accounts.proposal, PROPOSAL_UPDATE_SIGNERS)?;
        let p = &mut ctx.accounts.proposal;
        p.executed = true;
        let gov = &mut ctx.accounts.governance;
        gov.signers = p.new_signers.clone();
        gov.threshold = p.new_threshold;
        gov.epoch = gov.epoch.checked_add(1).ok_or(Eh8sError::MathOverflow)?;
        Ok(())
    }

    /// Owner or a pedagogical (NEXUS) agent sets a musician's Enigma level (0..5).
    pub fn update_musician_level(ctx: Context<UpdateMusicianLevel>, new_level: u8) -> Result<()> {
        require!(
            has_power(
                &ctx.accounts.authority.key(),
                &ctx.accounts.config,
                &ctx.accounts.agent_authority,
                PERM_PEDAGOGICAL
            ),
            Eh8sError::Unauthorized
        );
        require!(new_level <= MAX_ENIGMA_LEVEL, Eh8sError::InvalidLevel);
        ctx.accounts.musician_profile.enigma_level = new_level;
        Ok(())
    }

    /// Musician creates or refreshes their MusicianProfile PDA (instrument + ISO-3166 alpha-3
    /// country). A new profile starts at level 0; level and pending claims are never set here.
    /// A profile written by an older program version is grown in place before the update.
    pub fn upsert_musician_profile(
        ctx: Context<UpsertMusicianProfile>,
        instrument_code: u8,
        country: [u8; 3],
    ) -> Result<()> {
        require!(
            country.iter().all(|c| c.is_ascii_uppercase()),
            Eh8sError::InvalidCountry
        );
        let musician = &ctx.accounts.musician;
        let info = ctx.accounts.musician_profile.to_account_info();
        let space = 8 + MusicianProfile::INIT_SPACE;
        let rent = Rent::get()?;
        let bump = ctx.bumps.musician_profile;

        if info.owner == &system_program::ID {
            require!(info.lamports() == 0, Eh8sError::Unauthorized);
            let key = musician.key();
            let seeds: &[&[u8]] = &[MUSICIAN_SEED, key.as_ref(), &[bump]];
            system_program::create_account(
                CpiContext::new_with_signer(
                    ctx.accounts.system_program.to_account_info(),
                    system_program::CreateAccount {
                        from: musician.to_account_info(),
                        to: info.clone(),
                    },
                    &[seeds],
                ),
                rent.minimum_balance(space),
                space as u64,
                ctx.program_id,
            )?;
            let profile = MusicianProfile {
                authority: key,
                enigma_level: 0,
                instrument_code,
                pending_claims_usdc: 0,
                bump,
                country,
            };
            let mut data = info.try_borrow_mut_data()?;
            let mut out: &mut [u8] = &mut data;
            return profile.try_serialize(&mut out);
        }

        require_keys_eq!(*info.owner, crate::ID, Eh8sError::Unauthorized);
        if info.data_len() < space {
            let need = rent.minimum_balance(space).saturating_sub(info.lamports());
            if need > 0 {
                system_program::transfer(
                    CpiContext::new(
                        ctx.accounts.system_program.to_account_info(),
                        system_program::Transfer {
                            from: musician.to_account_info(),
                            to: info.clone(),
                        },
                    ),
                    need,
                )?;
            }
            info.realloc(space, true)?;
        }
        let mut profile = {
            let data = info.try_borrow_data()?;
            MusicianProfile::try_deserialize(&mut &data[..])?
        };
        require_keys_eq!(profile.authority, musician.key(), Eh8sError::Unauthorized);
        profile.instrument_code = instrument_code;
        profile.country = country;
        let mut data = info.try_borrow_mut_data()?;
        let mut out: &mut [u8] = &mut data;
        profile.try_serialize(&mut out)
    }

    /// Create or renew an academy plan for `months` (1..12). The price is fixed on-chain
    /// (1 Basic $30, 2 Band $55, 3 Pro $90 per month); 85% treasury / 15% instructor.
    /// Renewal extends from max(now, expires_at); the latest plan applies.
    pub fn subscribe_academy(ctx: Context<SubscribeAcademy>, plan_type: u8, months: u8) -> Result<()> {
        require!(valid_months(months), Eh8sError::InvalidPlan);
        let amount_usdc = academy_plan_price(plan_type)?
            .checked_mul(months as u64)
            .ok_or(Eh8sError::MathOverflow)?;
        let instructor_share = amount_usdc
            .checked_mul(15)
            .and_then(|v| v.checked_div(100))
            .ok_or(Eh8sError::MathOverflow)?;
        let treasury_share = amount_usdc
            .checked_sub(instructor_share)
            .ok_or(Eh8sError::MathOverflow)?;

        transfer_from_signer(
            &ctx.accounts.token_program,
            &ctx.accounts.payer_usdc,
            &ctx.accounts.treasury_usdc,
            &ctx.accounts.payer,
            treasury_share,
        )?;
        transfer_from_signer(
            &ctx.accounts.token_program,
            &ctx.accounts.payer_usdc,
            &ctx.accounts.instructor_usdc,
            &ctx.accounts.payer,
            instructor_share,
        )?;

        let now = Clock::get()?.unix_timestamp;
        let sub = &mut ctx.accounts.academy_subscription;
        if sub.bump == 0 {
            sub.bump = ctx.bumps.academy_subscription;
            sub.started_at = now;
        }
        sub.musician = ctx.accounts.payer.key();
        sub.plan_code = plan_type;
        sub.months_paid = sub.months_paid.saturating_add(months as u16);
        sub.amount_usdc = amount_usdc;
        sub.expires_at = renew_expiry(sub.expires_at, now, months)?;
        sub.active = 1;
        Ok(())
    }

    /// Owner or a HARMONY agent creates the on-chain BandVault for an off-chain band id with
    /// its member wallets (vault order). Weights start as an equal split, dust to the last member.
    pub fn create_band(ctx: Context<CreateBand>, band_id: u64, members: Vec<Pubkey>) -> Result<()> {
        require!(
            has_power(
                &ctx.accounts.authority.key(),
                &ctx.accounts.config,
                &ctx.accounts.agent_authority,
                PERM_HARMONY
            ),
            Eh8sError::Unauthorized
        );
        require!(
            !members.is_empty() && members.len() <= MAX_BAND_MEMBERS,
            Eh8sError::InvalidMembers
        );
        for (i, m) in members.iter().enumerate() {
            require!(*m != Pubkey::default(), Eh8sError::InvalidMembers);
            require!(!members[..i].contains(m), Eh8sError::InvalidMembers);
        }
        let vault = &mut ctx.accounts.band_vault;
        vault.band_id = band_id;
        vault.weights_bps = equal_weights(members.len());
        vault.members = members;
        vault.weights_version = 0;
        vault.bump = ctx.bumps.band_vault;
        Ok(())
    }

    /// Owner or a HARMONY agent syncs SPP weights (basis points, vault member order, sum = 10000).
    pub fn update_spp_weights(ctx: Context<UpdateSppWeights>, weights_bps: Vec<u16>) -> Result<()> {
        require!(
            has_power(
                &ctx.accounts.authority.key(),
                &ctx.accounts.config,
                &ctx.accounts.agent_authority,
                PERM_HARMONY
            ),
            Eh8sError::Unauthorized
        );
        let vault = &mut ctx.accounts.band_vault;
        require!(
            weights_bps.len() == vault.members.len(),
            Eh8sError::InvalidWeights
        );
        let sum: u64 = weights_bps.iter().map(|w| u64::from(*w)).sum();
        require!(sum == BPS_DENOMINATOR, Eh8sError::InvalidWeights);
        vault.weights_bps = weights_bps;
        vault.weights_version = vault
            .weights_version
            .checked_add(1)
            .ok_or(Eh8sError::MathOverflow)?;
        Ok(())
    }

    /// Venue wallet lists one venue for the Stage Map. It stays pending (cannot book) until the
    /// owner or a STAGE agent approves it.
    pub fn register_venue(
        ctx: Context<RegisterVenue>,
        venue_id: u64,
        name: String,
        country: [u8; 3],
        capacity: u32,
        contract_type: u8,
    ) -> Result<()> {
        require!(
            !name.trim().is_empty() && name.len() <= MAX_VENUE_NAME,
            Eh8sError::InvalidVenue
        );
        require!(capacity > 0, Eh8sError::InvalidVenue);
        require!(
            (1..=MAX_CONTRACT_TYPE).contains(&contract_type),
            Eh8sError::InvalidVenue
        );
        require!(
            country.iter().all(u8::is_ascii_uppercase),
            Eh8sError::InvalidCountry
        );
        let listing = &mut ctx.accounts.venue_listing;
        listing.venue = ctx.accounts.venue.key();
        listing.venue_id = venue_id;
        listing.name = name;
        listing.country = country;
        listing.capacity = capacity;
        listing.contract_type = contract_type;
        listing.status = VENUE_PENDING;
        listing.bump = ctx.bumps.venue_listing;
        Ok(())
    }

    /// Owner or a STAGE agent verifies a pending venue listing.
    pub fn approve_venue(ctx: Context<ApproveVenue>) -> Result<()> {
        require!(
            has_power(
                &ctx.accounts.authority.key(),
                &ctx.accounts.config,
                &ctx.accounts.agent_authority,
                PERM_STAGE
            ),
            Eh8sError::Unauthorized
        );
        ctx.accounts.venue_listing.status = VENUE_APPROVED;
        Ok(())
    }

    /// Approved venue proposes a show: creates the VenueAccessToken for (band, date) and moves
    /// `gross_usdc` into an escrow token account owned by that token PDA.
    pub fn propose_booking(
        ctx: Context<ProposeBooking>,
        concert_id: u64,
        band_id: u64,
        date_ymd: u32,
        gross_usdc: u64,
    ) -> Result<()> {
        require!(gross_usdc > 0, Eh8sError::ZeroAmount);
        require!(valid_ymd(date_ymd), Eh8sError::InvalidBooking);
        require!(
            ctx.accounts.venue_listing.status == VENUE_APPROVED,
            Eh8sError::VenueNotApproved
        );
        transfer_from_signer(
            &ctx.accounts.token_program,
            &ctx.accounts.venue_usdc,
            &ctx.accounts.escrow_usdc,
            &ctx.accounts.venue,
            gross_usdc,
        )?;
        let t = &mut ctx.accounts.venue_access_token;
        t.venue = ctx.accounts.venue.key();
        t.venue_listing = ctx.accounts.venue_listing.key();
        t.band_id = band_id;
        t.concert_id = concert_id;
        t.date_ymd = date_ymd;
        t.gross_usdc = gross_usdc;
        t.contract_hash = [0u8; 32];
        t.status = BOOKING_PROPOSED;
        t.bump = ctx.bumps.venue_access_token;
        t.escrow_bump = ctx.bumps.escrow_usdc;
        Ok(())
    }

    /// Owner or a STAGE agent confirms a proposed booking and pins the SHA-256 of the contract.
    pub fn confirm_booking(ctx: Context<ConfirmBooking>, contract_hash: [u8; 32]) -> Result<()> {
        require!(
            has_power(
                &ctx.accounts.authority.key(),
                &ctx.accounts.config,
                &ctx.accounts.agent_authority,
                PERM_STAGE
            ),
            Eh8sError::Unauthorized
        );
        require!(contract_hash != [0u8; 32], Eh8sError::InvalidBooking);
        let t = &mut ctx.accounts.venue_access_token;
        require!(t.status == BOOKING_PROPOSED, Eh8sError::InvalidBookingStatus);
        t.contract_hash = contract_hash;
        t.status = BOOKING_CONFIRMED;
        Ok(())
    }

    /// Venue withdraws a booking that was not confirmed yet: the escrow is refunded and both the
    /// escrow account and the VenueAccessToken are closed back to the venue.
    pub fn cancel_booking(ctx: Context<CancelBooking>) -> Result<()> {
        let t = &ctx.accounts.venue_access_token;
        require!(t.status == BOOKING_PROPOSED, Eh8sError::InvalidBookingStatus);
        let band = t.band_id.to_le_bytes();
        let date = t.date_ymd.to_le_bytes();
        let bump = [t.bump];
        let seeds: &[&[u8]] = &[ACCESS_SEED, t.venue.as_ref(), &band, &date, &bump];
        let amount = ctx.accounts.escrow_usdc.amount;
        escrow_out(
            &ctx.accounts.token_program,
            &ctx.accounts.escrow_usdc,
            &ctx.accounts.venue_usdc,
            &ctx.accounts.venue_access_token,
            seeds,
            amount,
        )?;
        close_escrow(
            &ctx.accounts.token_program,
            &ctx.accounts.escrow_usdc,
            &ctx.accounts.venue.to_account_info(),
            &ctx.accounts.venue_access_token,
            seeds,
        )
    }

    /// Owner or a VAULT agent settles a confirmed booking from its escrow: expenses → venue,
    /// fee (config bps of net) → treasury, pool → vault, member pending += pool × weight.
    /// Remaining accounts are the member MusicianProfile PDAs in vault order. Runs once; the
    /// emptied escrow stays open so a replay fails on the settled token, not a missing account.
    pub fn settle_booking<'info>(
        ctx: Context<'_, '_, 'info, 'info, SettleBooking<'info>>,
        gross_usdc: u64,
        expenses_usdc: u64,
    ) -> Result<()> {
        require!(
            has_power(
                &ctx.accounts.authority.key(),
                &ctx.accounts.config,
                &ctx.accounts.agent_authority,
                PERM_VAULT
            ),
            Eh8sError::Unauthorized
        );
        let t = &ctx.accounts.venue_access_token;
        require!(
            t.status != BOOKING_SETTLED && ctx.accounts.concert_settlement.settled == 0,
            Eh8sError::AlreadySettled
        );
        require!(t.status == BOOKING_CONFIRMED, Eh8sError::InvalidBookingStatus);
        require!(
            gross_usdc == t.gross_usdc && ctx.accounts.escrow_usdc.amount == gross_usdc,
            Eh8sError::EscrowMismatch
        );
        let net = gross_usdc
            .checked_sub(expenses_usdc)
            .ok_or(Eh8sError::MathOverflow)?;
        require!(net > 0, Eh8sError::ZeroAmount);
        let (fee, pool) = split_fee(net, ctx.accounts.config.protocol_fee_bps)?;
        credit_members(
            ctx.program_id,
            ctx.remaining_accounts,
            &ctx.accounts.band_vault,
            pool,
        )?;

        let band = t.band_id.to_le_bytes();
        let date = t.date_ymd.to_le_bytes();
        let bump = [t.bump];
        let venue = t.venue;
        let concert_id = t.concert_id;
        let seeds: &[&[u8]] = &[ACCESS_SEED, venue.as_ref(), &band, &date, &bump];
        for (to, amount) in [
            (&*ctx.accounts.venue_usdc, expenses_usdc),
            (&*ctx.accounts.treasury_usdc, fee),
            (&*ctx.accounts.vault_usdc, pool),
        ] {
            if amount > 0 {
                escrow_out(
                    &ctx.accounts.token_program,
                    &ctx.accounts.escrow_usdc,
                    to,
                    &ctx.accounts.venue_access_token,
                    seeds,
                    amount,
                )?;
            }
        }

        let band_key = ctx.accounts.band_vault.key();
        let row = &mut ctx.accounts.concert_settlement;
        row.venue = venue;
        row.concert_id = concert_id;
        row.gross_usdc = gross_usdc;
        row.expenses_usdc = expenses_usdc;
        row.eh8s_fee_usdc = fee;
        row.band_pool_usdc = pool;
        row.band = band_key;
        row.settled = 1;
        row.bump = ctx.bumps.concert_settlement;
        ctx.accounts.venue_access_token.status = BOOKING_SETTLED;
        Ok(())
    }

    /// Legacy venue-funded settle (kept wire-compatible for older clients); the product flow is
    /// `propose_booking` → `confirm_booking` → `settle_booking`.
    /// Venue pays DevNet USDC for one concert: net = gross − expenses (expenses stay with the
    /// venue); fee (config bps) of net → treasury; pool → vault; each band member's
    /// pending += pool × weight (dust to the last member). Remaining accounts are the member
    /// MusicianProfile PDAs in vault order. A concert settles only once.
    pub fn settle_concert<'info>(
        ctx: Context<'_, '_, 'info, 'info, SettleConcert<'info>>,
        concert_id: u64,
        gross_usdc: u64,
        expenses_usdc: u64,
    ) -> Result<()> {
        require!(gross_usdc > 0, Eh8sError::ZeroAmount);
        require!(
            ctx.accounts.concert_settlement.settled == 0,
            Eh8sError::AlreadySettled
        );
        let net = gross_usdc
            .checked_sub(expenses_usdc)
            .ok_or(Eh8sError::MathOverflow)?;
        require!(net > 0, Eh8sError::ZeroAmount);
        let (fee, pool) = split_fee(net, ctx.accounts.config.protocol_fee_bps)?;
        credit_members(
            ctx.program_id,
            ctx.remaining_accounts,
            &ctx.accounts.band_vault,
            pool,
        )?;

        if fee > 0 {
            transfer_from_signer(
                &ctx.accounts.token_program,
                &ctx.accounts.venue_usdc,
                &ctx.accounts.treasury_usdc,
                &ctx.accounts.venue,
                fee,
            )?;
        }
        if pool > 0 {
            transfer_from_signer(
                &ctx.accounts.token_program,
                &ctx.accounts.venue_usdc,
                &ctx.accounts.vault_usdc,
                &ctx.accounts.venue,
                pool,
            )?;
        }

        let band_key = ctx.accounts.band_vault.key();
        let row = &mut ctx.accounts.concert_settlement;
        row.venue = ctx.accounts.venue.key();
        row.concert_id = concert_id;
        row.gross_usdc = gross_usdc;
        row.expenses_usdc = expenses_usdc;
        row.eh8s_fee_usdc = fee;
        row.band_pool_usdc = pool;
        row.band = band_key;
        row.settled = 1;
        row.bump = ctx.bumps.concert_settlement;
        Ok(())
    }

    /// Musician claims pending USDC from the vault ATA into their own USDC account.
    pub fn claim_royalties(ctx: Context<ClaimRoyalties>, amount_usdc: u64) -> Result<()> {
        require!(amount_usdc > 0, Eh8sError::ZeroAmount);
        let profile = &mut ctx.accounts.musician_profile;
        require!(
            profile.pending_claims_usdc >= amount_usdc,
            Eh8sError::InsufficientClaim
        );
        let bump = [ctx.accounts.config.vault_bump];
        let seeds: &[&[u8]] = &[VAULT_SEED, &bump];
        let signer = &[seeds];
        token::transfer(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.to_account_info(),
                Transfer {
                    from: ctx.accounts.vault_usdc.to_account_info(),
                    to: ctx.accounts.musician_usdc.to_account_info(),
                    authority: ctx.accounts.vault_authority.to_account_info(),
                },
                signer,
            ),
            amount_usdc,
        )?;
        profile.pending_claims_usdc = profile
            .pending_claims_usdc
            .checked_sub(amount_usdc)
            .ok_or(Eh8sError::MathOverflow)?;
        Ok(())
    }

    /// Owner or a WAVE agent opens the song's RoyaltyPool (["royalty", track_id]) with 1..8
    /// distinct member wallets and their splits (bps, pool order, sum 10000). Runs once per song.
    pub fn create_royalty_pool(
        ctx: Context<CreateRoyaltyPool>,
        track_id: u64,
        members: Vec<Pubkey>,
        splits_bps: Vec<u16>,
    ) -> Result<()> {
        require!(
            has_power(
                &ctx.accounts.authority.key(),
                &ctx.accounts.config,
                &ctx.accounts.agent_authority,
                PERM_WAVE
            ),
            Eh8sError::Unauthorized
        );
        validate_splits(&members, &splits_bps)?;
        let pool = &mut ctx.accounts.royalty_pool;
        pool.track_id = track_id;
        pool.members = members;
        pool.splits_bps = splits_bps;
        pool.total_usdc = 0;
        pool.sync_total_usdc = 0;
        pool.bump = ctx.bumps.royalty_pool;
        Ok(())
    }

    /// Owner or a WAVE agent deposits song royalties into the vault; each member's pending claim
    /// grows by amount × split (dust to the last member). Remaining accounts are the member
    /// MusicianProfile PDAs in pool order.
    pub fn deposit_royalties<'info>(
        ctx: Context<'_, '_, 'info, 'info, DepositRoyalties<'info>>,
        track_id: u64,
        amount_usdc: u64,
    ) -> Result<()> {
        require!(amount_usdc > 0, Eh8sError::ZeroAmount);
        require!(ctx.accounts.royalty_pool.track_id == track_id, Eh8sError::MemberMismatch);
        require!(
            has_power(
                &ctx.accounts.depositor.key(),
                &ctx.accounts.config,
                &ctx.accounts.agent_authority,
                PERM_WAVE
            ),
            Eh8sError::Unauthorized
        );
        let pool = &ctx.accounts.royalty_pool;
        credit_split(
            ctx.program_id,
            ctx.remaining_accounts,
            &pool.members,
            &pool.splits_bps,
            amount_usdc,
        )?;
        transfer_from_signer(
            &ctx.accounts.token_program,
            &ctx.accounts.depositor_usdc,
            &ctx.accounts.vault_usdc,
            &ctx.accounts.depositor,
            amount_usdc,
        )?;
        let pool = &mut ctx.accounts.royalty_pool;
        pool.total_usdc = pool
            .total_usdc
            .checked_add(amount_usdc)
            .ok_or(Eh8sError::MathOverflow)?;
        Ok(())
    }

    /// A licensee pays one sync deal for a song: 20% → treasury, 80% (plus dust) → vault credited
    /// through the song's splits. SyncLicense (["sync", track_id, deal_id]) makes each deal
    /// payable once. Remaining accounts are the member MusicianProfile PDAs in pool order.
    pub fn pay_sync_license<'info>(
        ctx: Context<'_, '_, 'info, 'info, PaySyncLicense<'info>>,
        track_id: u64,
        deal_id: u64,
        amount_usdc: u64,
    ) -> Result<()> {
        require!(amount_usdc > 0, Eh8sError::ZeroAmount);
        let (fee, artist) = split_fee(amount_usdc, SYNC_FEE_BPS)?;
        let pool = &ctx.accounts.royalty_pool;
        credit_split(
            ctx.program_id,
            ctx.remaining_accounts,
            &pool.members,
            &pool.splits_bps,
            artist,
        )?;
        for (to, amount) in [
            (&ctx.accounts.treasury_usdc, fee),
            (&ctx.accounts.vault_usdc, artist),
        ] {
            if amount > 0 {
                transfer_from_signer(
                    &ctx.accounts.token_program,
                    &ctx.accounts.payer_usdc,
                    to,
                    &ctx.accounts.payer,
                    amount,
                )?;
            }
        }
        let pool = &mut ctx.accounts.royalty_pool;
        pool.sync_total_usdc = pool
            .sync_total_usdc
            .checked_add(amount_usdc)
            .ok_or(Eh8sError::MathOverflow)?;
        let lic = &mut ctx.accounts.sync_license;
        lic.track_id = track_id;
        lic.deal_id = deal_id;
        lic.payer = ctx.accounts.payer.key();
        lic.amount_usdc = amount_usdc;
        lic.eh8s_fee_usdc = fee;
        lic.artist_usdc = artist;
        lic.paid_at = Clock::get()?.unix_timestamp;
        lic.bump = ctx.bumps.sync_license;
        Ok(())
    }

    /// Create or renew a zone subscription (`MX`, `MX_CDMX`, `MX_CDMX_CENTRO`) for `months`
    /// (1..12). Tier price is fixed on-chain (1 Local $5, 2 City $15, 3 Country $35,
    /// 4 Regional $65, 5 Global $99 per month); 100% to the treasury.
    pub fn subscribe_geographic(
        ctx: Context<SubscribeGeographic>,
        geo_code: String,
        tier: u8,
        months: u8,
    ) -> Result<()> {
        require!(
            valid_geo_code(&geo_code) && valid_months(months),
            Eh8sError::InvalidGeoSubscription
        );
        let amount_usdc = geo_tier_price(tier)?
            .checked_mul(months as u64)
            .ok_or(Eh8sError::MathOverflow)?;
        transfer_from_signer(
            &ctx.accounts.token_program,
            &ctx.accounts.payer_usdc,
            &ctx.accounts.treasury_usdc,
            &ctx.accounts.payer,
            amount_usdc,
        )?;
        let now = Clock::get()?.unix_timestamp;
        let geo = &mut ctx.accounts.geo_subscription;
        if geo.bump == 0 {
            geo.bump = ctx.bumps.geo_subscription;
            geo.started_at = now;
        }
        geo.authority = ctx.accounts.payer.key();
        geo.geo_code = geo_code;
        geo.tier_code = tier;
        geo.months_paid = geo.months_paid.saturating_add(months as u16);
        geo.amount_usdc = amount_usdc;
        geo.expires_at = renew_expiry(geo.expires_at, now, months)?;
        geo.active = 1;
        Ok(())
    }
}

/// Monthly academy price in USDC atomic units (plan 1 Basic $30, 2 Band $55, 3 Pro $90).
pub fn academy_plan_price(plan_type: u8) -> Result<u64> {
    let usd: u64 = match plan_type {
        1 => 30,
        2 => 55,
        3 => 90,
        _ => return err!(Eh8sError::InvalidPlan),
    };
    Ok(usd * USDC_UNIT)
}

/// Monthly geographic price in USDC atomic units (tiers 1..5 = $5, $15, $35, $65, $99).
pub fn geo_tier_price(tier: u8) -> Result<u64> {
    let usd: u64 = match tier {
        1 => 5,
        2 => 15,
        3 => 35,
        4 => 65,
        5 => 99,
        _ => return err!(Eh8sError::InvalidGeoSubscription),
    };
    Ok(usd * USDC_UNIT)
}

/// Subscriptions are bought 1..12 months at a time.
pub fn valid_months(months: u8) -> bool {
    (1..=MAX_SUB_MONTHS).contains(&months)
}

/// Geo code: 2..24 bytes of `A-Z`, `0-9`, `_`, starting with two letters (`MX`, `MX_CDMX`).
pub fn valid_geo_code(code: &str) -> bool {
    let b = code.as_bytes();
    (2..=MAX_GEO_CODE).contains(&b.len())
        && b[0].is_ascii_uppercase()
        && b[1].is_ascii_uppercase()
        && b.iter().all(|c| c.is_ascii_uppercase() || c.is_ascii_digit() || *c == b'_')
}

/// New expiry after paying `months`: extends from max(now, current expiry) by 30-day months.
pub fn renew_expiry(current_expires_at: i64, now: i64, months: u8) -> Result<i64> {
    current_expires_at
        .max(now)
        .checked_add(months as i64 * MONTH_SECONDS)
        .ok_or_else(|| error!(Eh8sError::MathOverflow))
}

/// True when `signer` is the config owner, or `agent` is that signer's AgentAuthority with `bit`.
pub fn has_power(
    signer: &Pubkey,
    config: &Eh8sConfig,
    agent: &Option<Account<AgentAuthority>>,
    bit: u8,
) -> bool {
    if *signer == config.owner {
        return true;
    }
    match agent {
        Some(a) => a.agent == *signer && a.permissions & bit != 0,
        None => false,
    }
}

/// 1..8 distinct non-empty members; one non-zero split per member summing to 10000 bps.
pub fn validate_splits(members: &[Pubkey], splits_bps: &[u16]) -> Result<()> {
    require!(
        !members.is_empty() && members.len() <= MAX_BAND_MEMBERS,
        Eh8sError::InvalidMembers
    );
    for (i, m) in members.iter().enumerate() {
        require!(*m != Pubkey::default(), Eh8sError::InvalidMembers);
        require!(!members[..i].contains(m), Eh8sError::InvalidMembers);
    }
    require!(
        splits_bps.len() == members.len() && splits_bps.iter().all(|s| *s > 0),
        Eh8sError::InvalidWeights
    );
    let sum: u64 = splits_bps.iter().map(|s| u64::from(*s)).sum();
    require!(sum == BPS_DENOMINATOR, Eh8sError::InvalidWeights);
    Ok(())
}

/// 1..5 distinct non-empty signers and 1 ≤ threshold ≤ signer count.
pub fn validate_signers(signers: &[Pubkey], threshold: u8) -> Result<()> {
    require!(
        !signers.is_empty() && signers.len() <= MAX_GOVERNANCE_SIGNERS,
        Eh8sError::InvalidGovernance
    );
    require!(
        threshold >= 1 && usize::from(threshold) <= signers.len(),
        Eh8sError::InvalidGovernance
    );
    for (i, s) in signers.iter().enumerate() {
        require!(*s != Pubkey::default(), Eh8sError::InvalidGovernance);
        require!(!signers[..i].contains(s), Eh8sError::InvalidGovernance);
    }
    Ok(())
}

/// Proposal of `kind`, not executed, from the current signer epoch, with approvals from
/// current signers reaching the threshold.
fn ready_to_execute(gov: &Governance, p: &Proposal, kind: u8) -> Result<()> {
    require!(p.kind == kind, Eh8sError::InvalidProposal);
    require!(!p.executed, Eh8sError::ProposalExecuted);
    require!(p.epoch == gov.epoch, Eh8sError::StaleProposal);
    let approvals = p.approvals.iter().filter(|a| gov.signers.contains(a)).count();
    require!(approvals >= usize::from(gov.threshold), Eh8sError::ThresholdNotMet);
    Ok(())
}

fn set_agent(
    agent: &mut Account<AgentAuthority>,
    agent_wallet: Pubkey,
    permissions: u8,
    bump: u8,
) -> Result<()> {
    require!(permissions & !PERM_ALL == 0, Eh8sError::InvalidPermissions);
    require!(agent_wallet != Pubkey::default(), Eh8sError::InvalidPermissions);
    agent.agent = agent_wallet;
    agent.permissions = permissions;
    agent.bump = bump;
    Ok(())
}

/// Moves `amount` out of the treasury PDA ATA, signed by the `["treasury"]` PDA.
fn treasury_out<'info>(
    token_program: &Program<'info, Token>,
    treasury: &Account<'info, TokenAccount>,
    to: &Account<'info, TokenAccount>,
    treasury_authority: &UncheckedAccount<'info>,
    bump: u8,
    amount: u64,
) -> Result<()> {
    require!(treasury.amount >= amount, Eh8sError::InsufficientTreasury);
    let seeds: &[&[u8]] = &[TREASURY_SEED, &[bump]];
    token::transfer(
        CpiContext::new_with_signer(
            token_program.to_account_info(),
            Transfer {
                from: treasury.to_account_info(),
                to: to.to_account_info(),
                authority: treasury_authority.to_account_info(),
            },
            &[seeds],
        ),
        amount,
    )
}

/// Fee = gross × bps / 10000 (floor); pool = gross − fee.
pub fn split_fee(gross_usdc: u64, protocol_fee_bps: u16) -> Result<(u64, u64)> {
    let fee = u128::from(gross_usdc)
        .checked_mul(u128::from(protocol_fee_bps))
        .and_then(|v| v.checked_div(u128::from(BPS_DENOMINATOR)))
        .ok_or(Eh8sError::MathOverflow)?;
    let fee = u64::try_from(fee).map_err(|_| Eh8sError::MathOverflow)?;
    let pool = gross_usdc.checked_sub(fee).ok_or(Eh8sError::MathOverflow)?;
    Ok((fee, pool))
}

/// Equal weights summing to 10000; the last member takes the remainder.
pub fn equal_weights(n: usize) -> Vec<u16> {
    if n == 0 {
        return Vec::new();
    }
    let base = (BPS_DENOMINATOR / n as u64) as u16;
    let mut out = vec![base; n];
    out[n - 1] = (BPS_DENOMINATOR - u64::from(base) * (n as u64 - 1)) as u16;
    out
}

/// Member shares of `pool`: floor(pool × w / 10000) each; the last member takes the remainder.
pub fn split_members(pool: u64, weights_bps: &[u16]) -> Result<Vec<u64>> {
    require!(!weights_bps.is_empty(), Eh8sError::InvalidWeights);
    let mut out = Vec::with_capacity(weights_bps.len());
    let mut allocated: u64 = 0;
    for w in &weights_bps[..weights_bps.len() - 1] {
        let share = u128::from(pool)
            .checked_mul(u128::from(*w))
            .and_then(|v| v.checked_div(u128::from(BPS_DENOMINATOR)))
            .ok_or(Eh8sError::MathOverflow)?;
        let share = u64::try_from(share).map_err(|_| Eh8sError::MathOverflow)?;
        allocated = allocated.checked_add(share).ok_or(Eh8sError::MathOverflow)?;
        out.push(share);
    }
    out.push(pool.checked_sub(allocated).ok_or(Eh8sError::MathOverflow)?);
    Ok(out)
}

fn transfer_from_signer<'info>(
    token_program: &Program<'info, Token>,
    from: &Account<'info, TokenAccount>,
    to: &Account<'info, TokenAccount>,
    authority: &Signer<'info>,
    amount: u64,
) -> Result<()> {
    token::transfer(
        CpiContext::new(
            token_program.to_account_info(),
            Transfer {
                from: from.to_account_info(),
                to: to.to_account_info(),
                authority: authority.to_account_info(),
            },
        ),
        amount,
    )
}

/// True for a plausible `yyyymmdd` calendar date (month 1..12, day 1..31, year 2000..2999).
pub fn valid_ymd(date_ymd: u32) -> bool {
    let (y, m, d) = (date_ymd / 10_000, (date_ymd / 100) % 100, date_ymd % 100);
    (2000..3000).contains(&y) && (1..=12).contains(&m) && (1..=31).contains(&d)
}

/// Adds each member's weighted share of `pool` to their MusicianProfile pending claims.
/// `remaining` must be the writable member profile PDAs in BandVault order.
fn credit_members<'info>(
    program_id: &Pubkey,
    remaining: &'info [AccountInfo<'info>],
    band: &BandVault,
    pool: u64,
) -> Result<()> {
    credit_split(program_id, remaining, &band.members, &band.weights_bps, pool)
}

/// Adds `amount` × weight (dust to the last member) to each member's MusicianProfile pending
/// claims. `remaining` must be the writable member profile PDAs in `members` order.
fn credit_split<'info>(
    program_id: &Pubkey,
    remaining: &'info [AccountInfo<'info>],
    members: &[Pubkey],
    weights_bps: &[u16],
    amount: u64,
) -> Result<()> {
    require!(remaining.len() == members.len(), Eh8sError::MemberMismatch);
    let shares = split_members(amount, weights_bps)?;
    for ((info, member), share) in remaining.iter().zip(members.iter()).zip(shares.iter()) {
        require!(info.is_writable, Eh8sError::MemberMismatch);
        let mut profile: Account<'info, MusicianProfile> = Account::try_from(info)?;
        require!(profile.authority == *member, Eh8sError::MemberMismatch);
        let expected = Pubkey::create_program_address(
            &[MUSICIAN_SEED, member.as_ref(), &[profile.bump]],
            program_id,
        )
        .map_err(|_| Eh8sError::MemberMismatch)?;
        require_keys_eq!(expected, info.key(), Eh8sError::MemberMismatch);
        profile.pending_claims_usdc = profile
            .pending_claims_usdc
            .checked_add(*share)
            .ok_or(Eh8sError::MathOverflow)?;
        profile.exit(program_id)?;
    }
    Ok(())
}

/// Moves `amount` out of a booking escrow, signed by its VenueAccessToken PDA.
fn escrow_out<'info>(
    token_program: &Program<'info, Token>,
    escrow: &Account<'info, TokenAccount>,
    to: &Account<'info, TokenAccount>,
    access: &Account<'info, VenueAccessToken>,
    seeds: &[&[u8]],
    amount: u64,
) -> Result<()> {
    token::transfer(
        CpiContext::new_with_signer(
            token_program.to_account_info(),
            Transfer {
                from: escrow.to_account_info(),
                to: to.to_account_info(),
                authority: access.to_account_info(),
            },
            &[seeds],
        ),
        amount,
    )
}

/// Closes an empty booking escrow; its rent goes to `destination`.
fn close_escrow<'info>(
    token_program: &Program<'info, Token>,
    escrow: &Account<'info, TokenAccount>,
    destination: &AccountInfo<'info>,
    access: &Account<'info, VenueAccessToken>,
    seeds: &[&[u8]],
) -> Result<()> {
    token::close_account(CpiContext::new_with_signer(
        token_program.to_account_info(),
        CloseAccount {
            account: escrow.to_account_info(),
            destination: destination.clone(),
            authority: access.to_account_info(),
        },
        &[seeds],
    ))
}

#[account]
#[derive(InitSpace)]
pub struct Eh8sConfig {
    pub owner: Pubkey,
    pub usdc_mint: Pubkey,
    pub treasury_usdc: Pubkey,
    pub vault_usdc: Pubkey,
    pub protocol_fee_bps: u16,
    pub vault_bump: u8,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct MusicianProfile {
    pub authority: Pubkey,
    pub enigma_level: u8,
    pub instrument_code: u8,
    pub pending_claims_usdc: u64,
    pub bump: u8,
    /// ISO-3166 alpha-3; last field so v0.5 profiles (without it) decode after a zero-filled grow.
    pub country: [u8; 3],
}

#[account]
#[derive(InitSpace)]
pub struct AgentAuthority {
    pub agent: Pubkey,
    pub permissions: u8,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct Governance {
    #[max_len(5)]
    pub signers: Vec<Pubkey>,
    pub threshold: u8,
    /// Bumped on every signer-set change; proposals from an older epoch are stale.
    pub epoch: u32,
    pub proposal_count: u64,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct Proposal {
    pub id: u64,
    pub epoch: u32,
    pub proposer: Pubkey,
    pub kind: u8,
    pub amount_usdc: u64,
    pub target: Pubkey,
    pub permissions: u8,
    #[max_len(5)]
    pub new_signers: Vec<Pubkey>,
    pub new_threshold: u8,
    #[max_len(5)]
    pub approvals: Vec<Pubkey>,
    pub executed: bool,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct AcademySubscription {
    pub musician: Pubkey,
    pub plan_code: u8,
    pub months_paid: u16,
    /// Last payment (price x months).
    pub amount_usdc: u64,
    pub started_at: i64,
    pub expires_at: i64,
    pub active: u8,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct ConcertSettlement {
    pub venue: Pubkey,
    pub concert_id: u64,
    pub gross_usdc: u64,
    pub expenses_usdc: u64,
    pub eh8s_fee_usdc: u64,
    pub band_pool_usdc: u64,
    pub band: Pubkey,
    pub settled: u8,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct BandVault {
    pub band_id: u64,
    #[max_len(8)]
    pub members: Vec<Pubkey>,
    #[max_len(8)]
    pub weights_bps: Vec<u16>,
    pub weights_version: u32,
    pub bump: u8,
}

/// Verified venue on the Stage Map (`["venue", venue_wallet, venue_id]`).
#[account]
#[derive(InitSpace)]
pub struct VenueListing {
    pub venue: Pubkey,
    pub venue_id: u64,
    #[max_len(64)]
    pub name: String,
    pub country: [u8; 3],
    pub capacity: u32,
    pub contract_type: u8,
    /// 0 pending, 1 approved.
    pub status: u8,
    pub bump: u8,
}

/// Booking contract for one band on one date (`["access", venue_wallet, band_id, date_ymd]`).
/// Owns the escrow token account `["escrow", access_token]`.
#[account]
#[derive(InitSpace)]
pub struct VenueAccessToken {
    pub venue: Pubkey,
    pub venue_listing: Pubkey,
    pub band_id: u64,
    pub concert_id: u64,
    pub date_ymd: u32,
    pub gross_usdc: u64,
    /// SHA-256 of the canonical contract text; zero until confirmed.
    pub contract_hash: [u8; 32],
    /// 0 proposed, 1 confirmed, 2 settled.
    pub status: u8,
    pub bump: u8,
    pub escrow_bump: u8,
}

/// Per-song royalty pool (`["royalty", track_id]`); members and splits in pool order.
#[account]
#[derive(InitSpace)]
pub struct RoyaltyPool {
    pub track_id: u64,
    #[max_len(8)]
    pub members: Vec<Pubkey>,
    #[max_len(8)]
    pub splits_bps: Vec<u16>,
    /// Royalties deposited by WAVE / owner.
    pub total_usdc: u64,
    /// Gross sync-license payments (fee included).
    pub sync_total_usdc: u64,
    pub bump: u8,
}

/// One paid sync-license deal (`["sync", track_id, deal_id]`).
#[account]
#[derive(InitSpace)]
pub struct SyncLicense {
    pub track_id: u64,
    pub deal_id: u64,
    pub payer: Pubkey,
    pub amount_usdc: u64,
    pub eh8s_fee_usdc: u64,
    pub artist_usdc: u64,
    pub paid_at: i64,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct GeographicSubscription {
    pub authority: Pubkey,
    #[max_len(24)]
    pub geo_code: String,
    pub tier_code: u8,
    pub months_paid: u16,
    /// Last payment (price x months).
    pub amount_usdc: u64,
    pub started_at: i64,
    pub expires_at: i64,
    pub active: u8,
    pub bump: u8,
}

#[derive(Accounts)]
pub struct InitializeConfig<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    #[account(
        init,
        payer = authority,
        space = 8 + Eh8sConfig::INIT_SPACE,
        seeds = [b"eh8s".as_ref(), CONFIG_SEED],
        bump
    )]
    pub config: Account<'info, Eh8sConfig>,
    pub usdc_mint: Account<'info, Mint>,
    /// CHECK: PDA that owns the vault ATA; never read.
    #[account(seeds = [VAULT_SEED], bump)]
    pub vault_authority: UncheckedAccount<'info>,
    #[account(
        init,
        payer = authority,
        associated_token::mint = usdc_mint,
        associated_token::authority = vault_authority
    )]
    pub vault_usdc: Account<'info, TokenAccount>,
    #[account(constraint = program.programdata_address()? == Some(program_data.key()) @ Eh8sError::Unauthorized)]
    pub program: Program<'info, crate::program::Eh8sDevnet>,
    #[account(constraint = program_data.upgrade_authority_address == Some(authority.key()) @ Eh8sError::Unauthorized)]
    pub program_data: Account<'info, ProgramData>,
    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct InitTreasury<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    #[account(mut, seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(address = config.usdc_mint @ Eh8sError::InvalidMint)]
    pub usdc_mint: Account<'info, Mint>,
    /// CHECK: PDA that owns the treasury ATA; never read.
    #[account(seeds = [TREASURY_SEED], bump)]
    pub treasury_authority: UncheckedAccount<'info>,
    #[account(
        init_if_needed,
        payer = authority,
        associated_token::mint = usdc_mint,
        associated_token::authority = treasury_authority
    )]
    pub treasury_usdc: Account<'info, TokenAccount>,
    #[account(constraint = program.programdata_address()? == Some(program_data.key()) @ Eh8sError::Unauthorized)]
    pub program: Program<'info, crate::program::Eh8sDevnet>,
    #[account(constraint = program_data.upgrade_authority_address == Some(authority.key()) @ Eh8sError::Unauthorized)]
    pub program_data: Account<'info, ProgramData>,
    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct WithdrawTreasury<'info> {
    #[account(address = config.owner @ Eh8sError::Unauthorized)]
    pub owner: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    /// CHECK: PDA signer for the treasury ATA.
    #[account(seeds = [TREASURY_SEED], bump)]
    pub treasury_authority: UncheckedAccount<'info>,
    #[account(
        mut,
        address = config.treasury_usdc @ Eh8sError::InvalidTreasury,
        constraint = treasury_usdc.owner == treasury_authority.key() @ Eh8sError::InvalidTreasury
    )]
    pub treasury_usdc: Account<'info, TokenAccount>,
    #[account(
        mut,
        constraint = owner_usdc.mint == config.usdc_mint @ Eh8sError::InvalidMint,
        constraint = owner_usdc.owner == config.owner @ Eh8sError::Unauthorized
    )]
    pub owner_usdc: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
    /// CHECK: `["governance"]` PDA; this direct path only runs while it is still empty.
    #[account(seeds = [GOVERNANCE_SEED], bump)]
    pub governance: UncheckedAccount<'info>,
}

#[derive(Accounts)]
pub struct UpsertMusicianProfile<'info> {
    #[account(mut)]
    pub musician: Signer<'info>,
    /// CHECK: the musician's own `["musician", wallet]` PDA; created, grown, and decoded in the handler.
    #[account(mut, seeds = [MUSICIAN_SEED, musician.key().as_ref()], bump)]
    pub musician_profile: UncheckedAccount<'info>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
#[instruction(agent_wallet: Pubkey)]
pub struct AuthorizeAgent<'info> {
    #[account(mut, address = config.owner @ Eh8sError::Unauthorized)]
    pub owner: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        init_if_needed,
        payer = owner,
        space = 8 + AgentAuthority::INIT_SPACE,
        seeds = [AGENT_SEED, agent_wallet.as_ref()],
        bump
    )]
    pub agent_authority: Account<'info, AgentAuthority>,
    pub system_program: Program<'info, System>,
    /// CHECK: `["governance"]` PDA; this direct path only runs while it is still empty.
    #[account(seeds = [GOVERNANCE_SEED], bump)]
    pub governance: UncheckedAccount<'info>,
}

#[derive(Accounts)]
pub struct InitGovernance<'info> {
    #[account(mut, address = config.owner @ Eh8sError::Unauthorized)]
    pub owner: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        init,
        payer = owner,
        space = 8 + Governance::INIT_SPACE,
        seeds = [GOVERNANCE_SEED],
        bump
    )]
    pub governance: Account<'info, Governance>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct Propose<'info> {
    #[account(mut)]
    pub proposer: Signer<'info>,
    #[account(mut, seeds = [GOVERNANCE_SEED], bump = governance.bump)]
    pub governance: Account<'info, Governance>,
    #[account(
        init,
        payer = proposer,
        space = 8 + Proposal::INIT_SPACE,
        seeds = [PROPOSAL_SEED, &governance.proposal_count.to_le_bytes()],
        bump
    )]
    pub proposal: Account<'info, Proposal>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ApproveProposal<'info> {
    pub approver: Signer<'info>,
    #[account(seeds = [GOVERNANCE_SEED], bump = governance.bump)]
    pub governance: Account<'info, Governance>,
    #[account(mut, seeds = [PROPOSAL_SEED, &proposal.id.to_le_bytes()], bump = proposal.bump)]
    pub proposal: Account<'info, Proposal>,
}

#[derive(Accounts)]
pub struct ExecuteWithdrawProposal<'info> {
    pub executor: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(seeds = [GOVERNANCE_SEED], bump = governance.bump)]
    pub governance: Account<'info, Governance>,
    #[account(mut, seeds = [PROPOSAL_SEED, &proposal.id.to_le_bytes()], bump = proposal.bump)]
    pub proposal: Account<'info, Proposal>,
    /// CHECK: PDA signer for the treasury ATA.
    #[account(seeds = [TREASURY_SEED], bump)]
    pub treasury_authority: UncheckedAccount<'info>,
    #[account(
        mut,
        address = config.treasury_usdc @ Eh8sError::InvalidTreasury,
        constraint = treasury_usdc.owner == treasury_authority.key() @ Eh8sError::InvalidTreasury
    )]
    pub treasury_usdc: Account<'info, TokenAccount>,
    #[account(
        mut,
        address = proposal.target @ Eh8sError::InvalidProposal,
        constraint = destination_usdc.mint == config.usdc_mint @ Eh8sError::InvalidMint,
        constraint = governance.signers.contains(&destination_usdc.owner) @ Eh8sError::DestinationNotSigner
    )]
    pub destination_usdc: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct ExecuteAgentProposal<'info> {
    #[account(mut)]
    pub executor: Signer<'info>,
    #[account(seeds = [GOVERNANCE_SEED], bump = governance.bump)]
    pub governance: Account<'info, Governance>,
    #[account(mut, seeds = [PROPOSAL_SEED, &proposal.id.to_le_bytes()], bump = proposal.bump)]
    pub proposal: Account<'info, Proposal>,
    #[account(
        init_if_needed,
        payer = executor,
        space = 8 + AgentAuthority::INIT_SPACE,
        seeds = [AGENT_SEED, proposal.target.as_ref()],
        bump
    )]
    pub agent_authority: Account<'info, AgentAuthority>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ExecuteSignersProposal<'info> {
    pub executor: Signer<'info>,
    #[account(mut, seeds = [GOVERNANCE_SEED], bump = governance.bump)]
    pub governance: Account<'info, Governance>,
    #[account(mut, seeds = [PROPOSAL_SEED, &proposal.id.to_le_bytes()], bump = proposal.bump)]
    pub proposal: Account<'info, Proposal>,
}

#[derive(Accounts)]
pub struct UpdateMusicianLevel<'info> {
    pub authority: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        mut,
        seeds = [MUSICIAN_SEED, musician_profile.authority.as_ref()],
        bump = musician_profile.bump
    )]
    pub musician_profile: Account<'info, MusicianProfile>,
    #[account(seeds = [AGENT_SEED, authority.key().as_ref()], bump = agent_authority.bump)]
    pub agent_authority: Option<Account<'info, AgentAuthority>>,
}

#[derive(Accounts)]
pub struct SubscribeAcademy<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        init_if_needed,
        payer = payer,
        space = 8 + AcademySubscription::INIT_SPACE,
        seeds = [ACADEMY_SUB_SEED, payer.key().as_ref()],
        bump
    )]
    pub academy_subscription: Account<'info, AcademySubscription>,
    #[account(mut, constraint = payer_usdc.mint == config.usdc_mint @ Eh8sError::InvalidMint)]
    pub payer_usdc: Account<'info, TokenAccount>,
    #[account(mut, address = config.treasury_usdc @ Eh8sError::InvalidTreasury)]
    pub treasury_usdc: Account<'info, TokenAccount>,
    #[account(
        mut,
        constraint = instructor_usdc.mint == config.usdc_mint @ Eh8sError::InvalidMint,
        constraint = instructor_usdc.owner != payer.key() @ Eh8sError::Unauthorized
    )]
    pub instructor_usdc: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
#[instruction(band_id: u64)]
pub struct CreateBand<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        init,
        payer = authority,
        space = 8 + BandVault::INIT_SPACE,
        seeds = [BAND_SEED, &band_id.to_le_bytes()],
        bump
    )]
    pub band_vault: Account<'info, BandVault>,
    pub system_program: Program<'info, System>,
    #[account(seeds = [AGENT_SEED, authority.key().as_ref()], bump = agent_authority.bump)]
    pub agent_authority: Option<Account<'info, AgentAuthority>>,
}

#[derive(Accounts)]
pub struct UpdateSppWeights<'info> {
    pub authority: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        mut,
        seeds = [BAND_SEED, &band_vault.band_id.to_le_bytes()],
        bump = band_vault.bump
    )]
    pub band_vault: Account<'info, BandVault>,
    #[account(seeds = [AGENT_SEED, authority.key().as_ref()], bump = agent_authority.bump)]
    pub agent_authority: Option<Account<'info, AgentAuthority>>,
}

#[derive(Accounts)]
#[instruction(concert_id: u64)]
pub struct SettleConcert<'info> {
    #[account(mut)]
    pub venue: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        seeds = [BAND_SEED, &band_vault.band_id.to_le_bytes()],
        bump = band_vault.bump
    )]
    pub band_vault: Account<'info, BandVault>,
    #[account(
        init_if_needed,
        payer = venue,
        space = 8 + ConcertSettlement::INIT_SPACE,
        seeds = [CONCERT_SEED, venue.key().as_ref(), &concert_id.to_le_bytes()],
        bump
    )]
    pub concert_settlement: Account<'info, ConcertSettlement>,
    #[account(
        mut,
        constraint = venue_usdc.mint == config.usdc_mint @ Eh8sError::InvalidMint,
        constraint = venue_usdc.owner == venue.key() @ Eh8sError::Unauthorized
    )]
    pub venue_usdc: Account<'info, TokenAccount>,
    #[account(mut, address = config.treasury_usdc @ Eh8sError::InvalidTreasury)]
    pub treasury_usdc: Account<'info, TokenAccount>,
    #[account(mut, address = config.vault_usdc @ Eh8sError::InvalidVault)]
    pub vault_usdc: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
#[instruction(venue_id: u64)]
pub struct RegisterVenue<'info> {
    #[account(mut)]
    pub venue: Signer<'info>,
    #[account(
        init,
        payer = venue,
        space = 8 + VenueListing::INIT_SPACE,
        seeds = [VENUE_SEED, venue.key().as_ref(), &venue_id.to_le_bytes()],
        bump
    )]
    pub venue_listing: Account<'info, VenueListing>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ApproveVenue<'info> {
    pub authority: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        mut,
        seeds = [VENUE_SEED, venue_listing.venue.as_ref(), &venue_listing.venue_id.to_le_bytes()],
        bump = venue_listing.bump
    )]
    pub venue_listing: Account<'info, VenueListing>,
    #[account(seeds = [AGENT_SEED, authority.key().as_ref()], bump = agent_authority.bump)]
    pub agent_authority: Option<Account<'info, AgentAuthority>>,
}

#[derive(Accounts)]
#[instruction(concert_id: u64, band_id: u64, date_ymd: u32)]
pub struct ProposeBooking<'info> {
    #[account(mut)]
    pub venue: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        seeds = [VENUE_SEED, venue.key().as_ref(), &venue_listing.venue_id.to_le_bytes()],
        bump = venue_listing.bump,
        constraint = venue_listing.venue == venue.key() @ Eh8sError::Unauthorized
    )]
    pub venue_listing: Box<Account<'info, VenueListing>>,
    #[account(seeds = [BAND_SEED, &band_id.to_le_bytes()], bump = band_vault.bump)]
    pub band_vault: Box<Account<'info, BandVault>>,
    #[account(
        init,
        payer = venue,
        space = 8 + VenueAccessToken::INIT_SPACE,
        seeds = [ACCESS_SEED, venue.key().as_ref(), &band_id.to_le_bytes(), &date_ymd.to_le_bytes()],
        bump
    )]
    pub venue_access_token: Box<Account<'info, VenueAccessToken>>,
    #[account(address = config.usdc_mint @ Eh8sError::InvalidMint)]
    pub usdc_mint: Box<Account<'info, Mint>>,
    #[account(
        init,
        payer = venue,
        seeds = [ESCROW_SEED, venue_access_token.key().as_ref()],
        bump,
        token::mint = usdc_mint,
        token::authority = venue_access_token
    )]
    pub escrow_usdc: Box<Account<'info, TokenAccount>>,
    #[account(
        mut,
        constraint = venue_usdc.mint == config.usdc_mint @ Eh8sError::InvalidMint,
        constraint = venue_usdc.owner == venue.key() @ Eh8sError::Unauthorized
    )]
    pub venue_usdc: Box<Account<'info, TokenAccount>>,
    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ConfirmBooking<'info> {
    pub authority: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        mut,
        seeds = [
            ACCESS_SEED,
            venue_access_token.venue.as_ref(),
            &venue_access_token.band_id.to_le_bytes(),
            &venue_access_token.date_ymd.to_le_bytes()
        ],
        bump = venue_access_token.bump
    )]
    pub venue_access_token: Account<'info, VenueAccessToken>,
    #[account(seeds = [AGENT_SEED, authority.key().as_ref()], bump = agent_authority.bump)]
    pub agent_authority: Option<Account<'info, AgentAuthority>>,
}

#[derive(Accounts)]
pub struct CancelBooking<'info> {
    #[account(mut)]
    pub venue: Signer<'info>,
    #[account(
        mut,
        close = venue,
        seeds = [
            ACCESS_SEED,
            venue_access_token.venue.as_ref(),
            &venue_access_token.band_id.to_le_bytes(),
            &venue_access_token.date_ymd.to_le_bytes()
        ],
        bump = venue_access_token.bump,
        constraint = venue_access_token.venue == venue.key() @ Eh8sError::Unauthorized
    )]
    pub venue_access_token: Account<'info, VenueAccessToken>,
    #[account(
        mut,
        seeds = [ESCROW_SEED, venue_access_token.key().as_ref()],
        bump = venue_access_token.escrow_bump
    )]
    pub escrow_usdc: Account<'info, TokenAccount>,
    #[account(
        mut,
        constraint = venue_usdc.mint == escrow_usdc.mint @ Eh8sError::InvalidMint,
        constraint = venue_usdc.owner == venue.key() @ Eh8sError::Unauthorized
    )]
    pub venue_usdc: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct SettleBooking<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        mut,
        seeds = [
            ACCESS_SEED,
            venue_access_token.venue.as_ref(),
            &venue_access_token.band_id.to_le_bytes(),
            &venue_access_token.date_ymd.to_le_bytes()
        ],
        bump = venue_access_token.bump
    )]
    pub venue_access_token: Box<Account<'info, VenueAccessToken>>,
    #[account(
        seeds = [BAND_SEED, &venue_access_token.band_id.to_le_bytes()],
        bump = band_vault.bump
    )]
    pub band_vault: Box<Account<'info, BandVault>>,
    #[account(
        init_if_needed,
        payer = authority,
        space = 8 + ConcertSettlement::INIT_SPACE,
        seeds = [
            CONCERT_SEED,
            venue_access_token.venue.as_ref(),
            &venue_access_token.concert_id.to_le_bytes()
        ],
        bump
    )]
    pub concert_settlement: Box<Account<'info, ConcertSettlement>>,
    #[account(
        mut,
        seeds = [ESCROW_SEED, venue_access_token.key().as_ref()],
        bump = venue_access_token.escrow_bump
    )]
    pub escrow_usdc: Box<Account<'info, TokenAccount>>,
    #[account(
        mut,
        constraint = venue_usdc.mint == config.usdc_mint @ Eh8sError::InvalidMint,
        constraint = venue_usdc.owner == venue_access_token.venue @ Eh8sError::Unauthorized
    )]
    pub venue_usdc: Box<Account<'info, TokenAccount>>,
    #[account(mut, address = config.treasury_usdc @ Eh8sError::InvalidTreasury)]
    pub treasury_usdc: Box<Account<'info, TokenAccount>>,
    #[account(mut, address = config.vault_usdc @ Eh8sError::InvalidVault)]
    pub vault_usdc: Box<Account<'info, TokenAccount>>,
    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
    #[account(seeds = [AGENT_SEED, authority.key().as_ref()], bump = agent_authority.bump)]
    pub agent_authority: Option<Account<'info, AgentAuthority>>,
}

#[derive(Accounts)]
pub struct ClaimRoyalties<'info> {
    pub musician: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        mut,
        seeds = [MUSICIAN_SEED, musician.key().as_ref()],
        bump = musician_profile.bump,
        constraint = musician_profile.authority == musician.key() @ Eh8sError::Unauthorized
    )]
    pub musician_profile: Account<'info, MusicianProfile>,
    /// CHECK: PDA signer for the vault ATA.
    #[account(seeds = [VAULT_SEED], bump = config.vault_bump)]
    pub vault_authority: UncheckedAccount<'info>,
    #[account(mut, address = config.vault_usdc @ Eh8sError::InvalidVault)]
    pub vault_usdc: Account<'info, TokenAccount>,
    #[account(
        mut,
        constraint = musician_usdc.mint == config.usdc_mint @ Eh8sError::InvalidMint,
        constraint = musician_usdc.owner == musician.key() @ Eh8sError::Unauthorized
    )]
    pub musician_usdc: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
#[instruction(track_id: u64)]
pub struct CreateRoyaltyPool<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        init,
        payer = authority,
        space = 8 + RoyaltyPool::INIT_SPACE,
        seeds = [ROYALTY_SEED, &track_id.to_le_bytes()],
        bump
    )]
    pub royalty_pool: Account<'info, RoyaltyPool>,
    pub system_program: Program<'info, System>,
    #[account(seeds = [AGENT_SEED, authority.key().as_ref()], bump = agent_authority.bump)]
    pub agent_authority: Option<Account<'info, AgentAuthority>>,
}

#[derive(Accounts)]
#[instruction(track_id: u64)]
pub struct DepositRoyalties<'info> {
    pub depositor: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        mut,
        seeds = [ROYALTY_SEED, &track_id.to_le_bytes()],
        bump = royalty_pool.bump
    )]
    pub royalty_pool: Account<'info, RoyaltyPool>,
    #[account(mut, constraint = depositor_usdc.mint == config.usdc_mint @ Eh8sError::InvalidMint)]
    pub depositor_usdc: Account<'info, TokenAccount>,
    #[account(mut, address = config.vault_usdc @ Eh8sError::InvalidVault)]
    pub vault_usdc: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
    #[account(seeds = [AGENT_SEED, depositor.key().as_ref()], bump = agent_authority.bump)]
    pub agent_authority: Option<Account<'info, AgentAuthority>>,
}

#[derive(Accounts)]
#[instruction(track_id: u64, deal_id: u64)]
pub struct PaySyncLicense<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Eh8sConfig>>,
    #[account(
        mut,
        seeds = [ROYALTY_SEED, &track_id.to_le_bytes()],
        bump = royalty_pool.bump
    )]
    pub royalty_pool: Box<Account<'info, RoyaltyPool>>,
    #[account(
        init,
        payer = payer,
        space = 8 + SyncLicense::INIT_SPACE,
        seeds = [SYNC_SEED, &track_id.to_le_bytes(), &deal_id.to_le_bytes()],
        bump
    )]
    pub sync_license: Box<Account<'info, SyncLicense>>,
    #[account(mut, constraint = payer_usdc.mint == config.usdc_mint @ Eh8sError::InvalidMint)]
    pub payer_usdc: Box<Account<'info, TokenAccount>>,
    #[account(mut, address = config.treasury_usdc @ Eh8sError::InvalidTreasury)]
    pub treasury_usdc: Box<Account<'info, TokenAccount>>,
    #[account(mut, address = config.vault_usdc @ Eh8sError::InvalidVault)]
    pub vault_usdc: Box<Account<'info, TokenAccount>>,
    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
#[instruction(geo_code: String)]
pub struct SubscribeGeographic<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,
    #[account(seeds = [b"eh8s".as_ref(), CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Eh8sConfig>,
    #[account(
        init_if_needed,
        payer = payer,
        space = 8 + GeographicSubscription::INIT_SPACE,
        seeds = [GEO_SUB_SEED, payer.key().as_ref(), geo_code.as_bytes()],
        bump
    )]
    pub geo_subscription: Account<'info, GeographicSubscription>,
    #[account(mut, constraint = payer_usdc.mint == config.usdc_mint @ Eh8sError::InvalidMint)]
    pub payer_usdc: Account<'info, TokenAccount>,
    #[account(mut, address = config.treasury_usdc @ Eh8sError::InvalidTreasury)]
    pub treasury_usdc: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

#[error_code]
pub enum Eh8sError {
    #[msg("Invalid protocol fee")]
    InvalidFee,
    #[msg("Amount must be > 0")]
    ZeroAmount,
    #[msg("Math overflow")]
    MathOverflow,
    #[msg("Insufficient pending claim balance")]
    InsufficientClaim,
    #[msg("Unauthorized")]
    Unauthorized,
    #[msg("Token account mint is not the configured USDC mint")]
    InvalidMint,
    #[msg("Treasury account is not the configured treasury ATA")]
    InvalidTreasury,
    #[msg("Vault account is not the configured program vault ATA")]
    InvalidVault,
    #[msg("Concert already settled")]
    AlreadySettled,
    #[msg("Band members must be 1..8 distinct wallets")]
    InvalidMembers,
    #[msg("Weights must match the member count and sum to 10000 bps")]
    InvalidWeights,
    #[msg("Remaining accounts must be the band member profiles in vault order")]
    MemberMismatch,
    #[msg("Treasury balance is lower than the withdraw amount")]
    InsufficientTreasury,
    #[msg("Enigma level must be 0..5")]
    InvalidLevel,
    #[msg("Unknown agent permission bits or empty agent wallet")]
    InvalidPermissions,
    #[msg("Country must be three uppercase ASCII letters (ISO-3166 alpha-3)")]
    InvalidCountry,
    #[msg("Venue name must be 1..64 bytes, capacity > 0, contract type 1..4")]
    InvalidVenue,
    #[msg("Venue listing is not approved")]
    VenueNotApproved,
    #[msg("Booking date must be yyyymmdd and the contract hash non-zero")]
    InvalidBooking,
    #[msg("Booking is not in the status this instruction needs")]
    InvalidBookingStatus,
    #[msg("Settle gross must equal the booking gross held in escrow")]
    EscrowMismatch,
    #[msg("Governance is active: this action needs an executed proposal")]
    GovernanceActive,
    #[msg("Signer is not a governance signer")]
    NotGovernanceSigner,
    #[msg("Governance needs 1..5 distinct signers and 1 <= threshold <= signers")]
    InvalidGovernance,
    #[msg("Unknown proposal kind, empty target, or wrong execute instruction")]
    InvalidProposal,
    #[msg("This signer already approved the proposal")]
    AlreadyApproved,
    #[msg("Proposal already executed")]
    ProposalExecuted,
    #[msg("Approvals from current signers are below the threshold")]
    ThresholdNotMet,
    #[msg("The signer set changed after this proposal; open a new one")]
    StaleProposal,
    #[msg("Withdraw destination must be a USDC account owned by a governance signer")]
    DestinationNotSigner,
    #[msg("Academy plan must be 1..3 and months 1..12")]
    InvalidPlan,
    #[msg("Geo tier must be 1..5, months 1..12, and the geo code 2..24 of A-Z 0-9 _")]
    InvalidGeoSubscription,
}
