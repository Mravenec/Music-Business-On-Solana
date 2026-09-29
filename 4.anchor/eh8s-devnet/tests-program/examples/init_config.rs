//! One-time DevNet setup after deploy: owner treasury USDC ATA + initialize_config.
//! Idempotent — prints the existing config when it is already initialized.
//!
//!   cargo run --example init_config -- <upgrade-authority.json> <owner> <usdc-mint> [fee-bps] [rpc]

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
use std::str::FromStr;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args.len() < 3 {
        eprintln!("usage: init_config <keypair.json> <owner> <usdc-mint> [fee-bps] [rpc]");
        std::process::exit(2);
    }
    let authority = read_keypair_file(&args[0]).expect("keypair");
    let owner = Pubkey::from_str(&args[1]).expect("owner pubkey");
    let mint = Pubkey::from_str(&args[2]).expect("mint pubkey");
    let fee_bps: u16 = args.get(3).map(|v| v.parse().unwrap()).unwrap_or(1500);
    let rpc = args
        .get(4)
        .cloned()
        .unwrap_or_else(|| "https://api.devnet.solana.com".to_string());
    let client = RpcClient::new_with_commitment(rpc, CommitmentConfig::confirmed());

    let program_id = eh8s_devnet::ID;
    let config = Pubkey::find_program_address(&[b"eh8s", b"config"], &program_id).0;
    let vault_authority = Pubkey::find_program_address(&[b"vault"], &program_id).0;
    let vault_usdc = get_associated_token_address(&vault_authority, &mint);
    let treasury_usdc = get_associated_token_address(&owner, &mint);
    let program_data =
        Pubkey::find_program_address(&[program_id.as_ref()], &bpf_loader_upgradeable::id()).0;

    if let Ok(acc) = client.get_account(&config) {
        print_config(&config, &acc.data, None);
        return;
    }

    let create_treasury = spl_associated_token_account::instruction::create_associated_token_account_idempotent(
        &authority.pubkey(),
        &owner,
        &mint,
        &spl_token::id(),
    );
    let initialize = Instruction {
        program_id,
        accounts: eh8s_devnet::accounts::InitializeConfig {
            authority: authority.pubkey(),
            config,
            usdc_mint: mint,
            vault_authority,
            vault_usdc,
            program: program_id,
            program_data,
            token_program: spl_token::id(),
            associated_token_program: spl_associated_token_account::id(),
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::InitializeConfig {
            owner,
            protocol_fee_bps: fee_bps,
        }
        .data(),
    };
    let blockhash = client.get_latest_blockhash().expect("blockhash");
    let tx = Transaction::new_signed_with_payer(
        &[create_treasury, initialize],
        Some(&authority.pubkey()),
        &[&authority],
        blockhash,
    );
    let sig = client
        .send_and_confirm_transaction(&tx)
        .expect("initialize_config");
    let acc = client.get_account(&config).expect("config after init");
    print_config(&config, &acc.data, Some(sig.to_string()));
    assert_eq!(
        eh8s_devnet::Eh8sConfig::try_deserialize(&mut acc.data.as_slice())
            .unwrap()
            .treasury_usdc,
        treasury_usdc
    );
}

fn print_config(address: &Pubkey, data: &[u8], signature: Option<String>) {
    let cfg = eh8s_devnet::Eh8sConfig::try_deserialize(&mut &data[..]).expect("decode config");
    println!("config          {address}");
    if let Some(sig) = signature {
        println!("signature       {sig}");
        println!("explorer        https://explorer.solana.com/tx/{sig}?cluster=devnet");
    } else {
        println!("status          already initialized");
    }
    println!("owner           {}", cfg.owner);
    println!("usdc_mint       {}", cfg.usdc_mint);
    println!("treasury_usdc   {}", cfg.treasury_usdc);
    println!("vault_usdc      {}", cfg.vault_usdc);
    println!("protocol_fee    {} bps", cfg.protocol_fee_bps);
}
