mod common;

use common::*;
use solana_sdk::signature::{Keypair, Signer};

async fn venue_and_musician(env: &mut Env, venue_usdc_units: u64) -> (Keypair, SettleAccounts, Keypair) {
    let (venue, venue_usdc) = env.wallet_with_usdc(venue_usdc_units).await;
    let musician = env.funded_wallet().await;
    env.band_with(1, &[&musician]).await;
    let accounts = SettleAccounts {
        venue: venue.pubkey(),
        venue_usdc,
        treasury_usdc: env.treasury_usdc,
        vault_usdc: env.vault_usdc,
        band_id: 1,
        member_profiles: vec![musician_pda(&musician.pubkey())],
    };
    (venue, accounts, musician)
}

#[tokio::test]
async fn initialize_config_only_upgrade_authority() {
    let mut env = Env::start().await;
    let cfg: eh8s_devnet::Eh8sConfig = env.fetch(&config_pda()).await;
    assert_eq!(cfg.owner, env.owner.pubkey());
    assert_eq!(cfg.usdc_mint, env.mint);
    assert_eq!(cfg.treasury_usdc, env.treasury_usdc);
    assert_eq!(cfg.vault_usdc, env.vault_usdc);
    assert_eq!(cfg.protocol_fee_bps, FEE_BPS);

    // A second init (even by the authority) fails: config already exists.
    let stranger = env.funded_wallet().await;
    let ix = env.initialize_config_ix(&stranger.pubkey(), env.mint);
    assert_failed(env.send(&[ix], &[&stranger]).await);
}

#[tokio::test]
async fn settle_moves_fee_and_pool_from_config_bps() {
    let mut env = Env::start().await;
    let gross = 1_000 * USDC;
    let (venue, accounts, musician) = venue_and_musician(&mut env, gross).await;

    env.send(&[settle_ix(&accounts, 42, gross)], &[&venue]).await.unwrap();

    assert_eq!(env.token_balance(&env.treasury_usdc.clone()).await, 150 * USDC);
    assert_eq!(env.token_balance(&env.vault_usdc.clone()).await, 850 * USDC);
    assert_eq!(env.token_balance(&accounts.venue_usdc).await, 0);
    assert_eq!(env.pending(&musician.pubkey()).await, 850 * USDC);

    let row: eh8s_devnet::ConcertSettlement = env.fetch(&concert_pda(&venue.pubkey(), 42)).await;
    assert_eq!(row.concert_id, 42);
    assert_eq!(row.eh8s_fee_usdc, 150 * USDC);
    assert_eq!(row.band_pool_usdc, 850 * USDC);
    assert_eq!(row.band, band_pda(1));
    assert_eq!(row.settled, 1);
}

#[tokio::test]
async fn settle_same_concert_twice_fails() {
    let mut env = Env::start().await;
    let (venue, accounts, _m) = venue_and_musician(&mut env, 200 * USDC).await;
    env.send(&[settle_ix(&accounts, 7, 100 * USDC)], &[&venue]).await.unwrap();
    let res = env.send(&[settle_ix(&accounts, 7, 100 * USDC)], &[&venue]).await;
    assert_eh8s_err(res, E_ALREADY_SETTLED);
    // A different concert id is a new settlement.
    env.send(&[settle_ix(&accounts, 8, 100 * USDC)], &[&venue]).await.unwrap();
}

#[tokio::test]
async fn settle_rejects_foreign_treasury_and_vault() {
    let mut env = Env::start().await;
    let (venue, mut accounts, _m) = venue_and_musician(&mut env, 100 * USDC).await;
    let attacker = env.funded_wallet().await;
    let attacker_usdc = env.create_ata(&attacker.pubkey()).await;

    accounts.treasury_usdc = attacker_usdc;
    let res = env.send(&[settle_ix(&accounts, 1, 100 * USDC)], &[&venue]).await;
    assert_eh8s_err(res, E_INVALID_TREASURY);

    accounts.treasury_usdc = env.treasury_usdc;
    accounts.vault_usdc = attacker_usdc;
    let res = env.send(&[settle_ix(&accounts, 1, 100 * USDC)], &[&venue]).await;
    assert_eh8s_err(res, E_INVALID_VAULT);
}

#[tokio::test]
async fn settle_rejects_wrong_mint() {
    let mut env = Env::start().await;
    let (venue, mut accounts, _m) = venue_and_musician(&mut env, 0).await;
    let fake_mint = env.create_mint().await;
    let fake_ata = env.create_ata_for_mint(&venue.pubkey(), &fake_mint).await;
    env.mint_to(&fake_mint, &fake_ata, 100 * USDC).await;
    accounts.venue_usdc = fake_ata;
    let res = env.send(&[settle_ix(&accounts, 1, 100 * USDC)], &[&venue]).await;
    assert_eh8s_err(res, E_INVALID_MINT);
}

#[tokio::test]
async fn settle_rejects_non_profile_account() {
    let mut env = Env::start().await;
    let (venue, mut accounts, _m) = venue_and_musician(&mut env, 100 * USDC).await;
    // Config is owned by the program but is not a MusicianProfile.
    accounts.member_profiles = vec![config_pda()];
    assert_failed(env.send(&[settle_ix(&accounts, 1, 100 * USDC)], &[&venue]).await);
}

