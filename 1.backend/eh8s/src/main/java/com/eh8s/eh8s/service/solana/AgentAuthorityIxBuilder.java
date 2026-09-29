package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.IAgentAuthorityIxBuilder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the program v0.6.0 agent instructions: owner-only {@code authorize_agent} and the
 * agent-only {@code update_musician_level} (owner or a {@code 0x01} pedagogical agent).
 */
@Component
public class AgentAuthorityIxBuilder implements IAgentAuthorityIxBuilder {

  private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";

  /** Pedagogical (NEXUS) bit: may change a musician's Enigma level. */
  public static final int PERM_PEDAGOGICAL = 0x01;
  /** HARMONY bit: band vault create / SPP weights. */
  public static final int PERM_HARMONY = 0x02;
  /** STAGE bit: booking confirmation. */
  public static final int PERM_STAGE = 0x04;
  /** VAULT bit: concert settlement. */
  public static final int PERM_VAULT = 0x08;
  /** WAVE bit: royalty deposits. */
  public static final int PERM_WAVE = 0x10;
  /** Every bit the program accepts. */
  public static final int PERM_ALL =
      PERM_PEDAGOGICAL | PERM_HARMONY | PERM_STAGE | PERM_VAULT | PERM_WAVE;
  /** Highest Enigma level the program accepts. */
  public static final int MAX_LEVEL = 5;

  /**
   * Builds {@code authorize_agent(agent_wallet, permissions)}; {@code permissions = 0} revokes.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer, pays the AgentAuthority rent)
   * @param agentWallet agent wallet receiving the bits
   * @param permissions permission bits (subset of {@link #PERM_ALL})
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildAuthorize(
      String programId, String rpcUrl, String ownerWallet, String agentWallet, int permissions) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("authorize_agent");
    ByteBuffer data = ByteBuffer.allocate(8 + 32 + 1);
    data.put(disc);
    data.put(SolanaPda.decode(agentWallet));
    data.put((byte) permissions);

    String agentPda = SolanaPda.walletPda("agent", agentWallet, programId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("owner", ownerWallet, true, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("agentAuthority", agentPda, true, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));
    accounts.add(account("governance", GovernanceIxBuilder.governancePda(programId), false, false));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("instruction", "authorize_agent");
    body.put("programId", programId);
    body.put("rpcUrl", rpcUrl);
    body.put("ownerWalletPubkey", ownerWallet);
    body.put("agentWalletPubkey", agentWallet);
    body.put("permissions", permissions);
    body.put("agentAuthorityPda", agentPda);
    body.put("discriminatorHex", HexFormat.of().formatHex(disc));
    body.put("dataHex", HexFormat.of().formatHex(data.array()));
    body.put("accounts", accounts);
    body.put(
        "note",
        permissions == 0
            ? "Owner signs authorize_agent with 0 bits: the agent loses every on-chain power."
            : "Owner signs authorize_agent: the AgentAuthority PDA stores these permission bits.");
    return body;
  }

  /**
   * Builds {@code update_musician_level(new_level)}. When {@code agentSigner} is true the
   * signer's AgentAuthority PDA is passed; otherwise the optional account is the program id
   * (Anchor {@code None}), which only the config owner may use.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param signerWallet owner or pedagogical agent wallet (signer)
   * @param musicianWallet musician whose {@code ["musician", wallet]} profile changes
   * @param newLevel Enigma level 0..5
   * @param agentSigner whether to pass the signer's AgentAuthority PDA
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildLevel(
      String programId,
      String rpcUrl,
      String signerWallet,
      String musicianWallet,
      int newLevel,
      boolean agentSigner) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("update_musician_level");
    ByteBuffer data = ByteBuffer.allocate(8 + 1);
    data.put(disc);
    data.put((byte) newLevel);

    String musicianPda = SolanaPda.walletPda("musician", musicianWallet, programId);
    String agentAccount =
        agentSigner ? SolanaPda.walletPda("agent", signerWallet, programId) : programId;
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("authority", signerWallet, false, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("musicianProfile", musicianPda, true, false));
    accounts.add(account("agentAuthority", agentAccount, false, false));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("instruction", "update_musician_level");
    body.put("programId", programId);
    body.put("rpcUrl", rpcUrl);
    body.put("signerWalletPubkey", signerWallet);
    body.put("musicianWalletPubkey", musicianWallet);
    body.put("newLevel", newLevel);
    body.put("musicianProfilePda", musicianPda);
    body.put("agentAuthorityAccount", agentAccount);
    body.put("discriminatorHex", HexFormat.of().formatHex(disc));
    body.put("dataHex", HexFormat.of().formatHex(data.array()));
    body.put("accounts", accounts);
    body.put(
        "note",
        agentSigner
            ? "Pedagogical agent signs update_musician_level with its AgentAuthority PDA."
            : "Owner signs update_musician_level (the owner keeps every agent power).");
    return body;
  }

  private static Map<String, Object> account(
      String name, String pubkey, boolean writable, boolean signer) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", name);
    row.put("pubkey", pubkey);
    row.put("isWritable", writable);
    row.put("isSigner", signer);
    return row;
  }
}
