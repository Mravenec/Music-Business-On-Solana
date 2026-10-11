mod common;

use anchor_lang::prelude::Pubkey as AnchorPubkey;
use common::*;
use eh8s_devnet::{
    apply_partner_share, max_concurrent_bps, window_active_at, PartnerWindow, STUDIO_ROLE_ADMIN,
};
use solana_sdk::pubkey::Pubkey;
use solana_sdk::signature::{Keypair, Signer};

fn win(share_bps: u16, starts_at: i64, ends_at: i64) -> PartnerWindow {
    PartnerWindow { wallet: AnchorPubkey::new_unique(), share_bps, starts_at, ends_at }
}

async fn owner_sends(env: &mut Env, ix: solana_sdk::instruction::Instruction) -> Result<(), solana_program_test::BanksClientError> {
    let owner = env.owner.insecure_clone();
    env.send(&[ix], &[&owner]).await
}

async fn book(env: &mut Env) -> eh8s_devnet::PartnerBook {
    env.fetch(&partner_book_pda()).await
}

#[tokio::test]
async fn owner_grants_and_revokes_studio_admin() {
    let mut env = Env::start().await;
    let owner = env.owner.pubkey();
    let admin = Keypair::new().pubkey();

    owner_sends(&mut env, grant_studio_role_ix(&owner, &admin)).await.unwrap();
    let r: eh8s_devnet::StudioRole = env.fetch(&studio_role_pda(&admin)).await;
    assert_eq!((r.wallet, r.role, r.granted_by), (admin, STUDIO_ROLE_ADMIN, owner));

    // granting twice is idempotent
    owner_sends(&mut env, grant_studio_role_ix(&owner, &admin)).await.unwrap();

    owner_sends(&mut env, revoke_studio_role_ix(&owner, &admin)).await.unwrap();
    let r: eh8s_devnet::StudioRole = env.fetch(&studio_role_pda(&admin)).await;
    assert_eq!(r.role, 0);

    // a revoked wallet can be granted again
    owner_sends(&mut env, grant_studio_role_ix(&owner, &admin)).await.unwrap();
    let r: eh8s_devnet::StudioRole = env.fetch(&studio_role_pda(&admin)).await;
    assert_eq!(r.role, STUDIO_ROLE_ADMIN);
}

#[tokio::test]
async fn studio_role_rejects_stranger_studio_admin_and_bad_wallets() {
    let mut env = Env::start().await;
    let owner = env.owner.pubkey();
    let stranger = env.funded_wallet().await;
    let admin = env.funded_wallet().await;
    let target = Keypair::new().pubkey();
    owner_sends(&mut env, grant_studio_role_ix(&owner, &admin.pubkey())).await.unwrap();

    for signer in [&stranger, &admin] {
        let res = env.send(&[grant_studio_role_ix(&signer.pubkey(), &target)], &[signer]).await;
        assert_eh8s_err(res, E_UNAUTHORIZED);
        let res = env
            .send(&[revoke_studio_role_ix(&signer.pubkey(), &admin.pubkey())], &[signer])
            .await;
        assert_eh8s_err(res, E_UNAUTHORIZED);
    }
    // the admin keeps the role: a studio admin cannot revoke itself either
    let r: eh8s_devnet::StudioRole = env.fetch(&studio_role_pda(&admin.pubkey())).await;
    assert_eq!(r.role, STUDIO_ROLE_ADMIN);

    let res = owner_sends(&mut env, grant_studio_role_ix(&owner, &Pubkey::default())).await;
    assert_eh8s_err(res, E_INVALID_STUDIO_WALLET);
    // revoking a wallet that never held the role has no account to revoke
    assert_failed(owner_sends(&mut env, revoke_studio_role_ix(&owner, &target)).await);
}

#[tokio::test]
async fn owner_sets_updates_and_removes_partner_share() {
    let mut env = Env::start().await;
    let owner = env.owner.pubkey();
    let (a, b) = (Keypair::new().pubkey(), Keypair::new().pubkey());

    owner_sends(&mut env, set_partner_share_ix(&owner, &a, 6_000, 100, 0)).await.unwrap();
    owner_sends(&mut env, set_partner_share_ix(&owner, &b, 4_000, 50, 900)).await.unwrap();
    let s: eh8s_devnet::PartnerShare = env.fetch(&partner_share_pda(&a)).await;
    assert_eq!((s.wallet, s.share_bps, s.starts_at, s.ends_at, s.set_by), (a, 6_000, 100, 0, owner));
    let bk = book(&mut env).await;
    assert_eq!(bk.entries.len(), 2);
    assert_eq!(max_concurrent_bps(&bk.entries), 10_000);

    // update keeps one row per wallet
    owner_sends(&mut env, set_partner_share_ix(&owner, &a, 5_000, 100, 0)).await.unwrap();
    let bk = book(&mut env).await;
    assert_eq!(bk.entries.len(), 2);
    assert_eq!(bk.entries.iter().find(|e| e.wallet == a).unwrap().share_bps, 5_000);

    // share 0 removes the partner from the book and clears the window
    owner_sends(&mut env, set_partner_share_ix(&owner, &b, 0, 0, 0)).await.unwrap();
    let bk = book(&mut env).await;
    assert_eq!(bk.entries.len(), 1);
    let s: eh8s_devnet::PartnerShare = env.fetch(&partner_share_pda(&b)).await;
    assert_eq!((s.share_bps, s.starts_at, s.ends_at), (0, 0, 0));
}