#[tokio::test]
async fn upsert_keeps_pending_claims() {
    let mut env = Env::start().await;
    let (venue, accounts, musician) = venue_and_musician(&mut env, 100 * USDC).await;
    env.send(&[settle_ix(&accounts, 1, 100 * USDC)], &[&venue]).await.unwrap();
    assert_eq!(env.pending(&musician.pubkey()).await, 85 * USDC);
    env.upsert_profile(&musician, 5).await;
    assert_eq!(env.pending(&musician.pubkey()).await, 85 * USDC);
    let p: eh8s_devnet::MusicianProfile = env.fetch(&musician_pda(&musician.pubkey())).await;
    assert_eq!(p.instrument_code, 5);
    assert_eq!(p.enigma_level, 0);
    assert_eq!(&p.country, b"USA");
}

#[tokio::test]
async fn claim_pays_musician_and_guards_amount_and_signer() {
    let mut env = Env::start().await;
    let (venue, accounts, musician) = venue_and_musician(&mut env, 100 * USDC).await;
    env.send(&[settle_ix(&accounts, 1, 100 * USDC)], &[&venue]).await.unwrap();
    let dest = env.create_ata(&musician.pubkey()).await;
    let profile = musician_pda(&musician.pubkey());
    let vault = env.vault_usdc;

    let res = env
        .send(&[claim_ix(&musician.pubkey(), &profile, &vault, &dest, 86 * USDC)], &[&musician])
        .await;
    assert_eh8s_err(res, E_INSUFFICIENT_CLAIM);

    // Another wallet cannot claim against this profile.
    let thief = env.funded_wallet().await;
    let thief_usdc = env.create_ata(&thief.pubkey()).await;
    let res = env
        .send(&[claim_ix(&thief.pubkey(), &profile, &vault, &thief_usdc, USDC)], &[&thief])
        .await;
    assert_failed(res);

    // The musician's signature cannot pay a USDC account they do not own.
    let res = env
        .send(&[claim_ix(&musician.pubkey(), &profile, &vault, &thief_usdc, USDC)], &[&musician])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    env.send(&[claim_ix(&musician.pubkey(), &profile, &vault, &dest, 85 * USDC)], &[&musician])
        .await
        .unwrap();
    assert_eq!(env.token_balance(&dest).await, 85 * USDC);
    assert_eq!(env.token_balance(&vault).await, 0);
    assert_eq!(env.pending(&musician.pubkey()).await, 0);
}

#[tokio::test]
async fn deposit_funds_vault_and_rejects_foreign_vault() {
    let mut env = Env::start().await;
    let owner = env.owner.insecure_clone();
    let owner_usdc = env.treasury_usdc;
    let mint = env.mint;
    env.mint_to(&mint, &owner_usdc, 50 * USDC).await;
    let musician = env.funded_wallet().await;
    env.upsert_profile(&musician, 1).await;
    let members = [musician.pubkey()];
    env.send(&[create_pool_ix(&owner.pubkey(), 7, members.to_vec(), vec![10_000], false)], &[&owner])
        .await
        .unwrap();

    let res = env
        .send(&[deposit_ix(&owner.pubkey(), &owner_usdc, &owner_usdc, 7, 10 * USDC, &members, false)], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_VAULT);

    let vault = env.vault_usdc;
    env.send(&[deposit_ix(&owner.pubkey(), &owner_usdc, &vault, 7, 10 * USDC, &members, false)], &[&owner])
        .await
        .unwrap();
    assert_eq!(env.token_balance(&vault).await, 10 * USDC);
    assert_eq!(env.pending(&musician.pubkey()).await, 10 * USDC);
}

#[tokio::test]
async fn academy_splits_85_15_and_rejects_redirects() {
    let mut env = Env::start().await;
    let (payer, payer_usdc) = env.wallet_with_usdc(100 * USDC).await;
    let instructor = env.funded_wallet().await;
    let instructor_usdc = env.create_ata(&instructor.pubkey()).await;
    let treasury = env.treasury_usdc;

    let res = env
        .send(&[academy_ix(&payer.pubkey(), &payer_usdc, &instructor_usdc, &instructor_usdc, 1, 1)], &[&payer])
        .await;
    assert_eh8s_err(res, E_INVALID_TREASURY);

    let res = env
        .send(&[academy_ix(&payer.pubkey(), &payer_usdc, &treasury, &payer_usdc, 1, 1)], &[&payer])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    env.send(&[academy_ix(&payer.pubkey(), &payer_usdc, &treasury, &instructor_usdc, 1, 1)], &[&payer])
        .await
        .unwrap();
    assert_eq!(env.token_balance(&treasury).await, 25_500_000);
    assert_eq!(env.token_balance(&instructor_usdc).await, 4_500_000);
}

#[tokio::test]
async fn geographic_pays_treasury_only() {
    let mut env = Env::start().await;
    let (payer, payer_usdc) = env.wallet_with_usdc(10 * USDC).await;
    let res = env
        .send(&[geo_ix(&payer.pubkey(), &payer_usdc, &payer_usdc, "MX", 1, 1)], &[&payer])
        .await;
    assert_eh8s_err(res, E_INVALID_TREASURY);
    let treasury = env.treasury_usdc;
    env.send(&[geo_ix(&payer.pubkey(), &payer_usdc, &treasury, "MX", 1, 1)], &[&payer])
        .await
        .unwrap();
    assert_eq!(env.token_balance(&treasury).await, 5 * USDC);
}

#[test]
fn split_fee_floor_math() {
    assert_eq!(eh8s_devnet::split_fee(1_000 * USDC, 1_500).unwrap(), (150 * USDC, 850 * USDC));
    assert_eq!(eh8s_devnet::split_fee(1, 1_500).unwrap(), (0, 1));
    assert_eq!(eh8s_devnet::split_fee(u64::MAX, 10_000).unwrap(), (u64::MAX, 0));
}
