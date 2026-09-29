mod common;

use common::*;
use solana_sdk::signature::{Keypair, Signer};

async fn members(env: &mut Env, n: usize) -> Vec<Keypair> {
    let mut out = Vec::new();
    for _ in 0..n {
        let m = env.funded_wallet().await;
        env.upsert_profile(&m, 2).await;
        out.push(m);
    }
    out
}

fn keys(ms: &[Keypair]) -> Vec<solana_sdk::pubkey::Pubkey> {
    ms.iter().map(|m| m.pubkey()).collect()
}

#[tokio::test]
async fn create_pool_checks_splits_members_and_power() {
    let mut env = Env::start().await;
    let owner = env.owner.insecure_clone();
    let ms = members(&mut env, 3).await;
    let k = keys(&ms);

    let res = env
        .send(&[create_pool_ix(&owner.pubkey(), 1, k.clone(), vec![5_000, 3_000, 1_999], false)], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_WEIGHTS);

    let res = env
        .send(&[create_pool_ix(&owner.pubkey(), 1, k.clone(), vec![5_000, 5_000], false)], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_WEIGHTS);

    let res = env
        .send(&[create_pool_ix(&owner.pubkey(), 1, k.clone(), vec![10_000, 0, 0], false)], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_WEIGHTS);

    let dup = vec![k[0], k[0], k[1]];
    let res = env
        .send(&[create_pool_ix(&owner.pubkey(), 1, dup, vec![5_000, 3_000, 2_000], false)], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_MEMBERS);

    let nine: Vec<_> = (0..9).map(|_| Keypair::new().pubkey()).collect();
    let res = env
        .send(&[create_pool_ix(&owner.pubkey(), 1, nine, vec![1_111; 9], false)], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_MEMBERS);

    let stranger = env.funded_wallet().await;
    let res = env
        .send(&[create_pool_ix(&stranger.pubkey(), 1, k.clone(), vec![5_000, 3_000, 2_000], false)], &[&stranger])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    env.send(&[create_pool_ix(&owner.pubkey(), 1, k.clone(), vec![5_000, 3_000, 2_000], false)], &[&owner])
        .await
        .unwrap();
    let pool: eh8s_devnet::RoyaltyPool = env.fetch(&song_pool_pda(1)).await;
    assert_eq!(pool.track_id, 1);
    assert_eq!(pool.members, k);
    assert_eq!(pool.splits_bps, vec![5_000, 3_000, 2_000]);

    let res = env
        .send(&[create_pool_ix(&owner.pubkey(), 1, k, vec![5_000, 3_000, 2_000], false)], &[&owner])
        .await;
    assert_failed(res);
}

#[tokio::test]
async fn deposit_credits_each_member_by_split_with_dust_to_last() {
    let mut env = Env::start().await;
    let owner = env.owner.insecure_clone();
    let owner_usdc = env.treasury_usdc;
    let mint = env.mint;
    env.mint_to(&mint, &owner_usdc, 500 * USDC).await;
    let ms = members(&mut env, 3).await;
    let k = keys(&ms);
    env.send(&[create_pool_ix(&owner.pubkey(), 42, k.clone(), vec![5_000, 3_000, 2_000], false)], &[&owner])
        .await
        .unwrap();
    let vault = env.vault_usdc;

    let wrong_order = vec![k[1], k[0], k[2]];
    let res = env
        .send(&[deposit_ix(&owner.pubkey(), &owner_usdc, &vault, 42, 100 * USDC, &wrong_order, false)], &[&owner])
        .await;
    assert_eh8s_err(res, E_MEMBER_MISMATCH);

    let res = env
        .send(&[deposit_ix(&owner.pubkey(), &owner_usdc, &vault, 42, 100 * USDC, &k[..2], false)], &[&owner])
        .await;
    assert_eh8s_err(res, E_MEMBER_MISMATCH);

    let amount = 100 * USDC + 1;
    env.send(&[deposit_ix(&owner.pubkey(), &owner_usdc, &vault, 42, amount, &k, false)], &[&owner])
        .await
        .unwrap();
    assert_eq!(env.pending(&k[0]).await, 50 * USDC);
    assert_eq!(env.pending(&k[1]).await, 30 * USDC);
    assert_eq!(env.pending(&k[2]).await, 20 * USDC + 1);
    assert_eq!(env.token_balance(&vault).await, amount);
    let pool: eh8s_devnet::RoyaltyPool = env.fetch(&song_pool_pda(42)).await;
    assert_eq!(pool.total_usdc, amount);
    assert_eq!(pool.sync_total_usdc, 0);
}

