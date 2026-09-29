mod common;

use anchor_lang::Discriminator;
use common::*;
use solana_sdk::{account::Account, rent::Rent, signature::Signer};

#[tokio::test]
async fn owner_authorizes_and_revokes_agent() {
    let mut env = Env::start().await;
    let agent = env.funded_wallet().await;
    env.authorize_agent(&agent.pubkey(), PERM_PEDAGOGICAL | PERM_HARMONY).await;
    let a: eh8s_devnet::AgentAuthority = env.fetch(&agent_pda(&agent.pubkey())).await;
    assert_eq!(a.agent, agent.pubkey());
    assert_eq!(a.permissions, PERM_PEDAGOGICAL | PERM_HARMONY);

    env.authorize_agent(&agent.pubkey(), 0).await;
    let a: eh8s_devnet::AgentAuthority = env.fetch(&agent_pda(&agent.pubkey())).await;
    assert_eq!(a.permissions, 0);
}

#[tokio::test]
async fn authorize_rejects_stranger_and_unknown_bits() {
    let mut env = Env::start().await;
    let agent = env.funded_wallet().await;
    let stranger = env.funded_wallet().await;
    let res = env
        .send(&[authorize_agent_ix(&stranger.pubkey(), &agent.pubkey(), PERM_STAGE)], &[&stranger])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    let owner = env.owner.insecure_clone();
    let res = env
        .send(&[authorize_agent_ix(&owner.pubkey(), &agent.pubkey(), 0x20)], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_PERMISSIONS);
}

#[tokio::test]
async fn musician_cannot_set_own_level() {
    let mut env = Env::start().await;
    let musician = env.funded_wallet().await;
    env.upsert_profile(&musician, 2).await;
    let res = env
        .send(&[level_ix(&musician.pubkey(), &musician.pubkey(), 5, false)], &[&musician])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);
}

#[tokio::test]
async fn pedagogical_agent_sets_level_and_other_bits_cannot() {
    let mut env = Env::start().await;
    let musician = env.funded_wallet().await;
    env.upsert_profile(&musician, 2).await;
    let nexus = env.funded_wallet().await;
    let harmony = env.funded_wallet().await;
    env.authorize_agent(&nexus.pubkey(), PERM_PEDAGOGICAL).await;
    env.authorize_agent(&harmony.pubkey(), PERM_HARMONY).await;

    env.send(&[level_ix(&nexus.pubkey(), &musician.pubkey(), 3, true)], &[&nexus])
        .await
        .unwrap();
    let p: eh8s_devnet::MusicianProfile = env.fetch(&musician_pda(&musician.pubkey())).await;
    assert_eq!(p.enigma_level, 3);

    let res = env
        .send(&[level_ix(&harmony.pubkey(), &musician.pubkey(), 4, true)], &[&harmony])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    let res = env
        .send(&[level_ix(&nexus.pubkey(), &musician.pubkey(), 6, true)], &[&nexus])
        .await;
    assert_eh8s_err(res, E_INVALID_LEVEL);

    env.authorize_agent(&nexus.pubkey(), 0).await;
    let res = env
        .send(&[level_ix(&nexus.pubkey(), &musician.pubkey(), 4, true)], &[&nexus])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);
}

#[tokio::test]
async fn owner_keeps_every_agent_power() {
    let mut env = Env::start().await;
    let musician = env.funded_wallet().await;
    env.upsert_profile(&musician, 2).await;
    let owner = env.owner.insecure_clone();
    env.send(&[level_ix(&owner.pubkey(), &musician.pubkey(), 5, false)], &[&owner])
        .await
        .unwrap();
    let p: eh8s_devnet::MusicianProfile = env.fetch(&musician_pda(&musician.pubkey())).await;
    assert_eq!(p.enigma_level, 5);
}

#[tokio::test]
async fn agent_cannot_borrow_another_agents_authority() {
    let mut env = Env::start().await;
    let musician = env.funded_wallet().await;
    env.upsert_profile(&musician, 2).await;
    let nexus = env.funded_wallet().await;
    let impostor = env.funded_wallet().await;
    env.authorize_agent(&nexus.pubkey(), PERM_PEDAGOGICAL).await;

    let mut ix = level_ix(&impostor.pubkey(), &musician.pubkey(), 4, true);
    ix.accounts[3].pubkey = agent_pda(&nexus.pubkey());
    assert_failed(env.send(&[ix], &[&impostor]).await);
}

