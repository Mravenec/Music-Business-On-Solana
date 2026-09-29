//! Musician wallet refreshes its own `["musician", wallet]` profile (program v0.6.0). A v0.5
//! 51-byte profile grows in place to the new layout, keeping level and pending claims.
//!
//!   cargo run --example upsert_profile -- <musician.json> <instrument_code> <ISO3> [rpc]

use anchor_lang::{AccountDeserialize, InstructionData, ToAccountMetas};
use solana_client::rpc_client::RpcClient;
use solana_sdk::{
    commitment_config::CommitmentConfig,
    instruction::Instruction,
    pubkey::Pubkey,
    signature::{read_keypair_file, Signer},
    system_program,
    transaction::Transaction,
};

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args.len() < 3 || args[2].len() != 3 {
        eprintln!("usage: upsert_profile <musician.json> <instrument_code> <ISO3> [rpc]");
        std::process::exit(2);
    }
    let musician = read_keypair_file(&args[0]).expect("keypair");
    let instrument_code: u8 = args[1].parse().expect("instrument_code 0..255");
    let mut country = [0u8; 3];
    country.copy_from_slice(args[2].as_bytes());
    let rpc = args
        .get(3)
        .cloned()
        .unwrap_or_else(|| "https://api.devnet.solana.com".to_string());
    let client = RpcClient::new_with_commitment(rpc, CommitmentConfig::confirmed());

    let program_id = eh8s_devnet::ID;
    let profile =
        Pubkey::find_program_address(&[b"musician", musician.pubkey().as_ref()], &program_id).0;
    let before = client.get_account(&profile).ok().map(|a| a.data.len());
    println!("profile         {profile}");
    println!("bytes before    {}", before.map_or("none".to_string(), |n| n.to_string()));

    let ix = Instruction {
        program_id,
        accounts: eh8s_devnet::accounts::UpsertMusicianProfile {
            musician: musician.pubkey(),
            musician_profile: profile,
            system_program: system_program::id(),
        }
        .to_account_metas(None),
        data: eh8s_devnet::instruction::UpsertMusicianProfile { instrument_code, country }.data(),
    };
    let blockhash = client.get_latest_blockhash().expect("blockhash");
    let tx = Transaction::new_signed_with_payer(&[ix], Some(&musician.pubkey()), &[&musician], blockhash);
    let sig = client.send_and_confirm_transaction(&tx).expect("upsert_musician_profile");
    println!("signature       {sig}");
    println!("explorer        https://explorer.solana.com/tx/{sig}?cluster=devnet");

    let acc = client.get_account(&profile).expect("profile");
    let p = eh8s_devnet::MusicianProfile::try_deserialize(&mut acc.data.as_slice()).expect("decode");
    println!("bytes after     {}", acc.data.len());
    println!("authority       {}", p.authority);
    println!("enigma_level    {}", p.enigma_level);
    println!("instrument      {}", p.instrument_code);
    println!("pending_usdc    {}", p.pending_claims_usdc);
    println!("country         {}", String::from_utf8_lossy(&p.country));
}
