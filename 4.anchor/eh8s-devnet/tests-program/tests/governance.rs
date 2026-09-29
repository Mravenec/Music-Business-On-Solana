mod common;

use anchor_spl::associated_token::get_associated_token_address;
use common::*;
use solana_sdk::pubkey::Pubkey;
use solana_sdk::signature::{Keypair, Signer};

async fn with_treasury(env: &mut Env) -> Pubkey {
    let authority = env.authority.insecure_clone();
    let ix = init_treasury_ix(&authority.pubkey(), &env.mint);
    env.send(&[ix], &[&authority]).await.expect("init_treasury");
    get_associated_token_address(&treasury_authority(), &env.mint)
}

/// Mints what an academy payment of `gross` leaves in the treasury PDA ATA (85% of it).
async fn fund_treasury(env: &mut Env, treasury: &Pubkey, gross: u64) {
    let mint = env.mint;
    env.mint_to(&mint, treasury, gross / 100 * 85).await;
}

async fn init_gov(env: &mut Env, signers: Vec<Pubkey>, threshold: u8) {
    let owner = env.owner.insecure_clone();
    env.send(&[init_governance_ix(&owner.pubkey(), signers, threshold)], &[&owner])
        .await
        .expect("init_governance");
}

async fn gov(env: &mut Env) -> eh8s_devnet::Governance {
    env.fetch(&governance_pda()).await
}

#[tokio::test]
async fn init_governance_is_owner_only_once_with_valid_signer_set() {
    let mut env = Env::start().await;
    let owner = env.owner.insecure_clone();
    let stranger = env.funded_wallet().await;
    let s = [Keypair::new().pubkey(), Keypair::new().pubkey(), Keypair::new().pubkey()];

    let res = env
        .send(&[init_governance_ix(&stranger.pubkey(), vec![stranger.pubkey()], 1)], &[&stranger])
        .await;
    assert_eh8s_err(res, E_UNAUTHORIZED);

    for (signers, threshold) in [
        (vec![], 1u8),
        (vec![s[0]], 0),
        (vec![s[0], s[1]], 3),
        (vec![s[0], s[0]], 1),
        (vec![s[0], Pubkey::default()], 1),
        ((0..6).map(|_| Keypair::new().pubkey()).collect(), 3),
    ] {
        let res = env.send(&[init_governance_ix(&owner.pubkey(), signers, threshold)], &[&owner]).await;
        assert_eh8s_err(res, E_INVALID_GOVERNANCE);
    }

    init_gov(&mut env, vec![owner.pubkey(), s[1], s[2]], 2).await;
    let g = gov(&mut env).await;
    assert_eq!(g.signers, vec![owner.pubkey(), s[1], s[2]]);
    assert_eq!((g.threshold, g.epoch, g.proposal_count), (2, 0, 0));

    let res = env.send(&[init_governance_ix(&owner.pubkey(), vec![owner.pubkey()], 1)], &[&owner]).await;
    assert_failed(res);
}

#[tokio::test]
async fn direct_withdraw_and_authorize_work_before_governance_and_are_refused_after() {
    let mut env = Env::start().await;
    let treasury = with_treasury(&mut env).await;
    fund_treasury(&mut env, &treasury, 10 * USDC).await;
    let owner = env.owner.insecure_clone();
    let owner_usdc = env.treasury_usdc;
    let agent = Keypair::new().pubkey();

    env.send(&[withdraw_treasury_ix(&owner.pubkey(), &treasury, &owner_usdc, USDC)], &[&owner])
        .await
        .expect("direct withdraw before governance");
    env.authorize_agent(&agent, PERM_STAGE).await;

    init_gov(&mut env, vec![owner.pubkey()], 1).await;

    let res = env
        .send(&[withdraw_treasury_ix(&owner.pubkey(), &treasury, &owner_usdc, USDC)], &[&owner])
        .await;
    assert_eh8s_err(res, E_GOVERNANCE_ACTIVE);
    let res = env
        .send(&[authorize_agent_ix(&owner.pubkey(), &agent, PERM_VAULT)], &[&owner])
        .await;
    assert_eh8s_err(res, E_GOVERNANCE_ACTIVE);
    assert_eq!(env.token_balance(&owner_usdc).await, USDC);
}

