mod common;

use common::*;
use solana_sdk::signature::{Keypair, Signer};

const HASH: [u8; 32] = [7u8; 32];
const E_MATH_OVERFLOW: u32 = 2;

/// Venue wallet with 5,000 USDC and an owner-approved listing `venue_id`.
async fn approved_venue(env: &mut Env, venue_id: u64) -> (Keypair, solana_sdk::pubkey::Pubkey) {
    let (venue, venue_usdc) = env.wallet_with_usdc(5_000 * USDC).await;
    env.send(&[register_venue_ix(&venue.pubkey(), venue_id, "Bar La Cueva", *b"MEX", 180, 3)], &[&venue])
        .await
        .unwrap();
    let owner = env.owner.insecure_clone();
    env.send(&[approve_venue_ix(&owner.pubkey(), &venue_pda(&venue.pubkey(), venue_id), false)], &[&owner])
        .await
        .unwrap();
    (venue, venue_usdc)
}

fn booking(gross: u64) -> Booking {
    Booking { venue_id: 1, concert_id: 41, band_id: 9, date_ymd: 20261015, gross }
}

#[tokio::test]
async fn venue_registers_pending_and_only_stage_approves() {
    let mut env = Env::start().await;
    let venue = env.funded_wallet().await;
    env.send(&[register_venue_ix(&venue.pubkey(), 1, "Foro Disponible", *b"MEX", 400, 2)], &[&venue])
        .await
        .unwrap();
    let listing = venue_pda(&venue.pubkey(), 1);
    let l: eh8s_devnet::VenueListing = env.fetch(&listing).await;
    assert_eq!((l.venue, l.status, l.capacity, l.contract_type), (venue.pubkey(), 0, 400, 2));
    assert_eq!(l.name, "Foro Disponible");
    assert_eq!(&l.country, b"MEX");

    let stranger = env.funded_wallet().await;
    let harmony = env.funded_wallet().await;
    let stage = env.funded_wallet().await;
    env.authorize_agent(&harmony.pubkey(), PERM_HARMONY).await;
    env.authorize_agent(&stage.pubkey(), PERM_STAGE).await;
    let res = env.send(&[approve_venue_ix(&stranger.pubkey(), &listing, false)], &[&stranger]).await;
    assert_eh8s_err(res, E_UNAUTHORIZED);
    let res = env.send(&[approve_venue_ix(&harmony.pubkey(), &listing, true)], &[&harmony]).await;
    assert_eh8s_err(res, E_UNAUTHORIZED);
    env.send(&[approve_venue_ix(&stage.pubkey(), &listing, true)], &[&stage])
        .await
        .unwrap();
    let l: eh8s_devnet::VenueListing = env.fetch(&listing).await;
    assert_eq!(l.status, 1);
}

#[tokio::test]
async fn register_rejects_bad_listing() {
    let mut env = Env::start().await;
    let venue = env.funded_wallet().await;
    for (id, name, country, cap, ct, code) in [
        (1u64, "", *b"MEX", 100u32, 1u8, E_INVALID_VENUE),
        (2, "Club", *b"MEX", 0, 1, E_INVALID_VENUE),
        (3, "Club", *b"MEX", 100, 5, E_INVALID_VENUE),
        (4, "Club", *b"MEX", 100, 0, E_INVALID_VENUE),
        (5, "Club", *b"mex", 100, 1, E_INVALID_COUNTRY),
    ] {
        let res = env.send(&[register_venue_ix(&venue.pubkey(), id, name, country, cap, ct)], &[&venue]).await;
        assert_eh8s_err(res, code);
    }
    let long = "x".repeat(65);
    let res = env.send(&[register_venue_ix(&venue.pubkey(), 6, &long, *b"MEX", 100, 1)], &[&venue]).await;
    assert_failed(res);
}

#[tokio::test]
async fn unapproved_venue_cannot_book() {
    let mut env = Env::start().await;
    let m = env.funded_wallet().await;
    env.band_with(9, &[&m]).await;
    let (venue, venue_usdc) = env.wallet_with_usdc(1_000 * USDC).await;
    env.send(&[register_venue_ix(&venue.pubkey(), 1, "Club", *b"MEX", 100, 1)], &[&venue])
        .await
        .unwrap();
    let mint = env.mint;
    let res = env.send(&[propose_booking_ix(&venue.pubkey(), &venue_usdc, &mint, &booking(500 * USDC))], &[&venue]).await;
    assert_eh8s_err(res, E_VENUE_NOT_APPROVED);
}

#[tokio::test]
async fn propose_rejects_a_usdc_account_the_venue_does_not_own() {
    let mut env = Env::start().await;
    let member = env.funded_wallet().await;
    env.band_with(9, &[&member]).await;
    let (venue, _venue_usdc) = approved_venue(&mut env, 1).await;
    let (_other, other_usdc) = env.wallet_with_usdc(100 * USDC).await;
    let res = env
        .send(
            &[propose_booking_ix(&venue.pubkey(), &other_usdc, &env.mint, &booking(500 * USDC))],
            &[&venue],
        )
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);
}

