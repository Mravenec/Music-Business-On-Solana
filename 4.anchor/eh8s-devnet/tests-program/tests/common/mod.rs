#![allow(dead_code)]

use anchor_lang::{AccountDeserialize, InstructionData, ToAccountMetas};
use anchor_spl::associated_token::{
    get_associated_token_address, spl_associated_token_account,
};
use anchor_spl::token::spl_token;
use solana_program_test::{BanksClientError, ProgramTest, ProgramTestContext};
use solana_sdk::{
    account::Account,
    bpf_loader_upgradeable::{self, UpgradeableLoaderState},
    instruction::{Instruction, InstructionError},
    program_pack::Pack,
    pubkey::Pubkey,
    rent::Rent,
    signature::{Keypair, Signer},
    system_instruction, system_program,
    transaction::{Transaction, TransactionError},
};

pub const USDC: u64 = 1_000_000;
pub const FEE_BPS: u16 = 1_500;

pub fn program_id() -> Pubkey {
    eh8s_devnet::ID
}

pub fn config_pda() -> Pubkey {
    Pubkey::find_program_address(&[b"eh8s", b"config"], &program_id()).0
}

pub fn vault_authority() -> Pubkey {
    Pubkey::find_program_address(&[b"vault"], &program_id()).0
}

pub fn musician_pda(wallet: &Pubkey) -> Pubkey {
    Pubkey::find_program_address(&[b"musician", wallet.as_ref()], &program_id()).0
}

pub fn concert_pda(venue: &Pubkey, concert_id: u64) -> Pubkey {
    Pubkey::find_program_address(
        &[b"concert", venue.as_ref(), &concert_id.to_le_bytes()],
        &program_id(),
    )
    .0
}

pub fn academy_pda(payer: &Pubkey) -> Pubkey {
    Pubkey::find_program_address(&[b"academy_sub", payer.as_ref()], &program_id()).0
}

pub fn geo_pda(payer: &Pubkey, geo_code: &str) -> Pubkey {
    Pubkey::find_program_address(&[b"geo_sub", payer.as_ref(), geo_code.as_bytes()], &program_id()).0
}

pub fn song_pool_pda(track_id: u64) -> Pubkey {
    Pubkey::find_program_address(&[b"royalty", &track_id.to_le_bytes()], &program_id()).0
}

pub fn sync_pda(track_id: u64, deal_id: u64) -> Pubkey {
    Pubkey::find_program_address(
        &[b"sync", &track_id.to_le_bytes(), &deal_id.to_le_bytes()],
        &program_id(),
    )
    .0
}

pub fn treasury_authority() -> Pubkey {
    Pubkey::find_program_address(&[b"treasury"], &program_id()).0
}

pub fn programdata_address() -> Pubkey {
    Pubkey::find_program_address(&[program_id().as_ref()], &bpf_loader_upgradeable::id()).0
}

/// Custom Anchor error code for an `Eh8sError` variant index (offset 6000).
pub fn eh8s_code(variant_index: u32) -> u32 {
    6000 + variant_index
}

pub const E_INVALID_FEE: u32 = 0;
pub const E_ZERO: u32 = 1;
pub const E_INSUFFICIENT_CLAIM: u32 = 3;
pub const E_UNAUTHORIZED: u32 = 4;
pub const E_INVALID_MINT: u32 = 5;
pub const E_INVALID_TREASURY: u32 = 6;
pub const E_INVALID_VAULT: u32 = 7;
pub const E_ALREADY_SETTLED: u32 = 8;
pub const E_INVALID_MEMBERS: u32 = 9;
pub const E_INVALID_WEIGHTS: u32 = 10;
pub const E_MEMBER_MISMATCH: u32 = 11;
pub const E_INSUFFICIENT_TREASURY: u32 = 12;
pub const E_INVALID_LEVEL: u32 = 13;
pub const E_INVALID_PERMISSIONS: u32 = 14;
pub const E_INVALID_COUNTRY: u32 = 15;

pub const PERM_PEDAGOGICAL: u8 = 0x01;
pub const PERM_HARMONY: u8 = 0x02;
pub const PERM_STAGE: u8 = 0x04;
pub const PERM_VAULT: u8 = 0x08;
pub const PERM_WAVE: u8 = 0x10;

