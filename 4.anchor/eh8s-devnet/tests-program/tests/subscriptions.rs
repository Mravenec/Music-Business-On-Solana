mod common;

use common::*;
use eh8s_devnet::{AcademySubscription, GeographicSubscription, MONTH_SECONDS};
use solana_sdk::signature::Signer;

#[tokio::test]
async fn academy_rejects_unknown_plan_and_months_outside_1_to_12() {
    let mut env = Env::start().await;
    let (payer, payer_usdc) = env.wallet_with_usdc(2_000 * USDC).await;
    let instructor = env.funded_wallet().await;
    let instructor_usdc = env.create_ata(&instructor.pubkey()).await;
    let treasury = env.treasury_usdc;
    for (plan, months) in [(0u8, 1u8), (4, 1), (1, 0), (1, 13)] {
        let res = env
            .send(&[academy_ix(&payer.pubkey(), &payer_usdc, &treasury, &instructor_usdc, plan, months)], &[&payer])
            .await;
        assert_eh8s_err(res, E_INVALID_PLAN);
    }
    assert_eq!(env.token_balance(&treasury).await, 0);
}

#[tokio::test]
async fn academy_charges_price_times_months_on_chain_and_renews_from_expiry() {
    let mut env = Env::start().await;
    let (payer, payer_usdc) = env.wallet_with_usdc(300 * USDC).await;
    let instructor = env.funded_wallet().await;
    let instructor_usdc = env.create_ata(&instructor.pubkey()).await;
    let treasury = env.treasury_usdc;

    // Band plan ($55) x 3 months = $165: 85% treasury, 15% instructor.
    env.send(&[academy_ix(&payer.pubkey(), &payer_usdc, &treasury, &instructor_usdc, 2, 3)], &[&payer])
        .await
        .unwrap();
    assert_eq!(env.token_balance(&treasury).await, 140_250_000);
    assert_eq!(env.token_balance(&instructor_usdc).await, 24_750_000);
    let first: AcademySubscription = env.fetch(&academy_pda(&payer.pubkey())).await;
    assert_eq!(first.plan_code, 2);
    assert_eq!(first.months_paid, 3);
    assert_eq!(first.amount_usdc, 165 * USDC);
    assert_eq!(first.expires_at - first.started_at, 3 * MONTH_SECONDS);

    // Renew while still active: Pro ($90) x 1 extends from the current expiry, not from now.
    env.send(&[academy_ix(&payer.pubkey(), &payer_usdc, &treasury, &instructor_usdc, 3, 1)], &[&payer])
        .await
        .unwrap();
    let renewed: AcademySubscription = env.fetch(&academy_pda(&payer.pubkey())).await;
    assert_eq!(renewed.plan_code, 3);
    assert_eq!(renewed.months_paid, 4);
    assert_eq!(renewed.amount_usdc, 90 * USDC);
    assert_eq!(renewed.started_at, first.started_at);
    assert_eq!(renewed.expires_at, first.expires_at + MONTH_SECONDS);
    assert_eq!(env.token_balance(&payer_usdc).await, 300 * USDC - 165 * USDC - 90 * USDC);
}

#[tokio::test]
async fn academy_price_cannot_be_underpaid() {
    let mut env = Env::start().await;
    let (payer, payer_usdc) = env.wallet_with_usdc(29 * USDC).await;
    let instructor = env.funded_wallet().await;
    let instructor_usdc = env.create_ata(&instructor.pubkey()).await;
    let treasury = env.treasury_usdc;
    let res = env
        .send(&[academy_ix(&payer.pubkey(), &payer_usdc, &treasury, &instructor_usdc, 1, 1)], &[&payer])
        .await;
    assert_failed(res);
    assert_eq!(env.token_balance(&payer_usdc).await, 29 * USDC);
}

