package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.ISettleConcertIxBuilder;
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
 * Builds settle_concert instruction metadata for DevNet wallet signing (USDC fee + vault pool).
 */
@Component
public class SettleConcertIxBuilder implements ISettleConcertIxBuilder {

  private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";
  private static final String TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
  private static final BigDecimal MICRO = new BigDecimal("1000000");

  /**
   * Builds a wallet-ready settle_concert(concert_id, gross, expenses). The program computes
   * net = gross − expenses, the fee from {@code Eh8sConfig.protocol_fee_bps}, and each member
   * share from the BandVault weights; this builder mirrors that math so the UI can show it.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param usdcMint DevNet USDC mint
   * @param venueWallet venue signer pubkey
   * @param ownerWallet treasury owner pubkey
   * @param bandId MariaDB band id (BandVault PDA seed)
   * @param memberWallets band vault members in vault order (remaining accounts)
   * @param weightsBps synced weights in vault order
   * @param concertId MariaDB concert id (settlement PDA seed; settles once)
   * @param grossUsdc venue gross
   * @param expensesUsdc expenses kept by the venue
   * @param protocolFeeBps protocol fee in basis points (same value as on-chain config)
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> build(
      String programId,
      String rpcUrl,
      String usdcMint,
      String venueWallet,
      String ownerWallet,
      long bandId,
      List<String> memberWallets,
      List<Integer> weightsBps,
      long concertId,
      BigDecimal grossUsdc,
      BigDecimal expensesUsdc,
      int protocolFeeBps) {
    long grossAtomic = toAtomic(grossUsdc);
    long expensesAtomic = toAtomic(expensesUsdc);
    long netAtomic = grossAtomic - expensesAtomic;
    long feeAtomic = feeAtomic(netAtomic, protocolFeeBps);
    long poolAtomic = netAtomic - feeAtomic;

    byte[] discriminator = SettleClaimIxBuilder.anchorDiscriminator("settle_concert");
    ByteBuffer data = ByteBuffer.allocate(8 + 8 + 8 + 8).order(ByteOrder.LITTLE_ENDIAN);
    data.put(discriminator);
    data.putLong(concertId);
    data.putLong(grossAtomic);
    data.putLong(expensesAtomic);

    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("venue", venueWallet, true, true));
    accounts.add(
        account(
            "config", null, false, false, List.of("eh8s", "config"), "PDA seeds: [b\"eh8s\", b\"config\"]"));
    accounts.add(
        account(
            "bandVault",
            null,
            false,
            false,
            List.of("band", "u64le:" + bandId),
            "PDA seeds: [b\"band\", band_id.to_le_bytes()]"));
    accounts.add(
        account(
            "concertSettlement",
            null,
            true,
            false,
            List.of("concert", venueWallet, "u64le:" + concertId),
            "PDA seeds: [b\"concert\", venue, concert_id.to_le_bytes()]"));
    accounts.add(
        account("venueUsdc", null, true, false, null, "Venue ATA for usdcMint — FE derives"));
    accounts.add(
        account(
            "treasuryUsdc",
            usdcMint == null ? null : SubscribeAcademyIxBuilder.treasuryUsdc(programId, usdcMint),
            true,
            false,
            null,
            "Treasury PDA [\"treasury\"] USDC ATA (config.treasury_usdc)"));
    accounts.add(
        account(
            "vaultUsdc", null, true, false, null, "Vault authority ATA for usdcMint — FE derives"));
    accounts.add(account("tokenProgram", TOKEN_PROGRAM, false, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));

    List<Long> shares = splitMembers(poolAtomic, weightsBps);
    List<Map<String, Object>> members = new ArrayList<>();
    for (int i = 0; i < memberWallets.size(); i++) {
      accounts.add(
          account(
              "memberProfile" + i,
              null,
              true,
              false,
              List.of("musician", memberWallets.get(i)),
              "Remaining account: MusicianProfile PDA of vault member " + i));
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("wallet", memberWallets.get(i));
      m.put("bps", weightsBps.get(i));
      m.put("pendingUsdc", fromAtomic(shares.get(i)));
      members.add(m);
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("instruction", "settle_concert");
    body.put("programId", programId);
    body.put("rpcUrl", rpcUrl);
    body.put("usdcMint", usdcMint);
    body.put("ownerWalletPubkey", ownerWallet);
    body.put("bandId", bandId);
    body.put("memberWallets", memberWallets);
    body.put("members", members);
    body.put("concertId", concertId);
    body.put("protocolFeeBps", protocolFeeBps);
    body.put("grossUsdc", grossUsdc.setScale(6, RoundingMode.HALF_UP));
    body.put("expensesUsdc", expensesUsdc.setScale(6, RoundingMode.HALF_UP));
    body.put("netUsdc", fromAtomic(netAtomic));
    body.put("eh8sFeeUsdc", fromAtomic(feeAtomic));
    body.put("bandPoolUsdc", fromAtomic(poolAtomic));
    body.put("grossAtomic", grossAtomic);
    body.put("expensesAtomic", expensesAtomic);
    body.put("eh8sFeeAtomic", feeAtomic);
    body.put("bandPoolAtomic", poolAtomic);
    body.put("discriminatorHex", HexFormat.of().formatHex(discriminator));
    body.put("dataHex", HexFormat.of().formatHex(data.array()));
    body.put("accounts", accounts);
    body.put(
        "pdaNote",
        "Derive config [eh8s,config]; band_vault [band,band_id u64 LE]; concert_settlement [concert,venue,concert_id u64 LE]; remaining = musician_profile [musician,member] in vault order; vault [vault].");
    body.put(
        "note",
        "Venue signs a real DevNet settle: expenses stay with the venue, fee to treasury, pool to vault, pending split per member weight. A concert settles once.");
    return body;
  }

  /**
   * Member shares exactly as the program computes them: floor(pool × w / 10000), last takes the
   * remainder.
   *
   * @param poolAtomic pool in 6-decimal units
   * @param weightsBps weights in vault order
   * @return shares in 6-decimal units
   */
  public static List<Long> splitMembers(long poolAtomic, List<Integer> weightsBps) {
    List<Long> out = new ArrayList<>();
    long allocated = 0;
    for (int i = 0; i < weightsBps.size(); i++) {
      if (i == weightsBps.size() - 1) {
        out.add(poolAtomic - allocated);
      } else {
        long share = Math.multiplyExact(poolAtomic, (long) weightsBps.get(i)) / 10_000L;
        allocated += share;
        out.add(share);
      }
    }
    return out;
  }