pub fn agent_pda(wallet: &Pubkey) -> Pubkey {
    Pubkey::find_program_address(&[b"agent", wallet.as_ref()], &program_id()).0
}

pub struct Env {
    pub ctx: ProgramTestContext,
    pub authority: Keypair,
    pub owner: Keypair,
    pub mint_authority: Keypair,
    pub mint: Pubkey,
    pub treasury_usdc: Pubkey,
    pub vault_usdc: Pubkey,
}

fn funded(pt: &mut ProgramTest, kp: &Keypair) {
    pt.add_account(
        kp.pubkey(),
        Account {
            lamports: 100_000_000_000,
            data: vec![],
            owner: system_program::id(),
            executable: false,
            rent_epoch: 0,
        },
    );
}

/// Loads the built `.so` as an upgradeable program owned by `authority`.
fn add_upgradeable_program(pt: &mut ProgramTest, authority: &Pubkey) {
    let dir = std::env::var("SBF_OUT_DIR").expect("SBF_OUT_DIR must point at target/deploy");
    let elf = std::fs::read(format!("{dir}/eh8s_devnet.so")).expect("eh8s_devnet.so not built");
    let rent = Rent::default();
    let programdata = programdata_address();

    let program_data = bincode::serialize(&UpgradeableLoaderState::Program {
        programdata_address: programdata,
    })
    .unwrap();
    pt.add_account(
        program_id(),
        Account {
            lamports: rent.minimum_balance(program_data.len()),
            data: program_data,
            owner: bpf_loader_upgradeable::id(),
            executable: true,
            rent_epoch: 0,
        },
    );

    let mut data = bincode::serialize(&UpgradeableLoaderState::ProgramData {
        slot: 0,
        upgrade_authority_address: Some(*authority),
    })
    .unwrap();
    data.resize(UpgradeableLoaderState::size_of_programdata_metadata(), 0);
    data.extend_from_slice(&elf);
    pt.add_account(
        programdata,
        Account {
            lamports: rent.minimum_balance(data.len()),
            data,
            owner: bpf_loader_upgradeable::id(),
            executable: false,
            rent_epoch: 0,
        },
    );
}

impl Env {
    /// Starts a bank with the program, a USDC-like mint, the owner treasury ATA,
    /// and an initialized config (fee 15%).
    pub async fn start() -> Env {
        let authority = Keypair::new();
        let owner = Keypair::new();
        let mint_authority = Keypair::new();
        let mut pt = ProgramTest::default();
        pt.prefer_bpf(true);
        add_upgradeable_program(&mut pt, &authority.pubkey());
        funded(&mut pt, &authority);
        funded(&mut pt, &owner);
        funded(&mut pt, &mint_authority);
        let ctx = pt.start_with_context().await;

        let mut env = Env {
            ctx,
            authority,
            owner,
            mint_authority,
            mint: Pubkey::default(),
            treasury_usdc: Pubkey::default(),
            vault_usdc: Pubkey::default(),
        };
        env.mint = env.create_mint().await;
        env.treasury_usdc = env.create_ata(&env.owner.pubkey()).await;
        env.vault_usdc = get_associated_token_address(&vault_authority(), &env.mint);
        let ix = env.initialize_config_ix(&env.authority.pubkey(), env.mint);
        let authority = env.authority.insecure_clone();
        env.send(&[ix], &[&authority]).await.expect("initialize_config");
        env
    }

    pub fn initialize_config_ix(&self, signer: &Pubkey, mint: Pubkey) -> Instruction {
        Instruction {
            program_id: program_id(),
            accounts: eh8s_devnet::accounts::InitializeConfig {
                authority: *signer,
                config: config_pda(),
                usdc_mint: mint,
                vault_authority: vault_authority(),
                vault_usdc: get_associated_token_address(&vault_authority(), &mint),
                program: program_id(),
                program_data: programdata_address(),
                token_program: spl_token::id(),
                associated_token_program: spl_associated_token_account::id(),
                system_program: system_program::id(),
            }
            .to_account_metas(None),
            data: eh8s_devnet::instruction::InitializeConfig {
                owner: self.owner.pubkey(),
                protocol_fee_bps: FEE_BPS,
            }
            .data(),
        }
    }