#[tokio::test]
async fn escrow_confirm_and_vault_agent_settle() {
    let mut env = Env::start().await;
    let (a, b) = (env.funded_wallet().await, env.funded_wallet().await);
    env.band_with(9, &[&a, &b]).await;
    let (venue, venue_usdc) = approved_venue(&mut env, 1).await;
    let mint = env.mint;
    let bk = booking(1_000 * USDC);
    env.send(&[propose_booking_ix(&venue.pubkey(), &venue_usdc, &mint, &bk)], &[&venue])
        .await
        .unwrap();
    let access = access_pda(&venue.pubkey(), bk.band_id, bk.date_ymd);
    assert_eq!(env.token_balance(&venue_usdc).await, 4_000 * USDC);
    assert_eq!(env.token_balance(&escrow_pda(&access)).await, 1_000 * USDC);
    let t: eh8s_devnet::VenueAccessToken = env.fetch(&access).await;
    assert_eq!((t.status, t.gross_usdc, t.concert_id, t.date_ymd), (0, 1_000 * USDC, 41, 20261015));

    let stage = env.funded_wallet().await;
    env.authorize_agent(&stage.pubkey(), PERM_STAGE).await;
    env.send(&[confirm_booking_ix(&stage.pubkey(), &access, HASH, true)], &[&stage])
        .await
        .unwrap();
    let t: eh8s_devnet::VenueAccessToken = env.fetch(&access).await;
    assert_eq!((t.status, t.contract_hash), (1, HASH));

    let vault_agent = env.funded_wallet().await;
    env.authorize_agent(&vault_agent.pubkey(), PERM_VAULT).await;
    let accts = SettleBookingAccounts {
        signer: vault_agent.pubkey(),
        venue: venue.pubkey(),
        venue_usdc,
        treasury_usdc: env.treasury_usdc,
        vault_usdc: env.vault_usdc,
        member_profiles: vec![musician_pda(&a.pubkey()), musician_pda(&b.pubkey())],
        with_agent: true,
    };
    env.send(&[settle_booking_ix(&accts, &bk, 1_000 * USDC, 100 * USDC)], &[&vault_agent])
        .await
        .unwrap();

    // net 900 → fee 15% = 135 → treasury; pool 765 → vault; 382.5 each; expenses 100 back.
    assert_eq!(env.token_balance(&venue_usdc).await, 4_100 * USDC);
    assert_eq!(env.token_balance(&escrow_pda(&access)).await, 0);
    let treasury = env.treasury_usdc;
    let vault = env.vault_usdc;
    assert_eq!(env.token_balance(&treasury).await, 135 * USDC);
    assert_eq!(env.token_balance(&vault).await, 765 * USDC);
    assert_eq!(env.pending(&a.pubkey()).await, 382_500_000);
    assert_eq!(env.pending(&b.pubkey()).await, 382_500_000);
    let t: eh8s_devnet::VenueAccessToken = env.fetch(&access).await;
    assert_eq!(t.status, 2);
    let s: eh8s_devnet::ConcertSettlement = env.fetch(&concert_pda(&venue.pubkey(), 41)).await;
    assert_eq!((s.settled, s.eh8s_fee_usdc, s.band_pool_usdc), (1, 135 * USDC, 765 * USDC));

    let res = env.send(&[settle_booking_ix(&accts, &bk, 1_000 * USDC, 100 * USDC)], &[&vault_agent]).await;
    assert_eh8s_err(res, E_ALREADY_SETTLED);
}

#[tokio::test]
async fn settle_denies_wrong_signer_status_and_amounts() {
    let mut env = Env::start().await;
    let m = env.funded_wallet().await;
    env.band_with(9, &[&m]).await;
    let (venue, venue_usdc) = approved_venue(&mut env, 1).await;
    let mint = env.mint;
    let bk = booking(1_000 * USDC);
    env.send(&[propose_booking_ix(&venue.pubkey(), &venue_usdc, &mint, &bk)], &[&venue])
        .await
        .unwrap();
    let access = access_pda(&venue.pubkey(), bk.band_id, bk.date_ymd);
    let owner = env.owner.insecure_clone();
    let mut accts = SettleBookingAccounts {
        signer: owner.pubkey(),
        venue: venue.pubkey(),
        venue_usdc,
        treasury_usdc: env.treasury_usdc,
        vault_usdc: env.vault_usdc,
        member_profiles: vec![musician_pda(&m.pubkey())],
        with_agent: false,
    };

    let res = env.send(&[settle_booking_ix(&accts, &bk, 1_000 * USDC, 0)], &[&owner]).await;
    assert_eh8s_err(res, E_INVALID_BOOKING_STATUS);

    env.send(&[confirm_booking_ix(&owner.pubkey(), &access, HASH, false)], &[&owner])
        .await
        .unwrap();

    let stage = env.funded_wallet().await;
    env.authorize_agent(&stage.pubkey(), PERM_STAGE).await;
    accts.signer = stage.pubkey();
    accts.with_agent = true;
    let res = env.send(&[settle_booking_ix(&accts, &bk, 1_000 * USDC, 0)], &[&stage]).await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    accts.signer = venue.pubkey();
    accts.with_agent = false;
    let res = env.send(&[settle_booking_ix(&accts, &bk, 1_000 * USDC, 0)], &[&venue]).await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    accts.signer = owner.pubkey();
    accts.with_agent = false;
    let res = env.send(&[settle_booking_ix(&accts, &bk, 900 * USDC, 0)], &[&owner]).await;
    assert_eh8s_err(res, E_ESCROW_MISMATCH);
    let res = env.send(&[settle_booking_ix(&accts, &bk, 1_000 * USDC, 1_001 * USDC)], &[&owner]).await;
    assert_eh8s_err(res, E_MATH_OVERFLOW);
    let res = env.send(&[settle_booking_ix(&accts, &bk, 1_000 * USDC, 1_000 * USDC)], &[&owner]).await;
    assert_eh8s_err(res, E_ZERO);

    env.send(&[settle_booking_ix(&accts, &bk, 1_000 * USDC, 0)], &[&owner])
        .await
        .unwrap();
    assert_eq!(env.pending(&m.pubkey()).await, 850 * USDC);
}