#[tokio::test]
async fn withdraw_proposal_needs_threshold_each_signer_once_and_runs_once() {
    let mut env = Env::start().await;
    let treasury = with_treasury(&mut env).await;
    fund_treasury(&mut env, &treasury, 100 * USDC).await;
    let owner = env.owner.insecure_clone();
    let owner_usdc = env.treasury_usdc;
    let s2 = env.funded_wallet().await;
    let s3 = env.funded_wallet().await;
    let stranger = env.funded_wallet().await;
    init_gov(&mut env, vec![owner.pubkey(), s2.pubkey(), s3.pubkey()], 2).await;

    let res = env
        .send(&[propose_ix(&stranger.pubkey(), 0, ProposalArgs::withdraw(USDC, owner_usdc))], &[&stranger])
        .await;
    assert_eh8s_err(res, E_NOT_GOVERNANCE_SIGNER);

    env.send(&[propose_ix(&owner.pubkey(), 0, ProposalArgs::withdraw(30 * USDC, owner_usdc))], &[&owner])
        .await
        .expect("propose withdraw");
    let p: eh8s_devnet::Proposal = env.fetch(&proposal_pda(0)).await;
    assert_eq!((p.id, p.kind, p.amount_usdc, p.target), (0, KIND_WITHDRAW, 30 * USDC, owner_usdc));
    assert_eq!(p.approvals, vec![owner.pubkey()]);

    let res = env.send(&[execute_withdraw_ix(&stranger.pubkey(), 0, &treasury, &owner_usdc)], &[&stranger]).await;
    assert_eh8s_err(res, E_THRESHOLD_NOT_MET);
    let res = env.send(&[approve_proposal_ix(&stranger.pubkey(), 0)], &[&stranger]).await;
    assert_eh8s_err(res, E_NOT_GOVERNANCE_SIGNER);
    let res = env.send(&[approve_proposal_ix(&owner.pubkey(), 0)], &[&owner]).await;
    assert_eh8s_err(res, E_ALREADY_APPROVED);

    env.send(&[approve_proposal_ix(&s2.pubkey(), 0)], &[&s2]).await.expect("s2 approves");
    env.send(&[execute_withdraw_ix(&stranger.pubkey(), 0, &treasury, &owner_usdc)], &[&stranger])
        .await
        .expect("execute withdraw");
    assert_eq!(env.token_balance(&owner_usdc).await, 30 * USDC);
    assert_eq!(env.token_balance(&treasury).await, 55 * USDC);

    let res = env.send(&[execute_withdraw_ix(&stranger.pubkey(), 0, &treasury, &owner_usdc)], &[&stranger]).await;
    assert_eh8s_err(res, E_PROPOSAL_EXECUTED);
    let res = env.send(&[approve_proposal_ix(&s3.pubkey(), 0)], &[&s3]).await;
    assert_eh8s_err(res, E_PROPOSAL_EXECUTED);
    assert_eq!(gov(&mut env).await.proposal_count, 1);
}

#[tokio::test]
async fn withdraw_destination_must_be_the_target_usdc_account_of_a_signer() {
    let mut env = Env::start().await;
    let treasury = with_treasury(&mut env).await;
    fund_treasury(&mut env, &treasury, 10 * USDC).await;
    let owner = env.owner.insecure_clone();
    let owner_usdc = env.treasury_usdc;
    let thief = env.funded_wallet().await;
    let thief_usdc = env.create_ata(&thief.pubkey()).await;
    init_gov(&mut env, vec![owner.pubkey()], 1).await;

    let res = env.send(&[propose_ix(&owner.pubkey(), 0, ProposalArgs::withdraw(0, owner_usdc))], &[&owner]).await;
    assert_eh8s_err(res, E_ZERO);

    env.send(&[propose_ix(&owner.pubkey(), 0, ProposalArgs::withdraw(USDC, thief_usdc))], &[&owner])
        .await
        .unwrap();
    let res = env.send(&[execute_withdraw_ix(&owner.pubkey(), 0, &treasury, &thief_usdc)], &[&owner]).await;
    assert_eh8s_err(res, E_DESTINATION_NOT_SIGNER);

    env.send(&[propose_ix(&owner.pubkey(), 1, ProposalArgs::withdraw(USDC, owner_usdc))], &[&owner])
        .await
        .unwrap();
    let res = env.send(&[execute_withdraw_ix(&owner.pubkey(), 1, &treasury, &thief_usdc)], &[&owner]).await;
    assert_eh8s_err(res, E_DESTINATION_NOT_SIGNER);
    let res = env.send(&[execute_signers_ix(&owner.pubkey(), 1)], &[&owner]).await;
    assert_eh8s_err(res, E_INVALID_PROPOSAL);

    assert_eq!(env.token_balance(&thief_usdc).await, 0);
    assert_eq!(env.token_balance(&treasury).await, 8_500_000);
}