#[tokio::test]
async fn harmony_agent_creates_band_and_updates_weights() {
    let mut env = Env::start().await;
    let a = env.funded_wallet().await;
    let b = env.funded_wallet().await;
    env.upsert_profile(&a, 1).await;
    env.upsert_profile(&b, 2).await;
    let harmony = env.funded_wallet().await;
    let nexus = env.funded_wallet().await;
    env.authorize_agent(&harmony.pubkey(), PERM_HARMONY).await;
    env.authorize_agent(&nexus.pubkey(), PERM_PEDAGOGICAL).await;

    let res = env
        .send(&[create_band_as(&nexus.pubkey(), 40, vec![a.pubkey(), b.pubkey()], true)], &[&nexus])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    env.send(&[create_band_as(&harmony.pubkey(), 40, vec![a.pubkey(), b.pubkey()], true)], &[&harmony])
        .await
        .unwrap();
    env.send(&[update_weights_as(&harmony.pubkey(), 40, vec![6_000, 4_000], true)], &[&harmony])
        .await
        .unwrap();
    let v: eh8s_devnet::BandVault = env.fetch(&band_pda(40)).await;
    assert_eq!(v.weights_bps, vec![6_000, 4_000]);

    let res = env
        .send(&[update_weights_as(&nexus.pubkey(), 40, vec![5_000, 5_000], true)], &[&nexus])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);
}

#[tokio::test]
async fn upsert_validates_country() {
    let mut env = Env::start().await;
    let musician = env.funded_wallet().await;
    let res = env
        .send(&[upsert_ix(&musician.pubkey(), 1, *b"usa")], &[&musician])
        .await;
    assert_eh8s_err(res, E_INVALID_COUNTRY);
    env.send(&[upsert_ix(&musician.pubkey(), 1, *b"MEX")], &[&musician])
        .await
        .unwrap();
    let p: eh8s_devnet::MusicianProfile = env.fetch(&musician_pda(&musician.pubkey())).await;
    assert_eq!(&p.country, b"MEX");
}

#[tokio::test]
async fn upsert_migrates_v05_profile_and_keeps_level_and_pending() {
    let mut env = Env::start().await;
    let musician = env.funded_wallet().await;
    let pda = musician_pda(&musician.pubkey());
    let (_, bump) = solana_sdk::pubkey::Pubkey::find_program_address(
        &[b"musician", musician.pubkey().as_ref()],
        &program_id(),
    );

    // v0.5 layout: discriminator, authority, level, instrument, pending, bump (51 bytes).
    let mut data = eh8s_devnet::MusicianProfile::DISCRIMINATOR.to_vec();
    data.extend_from_slice(musician.pubkey().as_ref());
    data.push(4);
    data.push(7);
    data.extend_from_slice(&(7 * USDC).to_le_bytes());
    data.push(bump);
    assert_eq!(data.len(), 51);
    let old = Account {
        lamports: Rent::default().minimum_balance(data.len()),
        data,
        owner: program_id(),
        executable: false,
        rent_epoch: 0,
    };
    env.ctx.set_account(&pda, &old.into());

    env.send(&[upsert_ix(&musician.pubkey(), 9, *b"COL")], &[&musician])
        .await
        .unwrap();
    let acc = env.ctx.banks_client.get_account(pda).await.unwrap().unwrap();
    assert_eq!(acc.data.len(), 8 + 32 + 1 + 1 + 8 + 1 + 3);
    let p: eh8s_devnet::MusicianProfile = env.fetch(&pda).await;
    assert_eq!(p.authority, musician.pubkey());
    assert_eq!(p.enigma_level, 4);
    assert_eq!(p.instrument_code, 9);
    assert_eq!(p.pending_claims_usdc, 7 * USDC);
    assert_eq!(&p.country, b"COL");
}