#[tokio::test]
async fn venue_cancels_before_confirm_only() {
    let mut env = Env::start().await;
    let m = env.funded_wallet().await;
    env.band_with(9, &[&m]).await;
    let (venue, venue_usdc) = approved_venue(&mut env, 1).await;
    let mint = env.mint;
    let bk = booking(700 * USDC);
    env.send(&[propose_booking_ix(&venue.pubkey(), &venue_usdc, &mint, &bk)], &[&venue])
        .await
        .unwrap();
    let access = access_pda(&venue.pubkey(), bk.band_id, bk.date_ymd);

    let (stranger, stranger_usdc) = env.wallet_with_usdc(0).await;
    let res = env.send(&[cancel_booking_ix(&stranger.pubkey(), &access, &stranger_usdc)], &[&stranger]).await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    env.send(&[cancel_booking_ix(&venue.pubkey(), &access, &venue_usdc)], &[&venue])
        .await
        .unwrap();
    assert_eq!(env.token_balance(&venue_usdc).await, 5_000 * USDC);
    assert!(env.ctx.banks_client.get_account(access).await.unwrap().is_none());
    assert!(env.ctx.banks_client.get_account(escrow_pda(&access)).await.unwrap().is_none());

    // Same band/date can be proposed again after a cancel; once confirmed it cannot be cancelled.
    env.send(&[propose_booking_ix(&venue.pubkey(), &venue_usdc, &mint, &bk)], &[&venue])
        .await
        .unwrap();
    let owner = env.owner.insecure_clone();
    env.send(&[confirm_booking_ix(&owner.pubkey(), &access, HASH, false)], &[&owner])
        .await
        .unwrap();
    let res = env.send(&[cancel_booking_ix(&venue.pubkey(), &access, &venue_usdc)], &[&venue]).await;
    assert_eh8s_err(res, E_INVALID_BOOKING_STATUS);
}

#[tokio::test]
async fn confirm_rejects_zero_hash_bad_signer_and_bad_date() {
    let mut env = Env::start().await;
    let m = env.funded_wallet().await;
    env.band_with(9, &[&m]).await;
    let (venue, venue_usdc) = approved_venue(&mut env, 1).await;
    let mint = env.mint;

    let mut bad = booking(10 * USDC);
    bad.date_ymd = 20261340;
    let res = env.send(&[propose_booking_ix(&venue.pubkey(), &venue_usdc, &mint, &bad)], &[&venue]).await;
    assert_eh8s_err(res, E_INVALID_BOOKING);

    let bk = booking(10 * USDC);
    env.send(&[propose_booking_ix(&venue.pubkey(), &venue_usdc, &mint, &bk)], &[&venue])
        .await
        .unwrap();
    let access = access_pda(&venue.pubkey(), bk.band_id, bk.date_ymd);
    let res = env.send(&[confirm_booking_ix(&venue.pubkey(), &access, HASH, false)], &[&venue]).await;
    assert_eh8s_err(res, E_UNAUTHORIZED);
    let owner = env.owner.insecure_clone();
    let res = env.send(&[confirm_booking_ix(&owner.pubkey(), &access, [0u8; 32], false)], &[&owner]).await;
    assert_eh8s_err(res, E_INVALID_BOOKING);
    env.send(&[confirm_booking_ix(&owner.pubkey(), &access, HASH, false)], &[&owner])
        .await
        .unwrap();
    let res = env.send(&[confirm_booking_ix(&owner.pubkey(), &access, HASH, false)], &[&owner]).await;
    assert_eh8s_err(res, E_INVALID_BOOKING_STATUS);
}

#[tokio::test]
async fn booking_needs_existing_band_vault() {
    let mut env = Env::start().await;
    let (venue, venue_usdc) = approved_venue(&mut env, 1).await;
    let mint = env.mint;
    let res = env.send(&[propose_booking_ix(&venue.pubkey(), &venue_usdc, &mint, &booking(10 * USDC))], &[&venue]).await;
    assert_failed(res);
}