    pub async fn send(
        &mut self,
        ixs: &[Instruction],
        signers: &[&Keypair],
    ) -> Result<(), BanksClientError> {
        let blockhash = self.ctx.get_new_latest_blockhash().await.unwrap();
        let payer = signers[0];
        let tx = Transaction::new_signed_with_payer(ixs, Some(&payer.pubkey()), signers, blockhash);
        self.ctx.banks_client.process_transaction(tx).await
    }

    pub async fn funded_wallet(&mut self) -> Keypair {
        let kp = Keypair::new();
        let ix = system_instruction::transfer(&self.authority.pubkey(), &kp.pubkey(), 10_000_000_000);
        let authority = self.authority.insecure_clone();
        self.send(&[ix], &[&authority]).await.unwrap();
        kp
    }

    pub async fn create_mint(&mut self) -> Pubkey {
        let mint = Keypair::new();
        let rent = self.ctx.banks_client.get_rent().await.unwrap();
        let ixs = [
            system_instruction::create_account(
                &self.mint_authority.pubkey(),
                &mint.pubkey(),
                rent.minimum_balance(spl_token::state::Mint::LEN),
                spl_token::state::Mint::LEN as u64,
                &spl_token::id(),
            ),
            spl_token::instruction::initialize_mint(
                &spl_token::id(),
                &mint.pubkey(),
                &self.mint_authority.pubkey(),
                None,
                6,
            )
            .unwrap(),
        ];
        let ma = self.mint_authority.insecure_clone();
        self.send(&ixs, &[&ma, &mint]).await.unwrap();
        mint.pubkey()
    }

    pub async fn create_ata(&mut self, wallet: &Pubkey) -> Pubkey {
        let mint = self.mint;
        self.create_ata_for_mint(wallet, &mint).await
    }

    pub async fn create_ata_for_mint(&mut self, wallet: &Pubkey, mint: &Pubkey) -> Pubkey {
        let ix = spl_associated_token_account::instruction::create_associated_token_account(
            &self.mint_authority.pubkey(),
            wallet,
            mint,
            &spl_token::id(),
        );
        let ma = self.mint_authority.insecure_clone();
        self.send(&[ix], &[&ma]).await.unwrap();
        get_associated_token_address(wallet, mint)
    }

    pub async fn mint_to(&mut self, mint: &Pubkey, ata: &Pubkey, amount: u64) {
        let ix = spl_token::instruction::mint_to(
            &spl_token::id(),
            mint,
            ata,
            &self.mint_authority.pubkey(),
            &[],
            amount,
        )
        .unwrap();
        let ma = self.mint_authority.insecure_clone();
        self.send(&[ix], &[&ma]).await.unwrap();
    }

    /// Wallet with SOL, a USDC ATA holding `usdc` units, and (optionally) a MusicianProfile.
    pub async fn wallet_with_usdc(&mut self, usdc: u64) -> (Keypair, Pubkey) {
        let kp = self.funded_wallet().await;
        let ata = self.create_ata(&kp.pubkey()).await;
        if usdc > 0 {
            let mint = self.mint;
            self.mint_to(&mint, &ata, usdc).await;
        }
        (kp, ata)
    }

    pub async fn token_balance(&mut self, ata: &Pubkey) -> u64 {
        let acc = self.ctx.banks_client.get_account(*ata).await.unwrap().unwrap();
        spl_token::state::Account::unpack(&acc.data).unwrap().amount
    }

    pub async fn fetch<T: AccountDeserialize>(&mut self, address: &Pubkey) -> T {
        let acc = self.ctx.banks_client.get_account(*address).await.unwrap().unwrap();
        T::try_deserialize(&mut acc.data.as_slice()).unwrap()
    }

    /// Musician creates or updates their own profile (instrument + "USA"); level stays agent-owned.
    pub async fn upsert_profile(&mut self, musician: &Keypair, instrument_code: u8) {
        let ix = upsert_ix(&musician.pubkey(), instrument_code, *b"USA");
        self.send(&[ix], &[musician]).await.unwrap();
    }

