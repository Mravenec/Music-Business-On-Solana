package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.ISubscribeAcademyIxBuilder;
import com.eh8s.eh8s.service.interfaces.ISolanaRpcClient;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IAcademyOnchainRepository;
import com.eh8s.eh8s.service.interfaces.IAcademyOnchainService;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import com.eh8s.eh8s.service.solana.SubscribeAcademyIxBuilder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * DevNet subscribe_academy(plan_type, months) build + RPC confirm + on-chain expiry read. Never
 * marks paid without a confirmed signature.
 */
@Service
public class AcademyOnchainService implements IAcademyOnchainService {

  private final IAcademyOnchainRepository academyOnchainRepository;
  private final ISubscribeAcademyIxBuilder ixBuilder;
  private final ISolanaTxVerifier txVerifier;
  private final ISolanaRpcClient rpc;

  /**
   * Creates the service.
   *
   * @param academyOnchainRepository subscription persistence
   * @param ixBuilder instruction metadata builder
   * @param txVerifier DevNet transaction verifier
   * @param rpc DevNet RPC (AcademySubscription account reads)
   */
  public AcademyOnchainService(
      IAcademyOnchainRepository academyOnchainRepository,
      ISubscribeAcademyIxBuilder ixBuilder,
      ISolanaTxVerifier txVerifier,
      ISolanaRpcClient rpc) {
    this.academyOnchainRepository = academyOnchainRepository;
    this.ixBuilder = ixBuilder;
    this.txVerifier = txVerifier;
    this.rpc = rpc;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildSubscribeAcademy(
      Long subscriptionId, String walletPubkey, String instructorUsdcAta, String payerUsdcAta) {
    AcademySubscription sub = requireSubscription(subscriptionId);
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    Priced priced = price(sub);
    Map<String, Object> ix =
        ixBuilder.build(
            cfg.getProgramIdDevnet(),
            cfg.getUsdcMint(),
            cfg.getRpcUrl(),
            wallet,
            priced.planType(),
            priced.months(),
            priced.amount(),
            optionalString(instructorUsdcAta),
            optionalString(payerUsdcAta));
    ix.put("subscriptionId", subscriptionId);
    ix.put("ownerWalletPubkey", cfg.getOwnerWalletPubkey());
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public AcademySubscription confirmSubscribeAcademy(
      Long subscriptionId, String walletPubkey, String rawTxSignature, String academySubscriptionPda) {
    AcademySubscription sub = requireSubscription(subscriptionId);
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    String txSignature = requiredString(rawTxSignature, "rawTxSignature");
    String pda = requiredString(academySubscriptionPda, "academySubscriptionPda");
    String programId = cfg.getProgramIdDevnet();
    String derived = SubscribeAcademyIxBuilder.academyPda(wallet, programId);
    if (!derived.equals(pda)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "academySubscriptionPda does not belong to this payer wallet");
    }
    Priced priced = price(sub);
    Map<String, Object> expected =
        ixBuilder.build(
            programId,
            cfg.getUsdcMint(),
            cfg.getRpcUrl(),
            wallet,
            priced.planType(),
            priced.months(),
            priced.amount(),
            null,
            null);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "subscribe_academy",
        (String) expected.get("dataHex"),
        wallet,
        ISolanaTxVerifier.accounts(null, SolanaPda.configPda(programId), derived));
    return academyOnchainRepository.markConfirmed(
        subscriptionId, wallet, txSignature, pda, readExpiry(cfg.getRpcUrl(), derived));
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> onchainStatus(Long subscriptionId) {
    AcademySubscription sub = requireSubscription(subscriptionId);
    ChainConfig cfg = requireChainConfig();
    if (blank(sub.getPayerWalletPubkey())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Subscription is not confirmed on DevNet yet");
    }
    String pda =
        SubscribeAcademyIxBuilder.academyPda(sub.getPayerWalletPubkey(), cfg.getProgramIdDevnet());
    byte[] data = rpc.getAccountData(cfg.getRpcUrl(), pda);
    LocalDateTime expiresAt = SubscribeAcademyIxBuilder.decodeExpiresAt(data);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("subscriptionId", subscriptionId);
    out.put("academySubscriptionPda", pda);
    out.put("exists", data != null);
    out.put("expiresAt", expiresAt);
    out.put("active", expiresAt != null && expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC)));
    return out;
  }

  private Priced price(AcademySubscription sub) {
    AcademyPlan plan =
        academyOnchainRepository
            .findPlan(sub.getAcademyPlanId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Unknown plan"));
    if (plan.getOnchainPlanType() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Plan has no on-chain plan code");
    }
    int months = sub.getMonths() == null ? 1 : sub.getMonths();
    if (months < 1 || months > SubscribeAcademyIxBuilder.MAX_MONTHS) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription months must be 1..12");
    }
    BigDecimal amount = plan.getUsdcMonthly().multiply(BigDecimal.valueOf(months));
    BigDecimal stored = storedAmount(sub);
    if (stored.compareTo(BigDecimal.ZERO) > 0 && stored.compareTo(amount) != 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Stored amount does not match plan price x months");
    }
    return new Priced(plan.getOnchainPlanType(), months, amount);
  }

  private LocalDateTime readExpiry(String rpcUrl, String pda) {
    try {
      return SubscribeAcademyIxBuilder.decodeExpiresAt(rpc.getAccountData(rpcUrl, pda));
    } catch (ResponseStatusException ex) {
      return null;
    }
  }

  private static BigDecimal storedAmount(AcademySubscription sub) {
    return nullToZero(sub.getTreasuryUsdc()).add(nullToZero(sub.getInstructorUsdc()));
  }

  private AcademySubscription requireSubscription(Long subscriptionId) {
    return academyOnchainRepository
        .findSubscription(subscriptionId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown subscription"));
  }

  private ChainConfig requireChainConfig() {
    ChainConfig cfg =
        academyOnchainRepository
            .findActiveChainConfig()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No active chain_config"));
    if (blank(cfg.getRpcUrl()) || blank(cfg.getProgramIdDevnet()) || blank(cfg.getUsdcMint())) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "chain_config missing rpc/program/usdc mint");
    }
    return cfg;
  }

  private static String requiredString(String value, String key) {
    String trimmed = value == null ? null : value.trim();
    if (trimmed == null || trimmed.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    return trimmed;
  }

  private static String optionalString(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static BigDecimal nullToZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private record Priced(int planType, int months, BigDecimal amount) {}
}