#[tokio::test]
async fn partner_shares_never_sum_above_10000_while_windows_overlap() {
    let mut env = Env::start().await;
    let owner = env.owner.pubkey();
    let (a, b, c) = (Keypair::new().pubkey(), Keypair::new().pubkey(), Keypair::new().pubkey());

    owner_sends(&mut env, set_partner_share_ix(&owner, &a, 6_000, 0, 0)).await.unwrap();
    // overlaps a (open ended) -> 6000 + 4001 > 10000
    let res = owner_sends(&mut env, set_partner_share_ix(&owner, &b, 4_001, 500, 0)).await;
    assert_eh8s_err(res, E_PARTNER_SHARES_EXCEED_TOTAL);
    assert!(env.ctx.banks_client.get_account(partner_share_pda(&b)).await.unwrap().is_none());
    owner_sends(&mut env, set_partner_share_ix(&owner, &b, 4_000, 500, 0)).await.unwrap();

    // raising an existing partner past the cap is refused and leaves the book untouched
    let res = owner_sends(&mut env, set_partner_share_ix(&owner, &a, 6_001, 0, 0)).await;
    assert_eh8s_err(res, E_PARTNER_SHARES_EXCEED_TOTAL);
    assert_eq!(book(&mut env).await.entries.iter().find(|e| e.wallet == a).unwrap().share_bps, 6_000);

    // a window that ended before b starts does not count against b's period
    owner_sends(&mut env, set_partner_share_ix(&owner, &a, 10_000, 0, 500)).await.unwrap();
    // c overlaps both a [0,500) at 100..500 and b from 500 on: only fits beside neither at full cap
    let res = owner_sends(&mut env, set_partner_share_ix(&owner, &c, 1, 100, 0)).await;
    assert_eh8s_err(res, E_PARTNER_SHARES_EXCEED_TOTAL);
    owner_sends(&mut env, set_partner_share_ix(&owner, &c, 6_000, 500, 0)).await.unwrap();
    assert_eq!(max_concurrent_bps(&book(&mut env).await.entries), 10_000);
}

#[tokio::test]
async fn partner_share_validates_input_and_owner_only() {
    let mut env = Env::start().await;
    let owner = env.owner.pubkey();
    let a = Keypair::new().pubkey();
    let stranger = env.funded_wallet().await;
    let admin = env.funded_wallet().await;
    owner_sends(&mut env, grant_studio_role_ix(&owner, &admin.pubkey())).await.unwrap();

    for (bps, start, end) in [(10_001u16, 0i64, 0i64), (100, -1, 0), (100, 50, 50), (100, 50, 10)] {
        let res = owner_sends(&mut env, set_partner_share_ix(&owner, &a, bps, start, end)).await;
        assert_eh8s_err(res, E_INVALID_PARTNER_SHARE);
    }
    let res = owner_sends(&mut env, set_partner_share_ix(&owner, &Pubkey::default(), 100, 0, 0)).await;
    assert_eh8s_err(res, E_INVALID_PARTNER_SHARE);

    // the principal alone sets shares: neither a stranger nor a studio admin
    for signer in [&stranger, &admin] {
        let res = env
            .send(&[set_partner_share_ix(&signer.pubkey(), &a, 100, 0, 0)], &[signer])
            .await;
        assert_eh8s_err(res, E_UNAUTHORIZED);
    }
}

#[tokio::test]
async fn partner_book_holds_sixteen_partners() {
    let mut env = Env::start().await;
    let owner = env.owner.pubkey();
    let first = Keypair::new().pubkey();
    owner_sends(&mut env, set_partner_share_ix(&owner, &first, 100, 0, 0)).await.unwrap();
    for _ in 1..16 {
        let w = Keypair::new().pubkey();
        owner_sends(&mut env, set_partner_share_ix(&owner, &w, 100, 0, 0)).await.unwrap();
    }
    let extra = Keypair::new().pubkey();
    let res = owner_sends(&mut env, set_partner_share_ix(&owner, &extra, 100, 0, 0)).await;
    assert_eh8s_err(res, E_PARTNER_BOOK_FULL);
    // removing one frees a slot; updating an existing partner never needs a slot
    owner_sends(&mut env, set_partner_share_ix(&owner, &first, 200, 0, 0)).await.unwrap();
    owner_sends(&mut env, set_partner_share_ix(&owner, &first, 0, 0, 0)).await.unwrap();
    owner_sends(&mut env, set_partner_share_ix(&owner, &extra, 100, 0, 0)).await.unwrap();
    assert_eq!(book(&mut env).await.entries.len(), 16);
}