    /// Owner grants `permissions` to `agent_wallet` (0 revokes).
    pub async fn authorize_agent(&mut self, agent_wallet: &Pubkey, permissions: u8) {
        let owner = self.owner.insecure_clone();
        let ix = authorize_agent_ix(&owner.pubkey(), agent_wallet, permissions);
        self.send(&[ix], &[&owner]).await.expect("authorize_agent");
    }

    /// Owner creates a BandVault for `members` (each gets a MusicianProfile first).
    pub async fn band_with(&mut self, band_id: u64, members: &[&Keypair]) {
        for m in members {
            self.upsert_profile(m, 3).await;
        }
        let owner = self.owner.insecure_clone();
        let ix = create_band_ix(&owner.pubkey(), band_id, members.iter().map(|m| m.pubkey()).collect());
        self.send(&[ix], &[&owner]).await.expect("create_band");
    }

    pub async fn pending(&mut self, wallet: &Pubkey) -> u64 {
        let p: eh8s_devnet::MusicianProfile = self.fetch(&musician_pda(wallet)).await;
        p.pending_claims_usdc
    }
}

pub fn upsert_ix(musician: &Pubkey, instrument_code: u8, country: [u8; 3]) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::UpsertMusicianProfile {
            musician: *musician,
            musician_profile: musician_pda(musician),
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::UpsertMusicianProfile { instrument_code, country }.data(),
    }
}

pub fn authorize_agent_ix(owner: &Pubkey, agent_wallet: &Pubkey, permissions: u8) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::AuthorizeAgent {
            owner: *owner,
            config: config_pda(),
            agent_authority: agent_pda(agent_wallet),
            system_program: system_program::id(),
            governance: governance_pda(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::AuthorizeAgent { agent_wallet: *agent_wallet, permissions }.data(),
    }
}

/// `with_agent` passes the signer's AgentAuthority PDA (agents); owners may pass `false`.
pub fn level_ix(signer: &Pubkey, musician: &Pubkey, new_level: u8, with_agent: bool) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::UpdateMusicianLevel {
            authority: *signer,
            config: config_pda(),
            musician_profile: musician_pda(musician),
            agent_authority: with_agent.then(|| agent_pda(signer)),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::UpdateMusicianLevel { new_level }.data(),
    }
}

pub fn band_pda(band_id: u64) -> Pubkey {
    Pubkey::find_program_address(&[b"band", &band_id.to_le_bytes()], &program_id()).0
}

pub fn create_band_ix(signer: &Pubkey, band_id: u64, members: Vec<Pubkey>) -> Instruction {
    create_band_as(signer, band_id, members, false)
}

pub fn create_band_as(signer: &Pubkey, band_id: u64, members: Vec<Pubkey>, with_agent: bool) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::CreateBand {
            authority: *signer,
            config: config_pda(),
            band_vault: band_pda(band_id),
            system_program: system_program::id(),
            agent_authority: with_agent.then(|| agent_pda(signer)),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::CreateBand { band_id, members }.data(),
    }
}

pub fn update_weights_ix(signer: &Pubkey, band_id: u64, weights_bps: Vec<u16>) -> Instruction {
    update_weights_as(signer, band_id, weights_bps, false)
}

pub fn update_weights_as(signer: &Pubkey, band_id: u64, weights_bps: Vec<u16>, with_agent: bool) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::UpdateSppWeights {
            authority: *signer,
            config: config_pda(),
            band_vault: band_pda(band_id),
            agent_authority: with_agent.then(|| agent_pda(signer)),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::UpdateSppWeights { weights_bps }.data(),
    }
}

pub struct SettleAccounts {
    pub venue: Pubkey,
    pub venue_usdc: Pubkey,
    pub treasury_usdc: Pubkey,
    pub vault_usdc: Pubkey,
    pub band_id: u64,
    /// Member MusicianProfile PDAs in vault order (remaining accounts).
    pub member_profiles: Vec<Pubkey>,
}

pub fn settle_ix(a: &SettleAccounts, concert_id: u64, gross: u64) -> Instruction {
    settle_with_expenses_ix(a, concert_id, gross, 0)
}