#[tokio::test]
async fn geographic_validates_tier_months_and_geo_code() {
    let mut env = Env::start().await;
    let (payer, payer_usdc) = env.wallet_with_usdc(1_000 * USDC).await;
    let treasury = env.treasury_usdc;
    let bad = [
        ("MX", 0u8, 1u8),
        ("MX", 6, 1),
        ("MX", 1, 0),
        ("MX", 1, 13),
        ("mx", 1, 1),
        ("M", 1, 1),
        ("MX-CDMX", 1, 1),
        ("1X", 1, 1),
        ("MX_CDMX_CENTRO_NORTE_XYZ1", 1, 1),
    ];
    for (code, tier, months) in bad {
        let res = env
            .send(&[geo_ix(&payer.pubkey(), &payer_usdc, &treasury, code, tier, months)], &[&payer])
            .await;
        assert_eh8s_err(res, E_INVALID_GEO_SUBSCRIPTION);
    }
    assert_eq!(env.token_balance(&treasury).await, 0);
}

#[tokio::test]
async fn geographic_zone_accounts_are_per_code_and_renew() {
    let mut env = Env::start().await;
    let (payer, payer_usdc) = env.wallet_with_usdc(1_500 * USDC).await;
    let treasury = env.treasury_usdc;

    // City tier ($15) x 2 months for MX_CDMX = $30, all to the treasury.
    env.send(&[geo_ix(&payer.pubkey(), &payer_usdc, &treasury, "MX_CDMX", 2, 2)], &[&payer])
        .await
        .unwrap();
    assert_eq!(env.token_balance(&treasury).await, 30 * USDC);
    let city: GeographicSubscription = env.fetch(&geo_pda(&payer.pubkey(), "MX_CDMX")).await;
    assert_eq!(city.geo_code, "MX_CDMX");
    assert_eq!(city.tier_code, 2);
    assert_eq!(city.expires_at - city.started_at, 2 * MONTH_SECONDS);

    // Country tier ($35) for MX lives in its own account.
    env.send(&[geo_ix(&payer.pubkey(), &payer_usdc, &treasury, "MX", 3, 1)], &[&payer])
        .await
        .unwrap();
    let country: GeographicSubscription = env.fetch(&geo_pda(&payer.pubkey(), "MX")).await;
    assert_eq!(country.tier_code, 3);
    assert_eq!(env.token_balance(&treasury).await, 65 * USDC);

    // Global tier ($99) x 12 on the city zone extends from its current expiry.
    env.send(&[geo_ix(&payer.pubkey(), &payer_usdc, &treasury, "MX_CDMX", 5, 12)], &[&payer])
        .await
        .unwrap();
    let renewed: GeographicSubscription = env.fetch(&geo_pda(&payer.pubkey(), "MX_CDMX")).await;
    assert_eq!(renewed.tier_code, 5);
    assert_eq!(renewed.months_paid, 14);
    assert_eq!(renewed.amount_usdc, 1_188 * USDC);
    assert_eq!(renewed.expires_at, city.expires_at + 12 * MONTH_SECONDS);
}

#[test]
fn renewal_and_price_math() {
    use eh8s_devnet::{academy_plan_price, geo_tier_price, renew_expiry, valid_geo_code};
    assert_eq!(renew_expiry(0, 1_000, 1).unwrap(), 1_000 + MONTH_SECONDS);
    assert_eq!(renew_expiry(900, 1_000, 2).unwrap(), 1_000 + 2 * MONTH_SECONDS);
    assert_eq!(renew_expiry(5_000_000, 1_000, 1).unwrap(), 5_000_000 + MONTH_SECONDS);
    assert!(renew_expiry(i64::MAX - 10, 0, 1).is_err());
    assert_eq!(academy_plan_price(1).unwrap(), 30 * USDC);
    assert_eq!(academy_plan_price(2).unwrap(), 55 * USDC);
    assert_eq!(academy_plan_price(3).unwrap(), 90 * USDC);
    assert!(academy_plan_price(4).is_err());
    let tiers: Vec<u64> = (1..=5).map(|t| geo_tier_price(t).unwrap() / USDC).collect();
    assert_eq!(tiers, vec![5, 15, 35, 65, 99]);
    assert!(geo_tier_price(0).is_err());
    assert!(valid_geo_code("MX_CDMX_CENTRO"));
    assert!(valid_geo_code("US_NY_10001"));
    assert!(!valid_geo_code("mx_cdmx"));
    assert!(!valid_geo_code("MX CDMX"));
}