#[tokio::test]
async fn agent_proposal_grants_powers_and_rejects_unknown_bits() {
    let mut env = Env::start().await;
    let owner = env.owner.insecure_clone();
    let agent = Keypair::new().pubkey();
    init_gov(&mut env, vec![owner.pubkey()], 1).await;

    let res = env.send(&[propose_ix(&owner.pubkey(), 0, ProposalArgs::agent(agent, 0x40))], &[&owner]).await;
    assert_eh8s_err(res, E_INVALID_PERMISSIONS);
    let res = env.send(&[propose_ix(&owner.pubkey(), 0, ProposalArgs { kind: 9, ..ProposalArgs::agent(agent, 0) })], &[&owner]).await;
    assert_eh8s_err(res, E_INVALID_PROPOSAL);

    env.send(&[propose_ix(&owner.pubkey(), 0, ProposalArgs::agent(agent, PERM_STAGE | PERM_VAULT))], &[&owner])
        .await
        .unwrap();
    env.send(&[execute_agent_ix(&owner.pubkey(), 0, &agent)], &[&owner]).await.expect("execute agent");
    let a: eh8s_devnet::AgentAuthority = env.fetch(&agent_pda(&agent)).await;
    assert_eq!((a.agent, a.permissions), (agent, PERM_STAGE | PERM_VAULT));

    env.send(&[propose_ix(&owner.pubkey(), 1, ProposalArgs::agent(agent, 0))], &[&owner]).await.unwrap();
    env.send(&[execute_agent_ix(&owner.pubkey(), 1, &agent)], &[&owner]).await.expect("revoke");
    let a: eh8s_devnet::AgentAuthority = env.fetch(&agent_pda(&agent)).await;
    assert_eq!(a.permissions, 0);
}

#[tokio::test]
async fn signer_set_update_moves_threshold_and_makes_older_proposals_stale() {
    let mut env = Env::start().await;
    let treasury = with_treasury(&mut env).await;
    fund_treasury(&mut env, &treasury, 100 * USDC).await;
    let owner = env.owner.insecure_clone();
    let owner_usdc = env.treasury_usdc;
    let s2 = env.funded_wallet().await;
    let s3 = env.funded_wallet().await;
    let s4 = env.funded_wallet().await;
    let s4_usdc = env.create_ata(&s4.pubkey()).await;
    init_gov(&mut env, vec![owner.pubkey(), s2.pubkey(), s3.pubkey()], 3).await;

    env.send(&[propose_ix(&owner.pubkey(), 0, ProposalArgs::withdraw(10 * USDC, owner_usdc))], &[&owner])
        .await
        .unwrap();
    let res = env
        .send(&[propose_ix(&owner.pubkey(), 1, ProposalArgs::signers(vec![owner.pubkey(), s2.pubkey()], 3))], &[&owner])
        .await;
    assert_eh8s_err(res, E_INVALID_GOVERNANCE);

    let next = vec![owner.pubkey(), s2.pubkey(), s3.pubkey(), s4.pubkey()];
    env.send(&[propose_ix(&owner.pubkey(), 1, ProposalArgs::signers(next.clone(), 2))], &[&owner])
        .await
        .unwrap();
    env.send(&[approve_proposal_ix(&s2.pubkey(), 1)], &[&s2]).await.unwrap();
    let res = env.send(&[execute_signers_ix(&owner.pubkey(), 1)], &[&owner]).await;
    assert_eh8s_err(res, E_THRESHOLD_NOT_MET);
    env.send(&[approve_proposal_ix(&s3.pubkey(), 1)], &[&s3]).await.unwrap();
    env.send(&[execute_signers_ix(&owner.pubkey(), 1)], &[&owner]).await.expect("execute signers");
    let g = gov(&mut env).await;
    assert_eq!((g.signers, g.threshold, g.epoch), (next, 2, 1));

    let res = env.send(&[approve_proposal_ix(&s2.pubkey(), 0)], &[&s2]).await;
    assert_eh8s_err(res, E_STALE_PROPOSAL);
    let res = env.send(&[execute_withdraw_ix(&owner.pubkey(), 0, &treasury, &owner_usdc)], &[&owner]).await;
    assert_eh8s_err(res, E_STALE_PROPOSAL);

    env.send(&[propose_ix(&s4.pubkey(), 2, ProposalArgs::withdraw(5 * USDC, s4_usdc))], &[&s4])
        .await
        .expect("new signer proposes");
    env.send(&[approve_proposal_ix(&s2.pubkey(), 2)], &[&s2]).await.unwrap();
    env.send(&[execute_withdraw_ix(&s3.pubkey(), 2, &treasury, &s4_usdc)], &[&s3])
        .await
        .expect("2-of-4 withdraw");
    assert_eq!(env.token_balance(&s4_usdc).await, 5 * USDC);
    assert_eq!(env.token_balance(&owner_usdc).await, 0);
}