#[tokio::test]
async fn studio_powers_are_refused_once_governance_is_active() {
    let mut env = Env::start().await;
    let owner = env.owner.insecure_clone();
    let admin = Keypair::new().pubkey();
    let partner = Keypair::new().pubkey();
    // set up before governance: these rows must survive it
    owner_sends(&mut env, grant_studio_role_ix(&owner.pubkey(), &admin)).await.unwrap();
    owner_sends(&mut env, set_partner_share_ix(&owner.pubkey(), &partner, 2_500, 0, 0)).await.unwrap();

    let other = Keypair::new().pubkey();
    env.send(&[init_governance_ix(&owner.pubkey(), vec![owner.pubkey(), other], 2)], &[&owner])
        .await
        .unwrap();

    let res = owner_sends(&mut env, grant_studio_role_ix(&owner.pubkey(), &partner)).await;
    assert_eh8s_err(res, E_GOVERNANCE_ACTIVE);
    let res = owner_sends(&mut env, revoke_studio_role_ix(&owner.pubkey(), &admin)).await;
    assert_eh8s_err(res, E_GOVERNANCE_ACTIVE);
    let res = owner_sends(&mut env, set_partner_share_ix(&owner.pubkey(), &partner, 100, 0, 0)).await;
    assert_eh8s_err(res, E_GOVERNANCE_ACTIVE);

    let r: eh8s_devnet::StudioRole = env.fetch(&studio_role_pda(&admin)).await;
    assert_eq!(r.role, STUDIO_ROLE_ADMIN);
    assert_eq!(book(&mut env).await.entries.len(), 1);
}

#[tokio::test]
async fn studio_powers_do_not_touch_agent_or_profile_paths() {
    let mut env = Env::start().await;
    let owner = env.owner.pubkey();
    let admin = env.funded_wallet().await;
    owner_sends(&mut env, grant_studio_role_ix(&owner, &admin.pubkey())).await.unwrap();
    owner_sends(&mut env, set_partner_share_ix(&owner, &admin.pubkey(), 3_000, 0, 0)).await.unwrap();

    // a studio admin gets no agent power and no config power
    let musician = env.funded_wallet().await;
    env.upsert_profile(&musician, 2).await;
    let res = env
        .send(&[level_ix(&admin.pubkey(), &musician.pubkey(), 5, false)], &[&admin])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);
    let res = env
        .send(&[authorize_agent_ix(&admin.pubkey(), &admin.pubkey(), 0x1f)], &[&admin])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);
    env.authorize_agent(&admin.pubkey(), PERM_PEDAGOGICAL).await;
}

#[test]
fn window_math_is_half_open_and_open_ended() {
    let w = win(100, 10, 20);
    assert!(!window_active_at(&w, 9));
    assert!(window_active_at(&w, 10));
    assert!(window_active_at(&w, 19));
    assert!(!window_active_at(&w, 20));
    assert!(window_active_at(&win(1, 5, 0), i64::MAX - 1));

    // back-to-back windows never overlap; a shared start does
    assert_eq!(max_concurrent_bps(&[win(7_000, 0, 10), win(7_000, 10, 0)]), 7_000);
    assert_eq!(max_concurrent_bps(&[win(7_000, 0, 11), win(7_000, 10, 0)]), 14_000);
    assert_eq!(max_concurrent_bps(&[]), 0);
}

#[test]
fn apply_partner_share_is_atomic_on_error_paths() {
    let mut entries = Vec::new();
    let a = AnchorPubkey::new_unique();
    apply_partner_share(&mut entries, a, 9_000, 0, 0).unwrap();
    let b = AnchorPubkey::new_unique();
    assert!(apply_partner_share(&mut entries, b, 1_001, 0, 0).is_err());
    apply_partner_share(&mut entries, b, 1_000, 0, 0).unwrap();
    apply_partner_share(&mut entries, a, 0, 0, 0).unwrap();
    assert_eq!(entries.len(), 1);
    // removing an unknown wallet is a no-op
    apply_partner_share(&mut entries, AnchorPubkey::new_unique(), 0, 0, 0).unwrap();
    assert_eq!(entries.len(), 1);
}