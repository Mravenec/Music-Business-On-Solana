package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IChainReconcileRepository;
import com.eh8s.eh8s.service.interfaces.IChainReconcileService;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Scans confirmed money mirrors vs RPC. Demotes orphans to {@code orphaned}; never invents USDC or
 * touches live confirm write paths.
 */
@Service
public class ChainReconcileService implements IChainReconcileService {

  private final IChainReconcileRepository reconcileRepository;
  private final ISolanaTxVerifier txVerifier;

  /**
   * Creates the service.
   *
   * @param reconcileRepository confirmed-row reads and demotions
   * @param txVerifier SolanaTxVerifier-style presence / success checks
   */
  public ChainReconcileService(
      IChainReconcileRepository reconcileRepository, ISolanaTxVerifier txVerifier) {
    this.reconcileRepository = reconcileRepository;
    this.txVerifier = txVerifier;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> reconcile(String walletPubkey) {
    ChainConfig cfg = requireConfig();
    requireOwner(cfg, walletPubkey);
    return scan(cfg);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> reconcileScheduled() {
    ChainConfig cfg = reconcileRepository.findActiveChainConfig().orElse(null);
    if (cfg == null || blank(cfg.getRpcUrl())) {
      return Map.of("scanned", 0, "ok", 0, "flagged", List.of(), "rpcErrors", List.of());
    }
    return scan(cfg);
  }

  private Map<String, Object> scan(ChainConfig cfg) {
    String rpcUrl = cfg.getRpcUrl().trim();
    List<Map<String, Object>> flagged = new ArrayList<>();
    List<Map<String, Object>> rpcErrors = new ArrayList<>();
    int scanned = 0;
    int ok = 0;

    for (ConcertSettlement row : reconcileRepository.listConfirmedSettlements()) {
      scanned++;
      if (check(
          rpcUrl,
          "concert_settlement",
          row.getId(),
          row.getSettleTxSignature(),
          reconcileRepository::demoteSettlement,
          flagged,
          rpcErrors)) {
        ok++;
      }
    }
    for (PendingClaim row : reconcileRepository.listConfirmedClaims()) {
      scanned++;
      if (check(
          rpcUrl,
          "pending_claim",
          row.getId(),
          row.getTxSignature(),
          reconcileRepository::demoteClaim,
          flagged,
          rpcErrors)) {
        ok++;
      }
    }
    for (AcademySubscription row : reconcileRepository.listConfirmedAcademySubscriptions()) {
      scanned++;
      if (check(
          rpcUrl,
          "academy_subscription",
          row.getId(),
          row.getPayTxSignature(),
          reconcileRepository::demoteAcademySubscription,
          flagged,
          rpcErrors)) {
        ok++;
      }
    }
    for (GeographicSubscription row : reconcileRepository.listConfirmedGeoSubscriptions()) {
      scanned++;
      if (check(
          rpcUrl,
          "geographic_subscription",
          row.getId(),
          row.getPayTxSignature(),
          reconcileRepository::demoteGeoSubscription,
          flagged,
          rpcErrors)) {
        ok++;
      }
    }
    for (RoyaltyDeposit row : reconcileRepository.listConfirmedRoyaltyDeposits()) {
      scanned++;
      if (check(
          rpcUrl,
          "royalty_deposit",
          row.getId(),
          row.getDepositTxSignature(),
          reconcileRepository::demoteRoyaltyDeposit,
          flagged,
          rpcErrors)) {
        ok++;
      }
    }
    for (SyncLicenseDeal row : reconcileRepository.listPaidSyncLicenseDeals()) {
      scanned++;
      if (check(
          rpcUrl,
          "sync_license_deal",
          row.getId(),
          row.getPayTxSignature(),
          reconcileRepository::demoteSyncLicenseDeal,
          flagged,
          rpcErrors)) {
        ok++;
      }
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("scanned", scanned);
    out.put("ok", ok);
    out.put("flagged", flagged);
    out.put("rpcErrors", rpcErrors);
    out.put("inventedUsdc", false);
    return out;
  }

  /**
   * @return true when the row remains trusted (signature OK on chain)
   */
  private boolean check(
      String rpcUrl,
      String kind,
      Long id,
      String txSignature,
      LongFunction<Boolean> demote,
      List<Map<String, Object>> flagged,
      List<Map<String, Object>> rpcErrors) {
    if (blank(txSignature)) {
      demoteAndRecord(kind, id, txSignature, "missing signature", demote, flagged);
      return false;
    }
    try {
      txVerifier.requireSuccessfulTransaction(rpcUrl, txSignature);
      return true;
    } catch (ResponseStatusException ex) {
      if (ex.getStatusCode() == HttpStatus.BAD_GATEWAY) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("kind", kind);
        err.put("id", id);
        err.put("txSignature", txSignature);
        err.put("reason", ex.getReason());
        rpcErrors.add(err);
        return false;
      }
      demoteAndRecord(
          kind,
          id,
          txSignature,
          ex.getReason() == null ? "RPC signature check failed" : ex.getReason(),
          demote,
          flagged);
      return false;
    }
  }

  private static void demoteAndRecord(
      String kind,
      Long id,
      String txSignature,
      String reason,
      LongFunction<Boolean> demote,
      List<Map<String, Object>> flagged) {
    boolean updated = Boolean.TRUE.equals(demote.apply(id));
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("kind", kind);
    row.put("id", id);
    row.put("txSignature", txSignature);
    row.put("reason", reason);
    row.put("demoted", updated);
    flagged.add(row);
  }

  private ChainConfig requireConfig() {
    return reconcileRepository
        .findActiveChainConfig()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "No active chain config"));
  }

  private static void requireOwner(ChainConfig cfg, String walletPubkey) {
    if (blank(walletPubkey)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey is required");
    }
    String owner = cfg.getOwnerWalletPubkey();
    if (owner == null || !owner.equals(walletPubkey.trim())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the owner wallet can run chain reconcile");
    }
    if (blank(cfg.getRpcUrl())) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "chain_config missing rpcUrl");
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
