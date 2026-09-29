package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.ISolanaRpcClient;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.DevnetPayMarker;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.repository.interfaces.IDevnetPayRepository;
import com.eh8s.eh8s.service.interfaces.IDevnetPayService;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Records DevNet pay/claim markers in MariaDB. Claim markers require a confirmed signature.
 */
@Service
public class DevnetPayService implements IDevnetPayService {

  private final IDevnetPayRepository devnetPayRepository;
  private final ISolanaRpcClient solanaRpcClient;

  /**
   * Creates the service.
   *
   * @param devnetPayRepository pay marker persistence
   * @param solanaRpcClient DevNet RPC verifier for claim signatures
   */
  public DevnetPayService(
      IDevnetPayRepository devnetPayRepository, ISolanaRpcClient solanaRpcClient) {
    this.devnetPayRepository = devnetPayRepository;
    this.solanaRpcClient = solanaRpcClient;
  }

  /** {@inheritDoc} */
  @Override
  public List<DevnetPayMarker> markers() {
    requireChainConfig();
    return devnetPayRepository.findAll();
  }

  /** {@inheritDoc} */
  @Override
  public DevnetPayMarker recordMarker(DevnetPayMarker marker) {
    requireChainConfig();
    if (marker.getWalletPubkey() == null || marker.getWalletPubkey().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey is required");
    }
    if (marker.getTxSignature() == null || marker.getTxSignature().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "DevNet pay markers require a confirmed txSignature — intended notes are not allowed");
    }
    if (marker.getKind() == null || marker.getKind().isBlank()) {
      marker.setKind("pay");
    }
    marker.setStatus("submitted");
    if (marker.getIntendedInstruction() == null || marker.getIntendedInstruction().isBlank()) {
      marker.setIntendedInstruction("devnet_pay");
    }
    if (marker.getRecordedAt() == null) {
      marker.setRecordedAt(LocalDateTime.now());
    }
    return devnetPayRepository.insert(marker);
  }

  /** {@inheritDoc} */
  @Override
  public PendingClaim recordClaim(Long claimId, String walletPubkey) {
    requireChainConfig();
    requiredString(walletPubkey, "walletPubkey");
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Marking claim requires POST /pending-claims/{id}/claim-royalties/confirm with a DevNet-confirmed txSignature and claimPda (USDC transfer verified)");
  }

  /** {@inheritDoc} */
  @Override
  public AcademySubscription recordPay(Long subscriptionId, String walletPubkey) {
    requireChainConfig();
    requiredString(walletPubkey, "walletPubkey");
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Marking academy paid requires POST /academy-subscriptions/{id}/subscribe-academy/confirm with a DevNet-confirmed txSignature and academySubscriptionPda");
  }

  private ChainConfig requireChainConfig() {
    ChainConfig cfg =
        devnetPayRepository
            .findActiveChainConfig()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No active chain_config"));
    if (cfg.getRpcUrl() == null
        || cfg.getRpcUrl().isBlank()
        || cfg.getProgramIdDevnet() == null
        || cfg.getProgramIdDevnet().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "chain_config missing rpcUrl or programIdDevnet");
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
}