pub fn settle_with_expenses_ix(a: &SettleAccounts, concert_id: u64, gross: u64, expenses: u64) -> Instruction {
    let mut accounts = eh8s_devnet::accounts::SettleConcert {
        venue: a.venue,
        config: config_pda(),
        band_vault: band_pda(a.band_id),
        concert_settlement: concert_pda(&a.venue, concert_id),
        venue_usdc: a.venue_usdc,
        treasury_usdc: a.treasury_usdc,
        vault_usdc: a.vault_usdc,
        token_program: spl_token::id(),
        system_program: system_program::id(),
    }
    .to_account_metas(None);
    accounts.extend(
        a.member_profiles
            .iter()
            .map(|p| solana_sdk::instruction::AccountMeta::new(*p, false)),
    );
    Instruction {
        program_id: program_id(),
        accounts,
        data: eh8s_devnet::instruction::SettleConcert {
            concert_id,
            gross_usdc: gross,
            expenses_usdc: expenses,
        }
        .data(),
    }
}

pub fn claim_ix(musician: &Pubkey, profile: &Pubkey, vault_usdc: &Pubkey, dest: &Pubkey, amount: u64) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::ClaimRoyalties {
            musician: *musician,
            config: config_pda(),
            musician_profile: *profile,
            vault_authority: vault_authority(),
            vault_usdc: *vault_usdc,
            musician_usdc: *dest,
            token_program: spl_token::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::ClaimRoyalties { amount_usdc: amount }.data(),
    }
}

pub fn create_pool_ix(signer: &Pubkey, track_id: u64, members: Vec<Pubkey>, splits_bps: Vec<u16>, with_agent: bool) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::CreateRoyaltyPool {
            authority: *signer,
            config: config_pda(),
            royalty_pool: song_pool_pda(track_id),
            system_program: system_program::id(),
            agent_authority: with_agent.then(|| agent_pda(signer)),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::CreateRoyaltyPool { track_id, members, splits_bps }.data(),
    }
}

fn with_profiles(mut accounts: Vec<solana_sdk::instruction::AccountMeta>, members: &[Pubkey]) -> Vec<solana_sdk::instruction::AccountMeta> {
    accounts.extend(
        members
            .iter()
            .map(|m| solana_sdk::instruction::AccountMeta::new(musician_pda(m), false)),
    );
    accounts
}

/// Per-song deposit; members are the pool member wallets in the order to pass their profiles.
pub fn deposit_ix(
    depositor: &Pubkey,
    depositor_usdc: &Pubkey,
    vault_usdc: &Pubkey,
    track_id: u64,
    amount: u64,
    members: &[Pubkey],
    with_agent: bool,
) -> Instruction {
    let accounts = eh8s_devnet::accounts::DepositRoyalties {
        depositor: *depositor,
        config: config_pda(),
        royalty_pool: song_pool_pda(track_id),
        depositor_usdc: *depositor_usdc,
        vault_usdc: *vault_usdc,
        token_program: spl_token::id(),
        agent_authority: with_agent.then(|| agent_pda(depositor)),
    }
    .to_account_metas(None);
    Instruction {
        program_id: program_id(),
        accounts: with_profiles(accounts, members),
        data: eh8s_devnet::instruction::DepositRoyalties { track_id, amount_usdc: amount }.data(),
    }
}

pub struct SyncAccounts {
    pub payer: Pubkey,
    pub payer_usdc: Pubkey,
    pub treasury_usdc: Pubkey,
    pub vault_usdc: Pubkey,
}

pub fn sync_ix(a: &SyncAccounts, track_id: u64, deal_id: u64, amount: u64, members: &[Pubkey]) -> Instruction {
    let accounts = eh8s_devnet::accounts::PaySyncLicense {
        payer: a.payer,
        config: config_pda(),
        royalty_pool: song_pool_pda(track_id),
        sync_license: sync_pda(track_id, deal_id),
        payer_usdc: a.payer_usdc,
        treasury_usdc: a.treasury_usdc,
        vault_usdc: a.vault_usdc,
        token_program: spl_token::id(),
        system_program: system_program::id(),
    }
    .to_account_metas(None);
    Instruction {
        program_id: program_id(),
        accounts: with_profiles(accounts, members),
        data: eh8s_devnet::instruction::PaySyncLicense { track_id, deal_id, amount_usdc: amount }.data(),
    }
}

