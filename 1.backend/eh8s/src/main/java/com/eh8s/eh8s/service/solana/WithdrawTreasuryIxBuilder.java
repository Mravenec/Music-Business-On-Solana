package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.IWithdrawTreasuryIxBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds withdraw_treasury(amount) metadata for the config owner wallet. The treasury PDA
 * ({@code ["treasury"]}) signs inside the program; the owner only signs as {@code config.owner}.
 */
@Component
public class WithdrawTreasuryIxBuilder implements IWithdrawTreasuryIxBuilder {

  /**
   * Builds withdraw_treasury: treasury PDA ATA to the owner's USDC ATA.
   *
   * @param programId DevNet program id
   * @param usdcMint configured USDC mint
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer)
   * @param amountUsdc amount in USDC (6-decimal atomic on-chain)
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> build(
      String programId, String usdcMint, String rpcUrl, String ownerWallet, BigDecimal amountUsdc) {
    long atomic = toAtomic(amountUsdc);
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("withdraw_treasury");
    ByteBuffer data = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    data.putLong(atomic);

    String treasuryPda = SolanaPda.treasuryPda(programId);
    String treasuryUsdc = SolanaPda.associatedTokenAddress(treasuryPda, usdcMint);
    String ownerUsdc = SolanaPda.associatedTokenAddress(ownerWallet, usdcMint);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("owner", ownerWallet, false, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("treasuryAuthority", treasuryPda, false, false));
    accounts.add(account("treasuryUsdc", treasuryUsdc, true, false));
    accounts.add(account("ownerUsdc", ownerUsdc, true, false));
    accounts.add(account("tokenProgram", SolanaPda.TOKEN_PROGRAM, false, false));
    accounts.add(account("governance", GovernanceIxBuilder.governancePda(programId), false, false));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("instruction", "withdraw_treasury");
    body.put("programId", programId);
    body.put("rpcUrl", rpcUrl);
    body.put("usdcMint", usdcMint);
    body.put("ownerWalletPubkey", ownerWallet);
    body.put("amountUsdc", amountUsdc);
    body.put("amountAtomic", atomic);
    body.put("treasuryPda", treasuryPda);
    body.put("treasuryUsdc", treasuryUsdc);
    body.put("ownerUsdc", ownerUsdc);
    body.put("discriminatorHex", HexFormat.of().formatHex(disc));
    body.put("dataHex", HexFormat.of().formatHex(data.array()));
    body.put("accounts", accounts);
    body.put("note", "Owner signs withdraw_treasury: the treasury PDA pays the owner's USDC ATA.");
    return body;
  }

  /**
   * USDC to 6-decimal atomic units (truncates sub-micro dust).
   *
   * @param amountUsdc amount in USDC
   * @return atomic units
   */
  public static long toAtomic(BigDecimal amountUsdc) {
    return amountUsdc.movePointRight(6).setScale(0, RoundingMode.DOWN).longValueExact();
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
