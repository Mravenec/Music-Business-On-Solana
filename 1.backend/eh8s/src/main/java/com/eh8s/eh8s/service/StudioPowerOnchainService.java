package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.PartnerShare;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.StudioRoleGrant;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.IStudioPowerOnchainRepository;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.interfaces.IRoleApplicationService;
import com.eh8s.eh8s.service.interfaces.IStudioLedgerService;
import com.eh8s.eh8s.service.interfaces.IStudioPowerIxBuilder;
import com.eh8s.eh8s.service.interfaces.IStudioPowerOnchainService;
import com.eh8s.eh8s.service.solana.GovernanceIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Principal-signed studio power on DevNet. Each confirm rebuilds the exact instruction, verifies
 * the signature with {@link ISolanaTxVerifier} (program, data, signer, PDAs) and only then writes
 * the {@code eh8s_onchain} mirrors.
 */
@Service
public class StudioPowerOnchainService implements IStudioPowerOnchainService {

  private final IStudioPowerOnchainRepository repository;
  private final IChainConfigRepository chainConfigRepository;
  private final IStudioPowerIxBuilder ixBuilder;
  private final ISolanaTxVerifier txVerifier;
  private final IStudioLedgerService ledgerService;
  private final IRoleApplicationService roleApplicationService;

  /**
   * Creates the service.
   *
   * @param repository studio role / partner share mirrors
   * @param chainConfigRepository active chain config (program id, RPC, owner wallet)
   * @param ixBuilder v0.11.0 studio power instruction builder
   * @param txVerifier DevNet transaction verifier
   * @param ledgerService partner books, repaired from a confirmed partner share
   * @param roleApplicationService studio admin role cache that follows a confirmed grant / revoke
   */
  public StudioPowerOnchainService(
      IStudioPowerOnchainRepository repository,
      IChainConfigRepository chainConfigRepository,
      IStudioPowerIxBuilder ixBuilder,
      ISolanaTxVerifier txVerifier,
      IStudioLedgerService ledgerService,
      IRoleApplicationService roleApplicationService) {
    this.repository = repository;
    this.chainConfigRepository = chainConfigRepository;
    this.ixBuilder = ixBuilder;
    this.txVerifier = txVerifier;
    this.ledgerService = ledgerService;
    this.roleApplicationService = roleApplicationService;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> console(String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    requireOwner(cfg, walletPubkey);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("programId", cfg.getProgramIdDevnet());
    out.put("ownerWalletPubkey", cfg.getOwnerWalletPubkey());
    out.put("studioRoles", repository.findRoleGrants());
    out.put("partnerShares", repository.findPartnerShares());
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildGrantStudioRole(String walletPubkey, String adminWalletPubkey) {
    ChainConfig cfg = requireChainConfig();
    String owner = requireOwner(cfg, walletPubkey);
    String admin = requireGrantee(cfg, adminWalletPubkey, "adminWalletPubkey");
    return ixBuilder.buildGrantStudioRole(
        cfg.getProgramIdDevnet(), cfg.getRpcUrl(), owner, admin);
  }

  /** {@inheritDoc} */
  @Override
  public StudioRoleGrant confirmGrantStudioRole(
      String walletPubkey, String adminWalletPubkey, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    String owner = requireOwner(cfg, walletPubkey);
    String admin = requireGrantee(cfg, adminWalletPubkey, "adminWalletPubkey");
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected =
        ixBuilder.buildGrantStudioRole(programId, cfg.getRpcUrl(), owner, admin);
    String rolePda = (String) expected.get("studioRolePda");
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "grant_studio_role",
        (String) expected.get("dataHex"),
        owner,
        ISolanaTxVerifier.accounts(
            owner,
            SolanaPda.configPda(programId),
            rolePda,
            SolanaPda.SYSTEM_PROGRAM,
            GovernanceIxBuilder.governancePda(programId)));
    StudioRoleGrant row =
        repository.upsertRoleGranted(admin, rolePda, txSignature, LocalDateTime.now());
    roleApplicationService.ensureStudioAdmin(admin);
    return row;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildRevokeStudioRole(String walletPubkey, String adminWalletPubkey) {
    ChainConfig cfg = requireChainConfig();
    String owner = requireOwner(cfg, walletPubkey);
    String admin = requireWallet(adminWalletPubkey, "adminWalletPubkey");
    return ixBuilder.buildRevokeStudioRole(
        cfg.getProgramIdDevnet(), cfg.getRpcUrl(), owner, admin);
  }

  /** {@inheritDoc} */
  @Override
  public StudioRoleGrant confirmRevokeStudioRole(
      String walletPubkey, String adminWalletPubkey, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    String owner = requireOwner(cfg, walletPubkey);
    String admin = requireWallet(adminWalletPubkey, "adminWalletPubkey");
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected =
        ixBuilder.buildRevokeStudioRole(programId, cfg.getRpcUrl(), owner, admin);
    String rolePda = (String) expected.get("studioRolePda");
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "revoke_studio_role",
        (String) expected.get("dataHex"),
        owner,
        ISolanaTxVerifier.accounts(
            owner,
            SolanaPda.configPda(programId),
            rolePda,
            GovernanceIxBuilder.governancePda(programId)));
    StudioRoleGrant row =
        repository.upsertRoleRevoked(admin, rolePda, txSignature, LocalDateTime.now());
    roleApplicationService.clearStudioAdmin(admin);
    return row;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildSetPartnerShare(
      String walletPubkey,
      String partnerWalletPubkey,
      Integer shareBps,
      Long startsAt,
      Long endsAt) {
    ChainConfig cfg = requireChainConfig();
    String owner = requireOwner(cfg, walletPubkey);
    String partner = requireGrantee(cfg, partnerWalletPubkey, "partnerWalletPubkey");
    return partnerShareIx(cfg, owner, partner, shareBps, startsAt, endsAt);
  }

  /** {@inheritDoc} */
  @Override
  public PartnerShare confirmSetPartnerShare(
      String walletPubkey,
      String partnerWalletPubkey,
      Integer shareBps,
      Long startsAt,
      Long endsAt,
      String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    String owner = requireOwner(cfg, walletPubkey);
    String partner = requireGrantee(cfg, partnerWalletPubkey, "partnerWalletPubkey");
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected = partnerShareIx(cfg, owner, partner, shareBps, startsAt, endsAt);
    String sharePda = (String) expected.get("partnerSharePda");
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "set_partner_share",
        (String) expected.get("dataHex"),
        owner,
        ISolanaTxVerifier.accounts(
            owner,
            SolanaPda.configPda(programId),
            sharePda,
            (String) expected.get("partnerBookPda"),
            SolanaPda.SYSTEM_PROGRAM,
            GovernanceIxBuilder.governancePda(programId)));
    LocalDateTime now = LocalDateTime.now();
    int bps = shareBps;
    PartnerShare mirror;
    if (bps == 0) {
      mirror =
          repository
              .endPartnerShare(partner, now, txSignature)
              .orElseGet(() -> new PartnerShare(null, partner, 0, now, now, txSignature, sharePda));
    } else {
      mirror =
          repository.upsertPartnerShare(
              partner,
              bps,
              fromUnix(startsAt == null ? 0L : startsAt),
              endsAt == null || endsAt == 0 ? null : fromUnix(endsAt),
              sharePda,
              txSignature);
    }
    ledgerService.syncPartnerFromChain(mirror);
    return mirror;
  }

  private Map<String, Object> partnerShareIx(
      ChainConfig cfg,
      String owner,
      String partner,
      Integer shareBps,
      Long startsAt,
      Long endsAt) {
    if (shareBps == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shareBps is required");
    }
    try {
      return ixBuilder.buildSetPartnerShare(
          cfg.getProgramIdDevnet(),
          cfg.getRpcUrl(),
          owner,
          partner,
          shareBps,
          startsAt == null ? 0L : startsAt,
          endsAt == null ? 0L : endsAt);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  /**
   * Converts a program unix-seconds timestamp to the server-local time the books compare against.
   *
   * @param unixSeconds seconds since the epoch
   * @return local date-time
   */
  static LocalDateTime fromUnix(long unixSeconds) {
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(unixSeconds), ZoneId.systemDefault());
  }

  private String requireFreshSignature(String rawSignature) {
    String txSignature = requiredString(rawSignature, "txSignature");
    if (repository.isRecorded(txSignature)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Signature already recorded");
    }
    return txSignature;
  }

  private ChainConfig requireChainConfig() {
    ChainConfig cfg =
        chainConfigRepository
            .findActive()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No active chain_config"));
    if (blank(cfg.getOwnerWalletPubkey()) || blank(cfg.getProgramIdDevnet())) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "chain_config needs owner wallet and program id");
    }
    return cfg;
  }

  private static String requireOwner(ChainConfig cfg, String walletPubkey) {
    String wallet = requireWallet(walletPubkey, "walletPubkey");
    if (!cfg.getOwnerWalletPubkey().equals(wallet)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the principal wallet can sign studio power");
    }
    return wallet;
  }

  private static String requireGrantee(ChainConfig cfg, String walletPubkey, String key) {
    String wallet = requireWallet(walletPubkey, key);
    if (cfg.getOwnerWalletPubkey().equals(wallet)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "The principal wallet is not a studio grant");
    }
    return wallet;
  }

  private static String requireWallet(String value, String key) {
    String wallet = requiredString(value, key);
    byte[] raw;
    try {
      raw = SolanaPda.decode(wallet);
    } catch (RuntimeException ex) {
      raw = null;
    }
    if (raw == null || raw.length != 32 || Arrays.equals(raw, new byte[32])) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be a base58 wallet");
    }
    return wallet;
  }

  private static String requiredString(String value, String key) {
    String trimmed = value == null ? null : value.trim();
    if (trimmed == null || trimmed.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    return trimmed;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
