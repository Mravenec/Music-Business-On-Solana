mod common;

use common::*;
use solana_sdk::signature::{Keypair, Signer};

const SPEC_WEIGHTS: [u16; 4] = [3_800, 3_520, 2_000, 680];

async fn four_member_band(env: &mut Env, band_id: u64) -> Vec<Keypair> {
    let mut members = Vec::new();
    for _ in 0..4 {
        members.push(env.funded_wallet().await);
    }
    let refs: Vec<&Keypair> = members.iter().collect();
    env.band_with(band_id, &refs).await;
    members
}

fn settle_accounts(env: &Env, venue: &Keypair, venue_usdc: solana_sdk::pubkey::Pubkey, band_id: u64, members: &[Keypair]) -> SettleAccounts {
    SettleAccounts {
        venue: venue.pubkey(),
        venue_usdc,
        treasury_usdc: env.treasury_usdc,
        vault_usdc: env.vault_usdc,
        band_id,
        member_profiles: members.iter().map(|m| musician_pda(&m.pubkey())).collect(),
    }
}

#[tokio::test]
async fn spec_example_splits_pool_by_weights_on_chain() {
    let mut env = Env::start().await;
    let members = four_member_band(&mut env, 28).await;
    let owner = env.owner.insecure_clone();
    env.send(&[update_weights_ix(&owner.pubkey(), 28, SPEC_WEIGHTS.to_vec())], &[&owner])
        .await
        .unwrap();

    let (venue, venue_usdc) = env.wallet_with_usdc(875 * USDC).await;
    let accounts = settle_accounts(&env, &venue, venue_usdc, 28, &members);
    env.send(&[settle_with_expenses_ix(&accounts, 1, 875 * USDC, 200 * USDC)], &[&venue])
        .await
        .unwrap();

    // gross 875 - expenses 200 = net 675; fee 15% = 101.25; pool = 573.75
    assert_eq!(env.token_balance(&env.treasury_usdc.clone()).await, 101_250_000);
    assert_eq!(env.token_balance(&env.vault_usdc.clone()).await, 573_750_000);
    // expenses never leave the venue
    assert_eq!(env.token_balance(&venue_usdc).await, 200 * USDC);

    let expected = [218_025_000u64, 201_960_000, 114_750_000, 39_015_000];
    for (m, want) in members.iter().zip(expected) {
        assert_eq!(env.pending(&m.pubkey()).await, want);
    }

    let row: eh8s_devnet::ConcertSettlement = env.fetch(&concert_pda(&venue.pubkey(), 1)).await;
    assert_eq!(row.gross_usdc, 875 * USDC);
    assert_eq!(row.expenses_usdc, 200 * USDC);
    assert_eq!(row.eh8s_fee_usdc, 101_250_000);
    assert_eq!(row.band_pool_usdc, 573_750_000);
    assert_eq!(row.band, band_pda(28));
}

#[tokio::test]
async fn create_band_defaults_to_equal_weights_with_dust_to_last() {
    let mut env = Env::start().await;
    let a = env.funded_wallet().await;
    let b = env.funded_wallet().await;
    let c = env.funded_wallet().await;
    env.band_with(5, &[&a, &b, &c]).await;
    let v: eh8s_devnet::BandVault = env.fetch(&band_pda(5)).await;
    assert_eq!(v.band_id, 5);
    assert_eq!(v.members, vec![a.pubkey(), b.pubkey(), c.pubkey()]);
    assert_eq!(v.weights_bps, vec![3_333, 3_333, 3_334]);
    assert_eq!(v.weights_version, 0);
}

#[tokio::test]
async fn only_owner_creates_band_and_updates_weights() {
    let mut env = Env::start().await;
    let stranger = env.funded_wallet().await;
    let m = env.funded_wallet().await;
    let res = env
        .send(&[create_band_ix(&stranger.pubkey(), 9, vec![m.pubkey()])], &[&stranger])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    env.band_with(9, &[&m]).await;
    let res = env
        .send(&[update_weights_ix(&stranger.pubkey(), 9, vec![10_000])], &[&stranger])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);
}