pub fn academy_ix(
    payer: &Pubkey,
    payer_usdc: &Pubkey,
    treasury_usdc: &Pubkey,
    instructor_usdc: &Pubkey,
    plan_type: u8,
    months: u8,
) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::SubscribeAcademy {
            payer: *payer,
            config: config_pda(),
            academy_subscription: academy_pda(payer),
            payer_usdc: *payer_usdc,
            treasury_usdc: *treasury_usdc,
            instructor_usdc: *instructor_usdc,
            token_program: spl_token::id(),
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::SubscribeAcademy { plan_type, months }.data(),
    }
}

pub fn init_treasury_ix(authority: &Pubkey, mint: &Pubkey) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::InitTreasury {
            authority: *authority,
            config: config_pda(),
            usdc_mint: *mint,
            treasury_authority: treasury_authority(),
            treasury_usdc: get_associated_token_address(&treasury_authority(), mint),
            program: program_id(),
            program_data: programdata_address(),
            token_program: spl_token::id(),
            associated_token_program: spl_associated_token_account::id(),
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::InitTreasury {}.data(),
    }
}

pub fn withdraw_treasury_ix(owner: &Pubkey, treasury_usdc: &Pubkey, owner_usdc: &Pubkey, amount: u64) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::WithdrawTreasury {
            owner: *owner,
            config: config_pda(),
            treasury_authority: treasury_authority(),
            treasury_usdc: *treasury_usdc,
            owner_usdc: *owner_usdc,
            token_program: spl_token::id(),
            governance: governance_pda(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::WithdrawTreasury { amount_usdc: amount }.data(),
    }
}

pub fn geo_ix(
    payer: &Pubkey,
    payer_usdc: &Pubkey,
    treasury_usdc: &Pubkey,
    geo_code: &str,
    tier: u8,
    months: u8,
) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::SubscribeGeographic {
            payer: *payer,
            config: config_pda(),
            geo_subscription: geo_pda(payer, geo_code),
            payer_usdc: *payer_usdc,
            treasury_usdc: *treasury_usdc,
            token_program: spl_token::id(),
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::SubscribeGeographic { geo_code: geo_code.to_string(), tier, months }.data(),
    }
}

/// Asserts the transaction failed with the given Eh8sError variant index.
pub fn assert_eh8s_err(res: Result<(), BanksClientError>, variant_index: u32) {
    let code = eh8s_code(variant_index);
    match res {
        Err(BanksClientError::TransactionError(TransactionError::InstructionError(
            _,
            InstructionError::Custom(c),
        )))
        | Err(BanksClientError::SimulationError {
            err: TransactionError::InstructionError(_, InstructionError::Custom(c)),
            ..
        }) => assert_eq!(c, code, "expected Eh8sError code {code}, got {c}"),
        other => panic!("expected Eh8sError code {code}, got {other:?}"),
    }
}

/// Asserts the transaction failed (any error).
pub fn assert_failed(res: Result<(), BanksClientError>) {
    assert!(res.is_err(), "expected failure, transaction succeeded");
}

pub const E_INVALID_VENUE: u32 = 16;
pub const E_VENUE_NOT_APPROVED: u32 = 17;
pub const E_INVALID_BOOKING: u32 = 18;
pub const E_INVALID_BOOKING_STATUS: u32 = 19;
pub const E_ESCROW_MISMATCH: u32 = 20;

pub fn venue_pda(venue: &Pubkey, venue_id: u64) -> Pubkey {
    Pubkey::find_program_address(&[b"venue", venue.as_ref(), &venue_id.to_le_bytes()], &program_id()).0
}

pub fn access_pda(venue: &Pubkey, band_id: u64, date_ymd: u32) -> Pubkey {
    Pubkey::find_program_address(
        &[b"access", venue.as_ref(), &band_id.to_le_bytes(), &date_ymd.to_le_bytes()],
        &program_id(),
    )
    .0
}

pub fn escrow_pda(access: &Pubkey) -> Pubkey {
    Pubkey::find_program_address(&[b"escrow", access.as_ref()], &program_id()).0
}

