//! One-time DevNet step after the v0.5.0 upgrade: create the `["treasury"]` PDA USDC ATA and
//! point `config.treasury_usdc` at it. Idempotent — prints the config when already pointed.
//!
//!   cargo run --example init_treasury -- <upgrade-authority.json> [rpc]

use anchor_lang::{AccountDeserialize, InstructionData, ToAccountMetas};
use anchor_spl::associated_token::{get_associated_token_address, spl_associated_token_account};
use anchor_spl::token::spl_token;
use solana_client::rpc_client::RpcClient;
use solana_sdk::{
    bpf_loader_upgradeable,
    commitment_config::CommitmentConfig,
    instruction::Instruction,
    pubkey::Pubkey,
    signature::{read_keypair_file, Signer},
    system_program,
    transaction::Transaction,
};

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args.is_empty() {
        eprintln!("usage: init_treasury <keypair.json> [rpc]");
        std::process::exit(2);
    }
    let authority = read_keypair_file(&args[0]).expect("keypair");
    let rpc = args
        .get(1)
        .cloned()
        .unwrap_or_else(|| "https://api.devnet.solana.com".to_string());
    let client = RpcClient::new_with_commitment(rpc, CommitmentConfig::confirmed());

    let program_id = eh8s_devnet::ID;
    let config = Pubkey::find_program_address(&[b"eh8s", b"config"], &program_id).0;
    let treasury_authority = Pubkey::find_program_address(&[b"treasury"], &program_id).0;
    let program_data =
        Pubkey::find_program_address(&[program_id.as_ref()], &bpf_loader_upgradeable::id()).0;

    let before = read_config(&client, &config);
    let treasury_usdc = get_associated_token_address(&treasury_authority, &before.usdc_mint);
    println!("treasury_pda    {treasury_authority}");
    if before.treasury_usdc == treasury_usdc {
        println!("status          already pointed at the treasury PDA ATA");
        print_config(&config, &before);
        return;
    }
    println!("previous        {}", before.treasury_usdc);

    let ix = Instruction {
        program_id,
        accounts: eh8s_devnet::accounts::InitTreasury {
            authority: authority.pubkey(),
            config,
            usdc_mint: before.usdc_mint,
            treasury_authority,
            treasury_usdc,
            program: program_id,
            program_data,
            token_program: spl_token::id(),
            associated_token_program: spl_associated_token_account::id(),
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::InitTreasury {}.data(),
    };
    let blockhash = client.get_latest_blockhash().expect("blockhash");
    let tx = Transaction::new_signed_with_payer(&[ix], Some(&authority.pubkey()), &[&authority], blockhash);
    let sig = client.send_and_confirm_transaction(&tx).expect("init_treasury");
    println!("signature       {sig}");
    println!("explorer        https://explorer.solana.com/tx/{sig}?cluster=devnet");
    let after = read_config(&client, &config);
    assert_eq!(after.treasury_usdc, treasury_usdc);
    print_config(&config, &after);
}

fn read_config(client: &RpcClient, config: &Pubkey) -> eh8s_devnet::Eh8sConfig {
    let acc = client.get_account(config).expect("config must be initialized first");
    eh8s_devnet::Eh8sConfig::try_deserialize(&mut acc.data.as_slice()).expect("decode config")
}

fn print_config(address: &Pubkey, cfg: &eh8s_devnet::Eh8sConfig) {
    println!("config          {address}");
    println!("owner           {}", cfg.owner);
    println!("usdc_mint       {}", cfg.usdc_mint);
    println!("treasury_usdc   {}", cfg.treasury_usdc);
    println!("vault_usdc      {}", cfg.vault_usdc);
    println!("protocol_fee    {} bps", cfg.protocol_fee_bps);
}