  /**
   * Fee in atomic USDC exactly as the program computes it: floor(gross × bps / 10000).
   *
   * @param grossAtomic gross in 6-decimal units
   * @param protocolFeeBps fee basis points
   * @return fee in 6-decimal units
   */
  public static long feeAtomic(long grossAtomic, int protocolFeeBps) {
    return Math.multiplyExact(grossAtomic, (long) protocolFeeBps) / 10_000L;
  }

  private static long toAtomic(BigDecimal usdc) {
    return usdc.setScale(6, RoundingMode.HALF_UP).multiply(MICRO).longValueExact();
  }

  private static BigDecimal fromAtomic(long atomic) {
    return BigDecimal.valueOf(atomic).divide(MICRO).setScale(6, RoundingMode.UNNECESSARY);
  }

  private static Map<String, Object> account(
      String name, String pubkey, boolean writable, boolean signer) {
    return account(name, pubkey, writable, signer, null, null);
  }

  private static Map<String, Object> account(
      String name,
      String pubkey,
      boolean writable,
      boolean signer,
      List<String> pdaSeeds,
      String note) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", name);
    row.put("pubkey", pubkey);
    row.put("isWritable", writable);
    row.put("isSigner", signer);
    if (pdaSeeds != null) {
      row.put("pdaSeeds", pdaSeeds);
    }
    if (note != null) {
      row.put("note", note);
    }
    return row;
  }
}