pub fn register_venue_ix(venue: &Pubkey, venue_id: u64, name: &str, country: [u8; 3], capacity: u32, contract_type: u8) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::RegisterVenue {
            venue: *venue,
            venue_listing: venue_pda(venue, venue_id),
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::RegisterVenue {
            venue_id,
            name: name.to_string(),
            country,
            capacity,
            contract_type,
        }
        .data(),
    }
}

pub fn approve_venue_ix(signer: &Pubkey, listing: &Pubkey, with_agent: bool) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::ApproveVenue {
            authority: *signer,
            config: config_pda(),
            venue_listing: *listing,
            agent_authority: with_agent.then(|| agent_pda(signer)),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::ApproveVenue {}.data(),
    }
}

pub struct Booking {
    pub venue_id: u64,
    pub concert_id: u64,
    pub band_id: u64,
    pub date_ymd: u32,
    pub gross: u64,
}

pub fn propose_booking_ix(venue: &Pubkey, venue_usdc: &Pubkey, mint: &Pubkey, b: &Booking) -> Instruction {
    let access = access_pda(venue, b.band_id, b.date_ymd);
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::ProposeBooking {
            venue: *venue,
            config: config_pda(),
            venue_listing: venue_pda(venue, b.venue_id),
            band_vault: band_pda(b.band_id),
            venue_access_token: access,
            usdc_mint: *mint,
            escrow_usdc: escrow_pda(&access),
            venue_usdc: *venue_usdc,
            token_program: spl_token::id(),
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::ProposeBooking {
            concert_id: b.concert_id,
            band_id: b.band_id,
            date_ymd: b.date_ymd,
            gross_usdc: b.gross,
        }
        .data(),
    }
}

pub fn confirm_booking_ix(signer: &Pubkey, access: &Pubkey, contract_hash: [u8; 32], with_agent: bool) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::ConfirmBooking {
            authority: *signer,
            config: config_pda(),
            venue_access_token: *access,
            agent_authority: with_agent.then(|| agent_pda(signer)),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::ConfirmBooking { contract_hash }.data(),
    }
}

pub fn cancel_booking_ix(venue: &Pubkey, access: &Pubkey, venue_usdc: &Pubkey) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::CancelBooking {
            venue: *venue,
            venue_access_token: *access,
            escrow_usdc: escrow_pda(access),
            venue_usdc: *venue_usdc,
            token_program: spl_token::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::CancelBooking {}.data(),
    }
}

pub struct SettleBookingAccounts {
    pub signer: Pubkey,
    pub venue: Pubkey,
    pub venue_usdc: Pubkey,
    pub treasury_usdc: Pubkey,
    pub vault_usdc: Pubkey,
    pub member_profiles: Vec<Pubkey>,
    pub with_agent: bool,
}

pub fn settle_booking_ix(a: &SettleBookingAccounts, b: &Booking, gross: u64, expenses: u64) -> Instruction {
    let access = access_pda(&a.venue, b.band_id, b.date_ymd);
    let mut accounts = eh8s_devnet::accounts::SettleBooking {
        authority: a.signer,
        config: config_pda(),
        venue_access_token: access,
        band_vault: band_pda(b.band_id),
        concert_settlement: concert_pda(&a.venue, b.concert_id),
        escrow_usdc: escrow_pda(&access),
        venue_usdc: a.venue_usdc,
        treasury_usdc: a.treasury_usdc,
        vault_usdc: a.vault_usdc,
        token_program: spl_token::id(),
        system_program: system_program::id(),
        agent_authority: a.with_agent.then(|| agent_pda(&a.signer)),
    }
    .to_account_metas(None);
    accounts.extend(
        a.member_profiles
            .iter()
            .map(|p| solana_sdk::instruction::AccountMeta::new(*p, false)),
    );
    Instruction {
        program_id: program_id(),
        accounts,
        data: eh8s_devnet::instruction::SettleBooking {
            gross_usdc: gross,
            expenses_usdc: expenses,
        }
        .data(),
    }
}