#[tokio::test]
async fn create_band_rejects_empty_duplicate_or_oversized_members() {
    let mut env = Env::start().await;
    let owner = env.owner.insecure_clone();
    let m = env.funded_wallet().await;

    let res = env.send(&[create_band_ix(&owner.pubkey(), 1, vec![])], &[&owner]).await;
    assert_eh8s_err(res, E_INVALID_MEMBERS);

    let res = env
        .send(&[create_band_ix(&owner.pubkey(), 2, vec![m.pubkey(), m.pubkey()])], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_MEMBERS);

    let nine: Vec<_> = (0..9).map(|_| Keypair::new().pubkey()).collect();
    let res = env.send(&[create_band_ix(&owner.pubkey(), 3, nine)], &[&owner]).await;
    assert_eh8s_err(res, E_INVALID_MEMBERS);
}

#[tokio::test]
async fn weights_must_match_members_and_sum_to_10000() {
    let mut env = Env::start().await;
    let _members = four_member_band(&mut env, 4).await;
    let owner = env.owner.insecure_clone();

    let res = env
        .send(&[update_weights_ix(&owner.pubkey(), 4, vec![5_000, 5_000])], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_WEIGHTS);

    let res = env
        .send(&[update_weights_ix(&owner.pubkey(), 4, vec![3_800, 3_520, 2_000, 679])], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_WEIGHTS);

    env.send(&[update_weights_ix(&owner.pubkey(), 4, SPEC_WEIGHTS.to_vec())], &[&owner])
        .await
        .unwrap();
    let v: eh8s_devnet::BandVault = env.fetch(&band_pda(4)).await;
    assert_eq!(v.weights_bps, SPEC_WEIGHTS.to_vec());
    assert_eq!(v.weights_version, 1);
}

#[tokio::test]
async fn settle_rejects_missing_reordered_or_foreign_member_profiles() {
    let mut env = Env::start().await;
    let members = four_member_band(&mut env, 6).await;
    let (venue, venue_usdc) = env.wallet_with_usdc(100 * USDC).await;
    let mut accounts = settle_accounts(&env, &venue, venue_usdc, 6, &members);

    let full = accounts.member_profiles.clone();
    accounts.member_profiles = full[..3].to_vec();
    let res = env.send(&[settle_ix(&accounts, 1, 100 * USDC)], &[&venue]).await;
    assert_eh8s_err(res, E_MEMBER_MISMATCH);

    accounts.member_profiles = vec![full[1], full[0], full[2], full[3]];
    let res = env.send(&[settle_ix(&accounts, 1, 100 * USDC)], &[&venue]).await;
    assert_eh8s_err(res, E_MEMBER_MISMATCH);

    let outsider = env.funded_wallet().await;
    env.upsert_profile(&outsider, 1).await;
    accounts.member_profiles = vec![full[0], full[1], full[2], musician_pda(&outsider.pubkey())];
    let res = env.send(&[settle_ix(&accounts, 1, 100 * USDC)], &[&venue]).await;
    assert_eh8s_err(res, E_MEMBER_MISMATCH);

    accounts.member_profiles = full;
    env.send(&[settle_ix(&accounts, 1, 100 * USDC)], &[&venue]).await.unwrap();
}

#[tokio::test]
async fn settle_rejects_expenses_at_or_above_gross() {
    let mut env = Env::start().await;
    let members = four_member_band(&mut env, 7).await;
    let (venue, venue_usdc) = env.wallet_with_usdc(100 * USDC).await;
    let accounts = settle_accounts(&env, &venue, venue_usdc, 7, &members);
    let res = env
        .send(&[settle_with_expenses_ix(&accounts, 1, 100 * USDC, 100 * USDC)], &[&venue])
        .await;
    assert_eh8s_err(res, E_ZERO);
    let res = env
        .send(&[settle_with_expenses_ix(&accounts, 1, 100 * USDC, 101 * USDC)], &[&venue])
        .await;
    assert_failed(res);
}

#[test]
fn split_members_floor_math_dust_to_last() {
    let shares = eh8s_devnet::split_members(573_750_000, &SPEC_WEIGHTS).unwrap();
    assert_eq!(shares, vec![218_025_000, 201_960_000, 114_750_000, 39_015_000]);
    assert_eq!(eh8s_devnet::split_members(10, &[3_333, 3_333, 3_334]).unwrap(), vec![3, 3, 4]);
    assert_eq!(eh8s_devnet::equal_weights(3), vec![3_333, 3_333, 3_334]);
    assert_eq!(eh8s_devnet::equal_weights(1), vec![10_000]);
}
