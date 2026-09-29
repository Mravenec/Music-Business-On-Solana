mod common;

use anchor_spl::associated_token::get_associated_token_address;
use common::*;
use solana_sdk::signature::{Keypair, Signer};

/// Runs `init_treasury` as the upgrade authority and returns the treasury PDA ATA.
async fn with_treasury(env: &mut Env) -> solana_sdk::pubkey::Pubkey {
    let authority = env.authority.insecure_clone();
    let ix = init_treasury_ix(&authority.pubkey(), &env.mint);
    env.send(&[ix], &[&authority]).await.expect("init_treasury");
    get_associated_token_address(&treasury_authority(), &env.mint)
}

/// Mints what an academy payment of `gross` leaves in the treasury PDA ATA (85% of it).
async fn fund_treasury(env: &mut Env, treasury: &solana_sdk::pubkey::Pubkey, gross: u64) {
    let mint = env.mint;
    env.mint_to(&mint, treasury, gross / 100 * 85).await;
}

#[tokio::test]
async fn init_treasury_repoints_config_and_is_upgrade_authority_only() {
    let mut env = Env::start().await;
    let stranger = env.funded_wallet().await;
    let ix = init_treasury_ix(&stranger.pubkey(), &env.mint);
    assert_eh8s_err(env.send(&[ix], &[&stranger]).await, E_UNAUTHORIZED);

    let treasury = with_treasury(&mut env).await;
    let cfg: eh8s_devnet::Eh8sConfig = env.fetch(&config_pda()).await;
    assert_eq!(cfg.treasury_usdc, treasury);
    assert_eq!(cfg.owner, env.owner.pubkey());
    assert_eq!(env.token_balance(&treasury).await, 0);

    // Idempotent: running it again keeps the same ATA.
    let authority = env.authority.insecure_clone();
    let ix = init_treasury_ix(&authority.pubkey(), &env.mint);
    env.send(&[ix], &[&authority]).await.expect("second init_treasury");
}

#[tokio::test]
async fn fees_land_in_treasury_pda_and_old_owner_ata_is_rejected() {
    let mut env = Env::start().await;
    let old_owner_ata = env.treasury_usdc;
    let treasury = with_treasury(&mut env).await;

    let (payer, payer_usdc) = env.wallet_with_usdc(40 * USDC).await;
    let instructor = env.funded_wallet().await;
    let instructor_usdc = env.create_ata(&instructor.pubkey()).await;
    let res = env
        .send(&[academy_ix(&payer.pubkey(), &payer_usdc, &old_owner_ata, &instructor_usdc, 1, 1)], &[&payer])
        .await;
    assert_eh8s_err(res, E_INVALID_TREASURY);
    let res = env.send(&[geo_ix(&payer.pubkey(), &payer_usdc, &old_owner_ata, "MX", 1, 1)], &[&payer]).await;
    assert_eh8s_err(res, E_INVALID_TREASURY);

    env.send(&[academy_ix(&payer.pubkey(), &payer_usdc, &treasury, &instructor_usdc, 1, 1)], &[&payer])
        .await
        .unwrap();
    env.send(&[geo_ix(&payer.pubkey(), &payer_usdc, &treasury, "MX", 1, 1)], &[&payer])
        .await
        .unwrap();
    assert_eq!(env.token_balance(&treasury).await, 25_500_000 + 5 * USDC);
    assert_eq!(env.token_balance(&old_owner_ata).await, 0);
}

#[tokio::test]
async fn owner_withdraws_to_own_ata() {
    let mut env = Env::start().await;
    let treasury = with_treasury(&mut env).await;
    fund_treasury(&mut env, &treasury, 100 * USDC).await;
    assert_eq!(env.token_balance(&treasury).await, 85 * USDC);

    let owner = env.owner.insecure_clone();
    let owner_usdc = env.treasury_usdc;
    env.send(&[withdraw_treasury_ix(&owner.pubkey(), &treasury, &owner_usdc, 60 * USDC)], &[&owner])
        .await
        .unwrap();
    assert_eq!(env.token_balance(&treasury).await, 25 * USDC);
    assert_eq!(env.token_balance(&owner_usdc).await, 60 * USDC);
}

#[tokio::test]
async fn withdraw_rejects_non_owner_over_withdraw_zero_and_foreign_destination() {
    let mut env = Env::start().await;
    let treasury = with_treasury(&mut env).await;
    fund_treasury(&mut env, &treasury, 10 * USDC).await;
    let owner = env.owner.insecure_clone();
    let owner_usdc = env.treasury_usdc;

    let thief: Keypair = env.funded_wallet().await;
    let thief_usdc = env.create_ata(&thief.pubkey()).await;
    let res = env
        .send(&[withdraw_treasury_ix(&thief.pubkey(), &treasury, &thief_usdc, USDC)], &[&thief])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    let res = env
        .send(&[withdraw_treasury_ix(&owner.pubkey(), &treasury, &thief_usdc, USDC)], &[&owner])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    let res = env
        .send(&[withdraw_treasury_ix(&owner.pubkey(), &treasury, &owner_usdc, 9 * USDC)], &[&owner])
        .await;
    assert_eh8s_err(res, E_INSUFFICIENT_TREASURY);

    let res = env
        .send(&[withdraw_treasury_ix(&owner.pubkey(), &treasury, &owner_usdc, 0)], &[&owner])
        .await;
    assert_eh8s_err(res, E_ZERO);

    assert_eq!(env.token_balance(&treasury).await, 8_500_000);
}

#[tokio::test]
async fn withdraw_before_init_treasury_is_rejected() {
    let mut env = Env::start().await;
    let owner = env.owner.insecure_clone();
    let owner_ata = env.treasury_usdc;
    let res = env
        .send(&[withdraw_treasury_ix(&owner.pubkey(), &owner_ata, &owner_ata, USDC)], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_TREASURY);
}