pub const E_GOVERNANCE_ACTIVE: u32 = 21;
pub const E_NOT_GOVERNANCE_SIGNER: u32 = 22;
pub const E_INVALID_GOVERNANCE: u32 = 23;
pub const E_INVALID_PROPOSAL: u32 = 24;
pub const E_ALREADY_APPROVED: u32 = 25;
pub const E_PROPOSAL_EXECUTED: u32 = 26;
pub const E_THRESHOLD_NOT_MET: u32 = 27;
pub const E_STALE_PROPOSAL: u32 = 28;
pub const E_DESTINATION_NOT_SIGNER: u32 = 29;
pub const E_INVALID_PLAN: u32 = 30;
pub const E_INVALID_GEO_SUBSCRIPTION: u32 = 31;

pub const KIND_WITHDRAW: u8 = 0;
pub const KIND_AUTHORIZE_AGENT: u8 = 1;
pub const KIND_UPDATE_SIGNERS: u8 = 2;

pub fn governance_pda() -> Pubkey {
    Pubkey::find_program_address(&[b"governance"], &program_id()).0
}

pub fn proposal_pda(id: u64) -> Pubkey {
    Pubkey::find_program_address(&[b"proposal", &id.to_le_bytes()], &program_id()).0
}

pub fn init_governance_ix(owner: &Pubkey, signers: Vec<Pubkey>, threshold: u8) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::InitGovernance {
            owner: *owner,
            config: config_pda(),
            governance: governance_pda(),
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::InitGovernance { signers, threshold }.data(),
    }
}

pub struct ProposalArgs {
    pub kind: u8,
    pub amount_usdc: u64,
    pub target: Pubkey,
    pub permissions: u8,
    pub new_signers: Vec<Pubkey>,
    pub new_threshold: u8,
}

impl ProposalArgs {
    pub fn withdraw(amount_usdc: u64, destination: Pubkey) -> Self {
        Self { kind: KIND_WITHDRAW, amount_usdc, target: destination, permissions: 0, new_signers: vec![], new_threshold: 0 }
    }
    pub fn agent(agent_wallet: Pubkey, permissions: u8) -> Self {
        Self { kind: KIND_AUTHORIZE_AGENT, amount_usdc: 0, target: agent_wallet, permissions, new_signers: vec![], new_threshold: 0 }
    }
    pub fn signers(new_signers: Vec<Pubkey>, new_threshold: u8) -> Self {
        Self { kind: KIND_UPDATE_SIGNERS, amount_usdc: 0, target: Pubkey::default(), permissions: 0, new_signers, new_threshold }
    }
}

/// `id` must be the governance `proposal_count` at send time.
pub fn propose_ix(proposer: &Pubkey, id: u64, a: ProposalArgs) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::Propose {
            proposer: *proposer,
            governance: governance_pda(),
            proposal: proposal_pda(id),
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::Propose {
            kind: a.kind,
            amount_usdc: a.amount_usdc,
            target: a.target,
            permissions: a.permissions,
            new_signers: a.new_signers,
            new_threshold: a.new_threshold,
        }
        .data(),
    }
}

pub fn approve_proposal_ix(approver: &Pubkey, id: u64) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::ApproveProposal {
            approver: *approver,
            governance: governance_pda(),
            proposal: proposal_pda(id),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::ApproveProposal {}.data(),
    }
}

pub fn execute_withdraw_ix(executor: &Pubkey, id: u64, treasury_usdc: &Pubkey, destination_usdc: &Pubkey) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::ExecuteWithdrawProposal {
            executor: *executor,
            config: config_pda(),
            governance: governance_pda(),
            proposal: proposal_pda(id),
            treasury_authority: treasury_authority(),
            treasury_usdc: *treasury_usdc,
            destination_usdc: *destination_usdc,
            token_program: spl_token::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::ExecuteWithdrawProposal {}.data(),
    }
}

pub fn execute_agent_ix(executor: &Pubkey, id: u64, agent_wallet: &Pubkey) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::ExecuteAgentProposal {
            executor: *executor,
            governance: governance_pda(),
            proposal: proposal_pda(id),
            agent_authority: agent_pda(agent_wallet),
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::ExecuteAgentProposal {}.data(),
    }
}

pub fn execute_signers_ix(executor: &Pubkey, id: u64) -> Instruction {
    Instruction {
        program_id: program_id(),
        accounts: eh8s_devnet::accounts::ExecuteSignersProposal {
            executor: *executor,
            governance: governance_pda(),
            proposal: proposal_pda(id),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::ExecuteSignersProposal {}.data(),
    }
}