#[tokio::test]
async fn only_owner_or_wave_agent_deposits() {
    let mut env = Env::start().await;
    let owner = env.owner.insecure_clone();
    let ms = members(&mut env, 2).await;
    let k = keys(&ms);
    env.send(&[create_pool_ix(&owner.pubkey(), 9, k.clone(), vec![6_000, 4_000], false)], &[&owner])
        .await
        .unwrap();
    let vault = env.vault_usdc;

    let (stranger, stranger_usdc) = env.wallet_with_usdc(50 * USDC).await;
    let res = env
        .send(&[deposit_ix(&stranger.pubkey(), &stranger_usdc, &vault, 9, 10 * USDC, &k, false)], &[&stranger])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    let (harmony, harmony_usdc) = env.wallet_with_usdc(50 * USDC).await;
    env.authorize_agent(&harmony.pubkey(), PERM_HARMONY).await;
    let res = env
        .send(&[deposit_ix(&harmony.pubkey(), &harmony_usdc, &vault, 9, 10 * USDC, &k, true)], &[&harmony])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    let (wave, wave_usdc) = env.wallet_with_usdc(50 * USDC).await;
    env.authorize_agent(&wave.pubkey(), PERM_WAVE).await;
    env.send(&[deposit_ix(&wave.pubkey(), &wave_usdc, &vault, 9, 10 * USDC, &k, true)], &[&wave])
        .await
        .unwrap();
    assert_eq!(env.pending(&k[0]).await, 6 * USDC);
    assert_eq!(env.pending(&k[1]).await, 4 * USDC);

    let ms2 = members(&mut env, 1).await;
    env.send(&[create_pool_ix(&wave.pubkey(), 10, keys(&ms2), vec![10_000], true)], &[&wave])
        .await
        .unwrap();

    env.authorize_agent(&wave.pubkey(), 0).await;
    let res = env
        .send(&[deposit_ix(&wave.pubkey(), &wave_usdc, &vault, 9, 10 * USDC, &k, true)], &[&wave])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);
}

#[tokio::test]
async fn sync_license_pays_80_20_with_dust_once_per_deal() {
    let mut env = Env::start().await;
    let owner = env.owner.insecure_clone();
    let ms = members(&mut env, 2).await;
    let k = keys(&ms);
    env.send(&[create_pool_ix(&owner.pubkey(), 5, k.clone(), vec![7_500, 2_500], false)], &[&owner])
        .await
        .unwrap();
    let (licensee, licensee_usdc) = env.wallet_with_usdc(100 * USDC).await;
    let treasury = env.treasury_usdc;
    let vault = env.vault_usdc;
    let a = SyncAccounts {
        payer: licensee.pubkey(),
        payer_usdc: licensee_usdc,
        treasury_usdc: treasury,
        vault_usdc: vault,
    };
    let treasury_before = env.token_balance(&treasury).await;

    let redirect = SyncAccounts { treasury_usdc: licensee_usdc, ..a_copy(&a) };
    let res = env.send(&[sync_ix(&redirect, 5, 1, 10 * USDC, &k)], &[&licensee]).await;
    assert_eh8s_err(res, E_INVALID_TREASURY);

    let amount = 10 * USDC + 3;
    env.send(&[sync_ix(&a, 5, 1, amount, &k)], &[&licensee]).await.unwrap();
    let fee = 2 * USDC;
    let artist = amount - fee;
    assert_eq!(env.token_balance(&treasury).await - treasury_before, fee);
    assert_eq!(env.token_balance(&vault).await, artist);
    assert_eq!(env.pending(&k[0]).await, 6 * USDC + 2);
    assert_eq!(env.pending(&k[1]).await, 2 * USDC + 1);

    let lic: eh8s_devnet::SyncLicense = env.fetch(&sync_pda(5, 1)).await;
    assert_eq!((lic.track_id, lic.deal_id), (5, 1));
    assert_eq!(lic.payer, licensee.pubkey());
    assert_eq!((lic.amount_usdc, lic.eh8s_fee_usdc, lic.artist_usdc), (amount, fee, artist));
    let pool: eh8s_devnet::RoyaltyPool = env.fetch(&song_pool_pda(5)).await;
    assert_eq!(pool.sync_total_usdc, amount);
    assert_eq!(pool.total_usdc, 0);

    let res = env.send(&[sync_ix(&a, 5, 1, amount, &k)], &[&licensee]).await;
    assert_failed(res);

    env.send(&[sync_ix(&a, 5, 2, 5 * USDC, &k)], &[&licensee]).await.unwrap();
    let pool: eh8s_devnet::RoyaltyPool = env.fetch(&song_pool_pda(5)).await;
    assert_eq!(pool.sync_total_usdc, amount + 5 * USDC);

    let res = env.send(&[sync_ix(&a, 6, 1, 5 * USDC, &k)], &[&licensee]).await;
    assert_failed(res);
}

fn a_copy(a: &SyncAccounts) -> SyncAccounts {
    SyncAccounts {
        payer: a.payer,
        payer_usdc: a.payer_usdc,
        treasury_usdc: a.treasury_usdc,
        vault_usdc: a.vault_usdc,
    }
}
