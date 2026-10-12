package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.IEnrollmentRepository;
import com.eh8s.eh8s.repository.interfaces.IRoleApplicationRepository;
import com.eh8s.eh8s.repository.interfaces.IStudioLedgerRepository;
import com.eh8s.eh8s.repository.interfaces.IStudioPowerOnchainRepository;
import com.eh8s.eh8s.service.interfaces.IEnrollmentService;
import com.eh8s.eh8s.service.interfaces.IJwtService;
import com.eh8s.eh8s.service.interfaces.ISolanaRpcClient;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import com.eh8s.eh8s.service.solana.SettleClaimIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Off-chain academy enrollment and wallet session identity.
 */
@Service
public class EnrollmentService implements IEnrollmentService {

  private static final BigDecimal TREASURY_SHARE = new BigDecimal("0.85");
  private static final BigDecimal INSTRUCTOR_SHARE = new BigDecimal("0.15");
  private static final int MAX_MONTHS = 12;
  private static final String ROLE_OWNER = "owner";
  private static final String ROLE_MUSICIAN = "musician";
  private static final long NONCE_TTL_SECONDS = 300;

  private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

  private final IEnrollmentRepository enrollmentRepository;
  private final IChainConfigRepository chainConfigRepository;
  private final IJwtService jwtService;
  private final IRoleApplicationRepository roleApplicationRepository;
  private final IStudioLedgerRepository studioLedgerRepository;
  private final IStudioPowerOnchainRepository studioPowerOnchainRepository;
  private final ISolanaTxVerifier txVerifier;
  private final ISolanaRpcClient solanaRpcClient;

  /**
   * Creates the service.
   *
   * @param enrollmentRepository enrollment persistence
   * @param chainConfigRepository active chain_config (owner wallet + fee)
   * @param jwtService issues Bearer tokens after wallet upsert
   * @param roleApplicationRepository granted studio admin and partner roles
   * @param studioLedgerRepository active partner rows
   * @param studioPowerOnchainRepository confirmed on-chain studio role mirror (wins over the cache)
   * @param txVerifier DevNet upsert_musician_profile verifier
   * @param solanaRpcClient DevNet account reads for PDA recover
   */
  public EnrollmentService(
      IEnrollmentRepository enrollmentRepository,
      IChainConfigRepository chainConfigRepository,
      IJwtService jwtService,
      IRoleApplicationRepository roleApplicationRepository,
      IStudioLedgerRepository studioLedgerRepository,
      IStudioPowerOnchainRepository studioPowerOnchainRepository,
      ISolanaTxVerifier txVerifier,
      ISolanaRpcClient solanaRpcClient) {
    this.enrollmentRepository = enrollmentRepository;
    this.chainConfigRepository = chainConfigRepository;
    this.jwtService = jwtService;
    this.roleApplicationRepository = roleApplicationRepository;
    this.studioLedgerRepository = studioLedgerRepository;
    this.studioPowerOnchainRepository = studioPowerOnchainRepository;
    this.txVerifier = txVerifier;
    this.solanaRpcClient = solanaRpcClient;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Account createAccount(Account account) {
    if (account.getRole() == null || account.getRole().isBlank()) {
      account.setRole(ROLE_MUSICIAN);
    }
    if (ROLE_OWNER.equalsIgnoreCase(account.getRole())
        && !isPlatformOwnerWallet(account.getWalletPubkey())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot self-assign owner role");
    }
    if (account.getEmail() != null
        && enrollmentRepository.findAccountByEmail(account.getEmail()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
    }
    return enrollmentRepository.insertAccount(account);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MusicianProfile createMusician(MusicianProfile profile) {
    if (profile.getEnigmaScore() == null) {
      profile.setEnigmaScore((byte) 0);
    }
    String country = profile.getCountryCode() == null ? null : profile.getCountryCode().trim();
    if (country != null && !country.matches("[A-Z]{3}")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "countryCode must be three uppercase letters (ISO-3166 alpha-3)");
    }
    profile.setCountryCode(country);
    // PDA only after confirm/recover — never trust a client-supplied value here.
    profile.setMusicianProfilePda(null);
    profile.setEnigmaLevelId(
        enrollmentRepository
            .findEnigmaLevelId(0)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Enigma level 0 not seeded")));
    return enrollmentRepository.insertMusician(profile);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MusicianProfile confirmMusicianProfile(
      Long profileId, String walletPubkey, String txSignature, String musicianProfilePda) {
    MusicianProfile profile = requireOwnedProfile(profileId, walletPubkey);
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    String tx = requiredString(txSignature, "txSignature");
    String pda = requiredString(musicianProfilePda, "musicianProfilePda");
    String derived = SolanaPda.walletPda("musician", wallet, cfg.getProgramIdDevnet());
    if (!derived.equals(pda)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "musicianProfilePda does not match this wallet");
    }
    if (profile.getMusicianProfilePda() != null
        && !profile.getMusicianProfilePda().isBlank()
        && profile.getMusicianProfilePda().equals(derived)) {
      return profile;
    }
    String country = requireCountry(profile);
    int instrumentCode = requireInstrumentCode(profile);
    String dataHex = upsertMusicianProfileDataHex(instrumentCode, country);
    txVerifier.verify(
        cfg.getRpcUrl(),
        cfg.getProgramIdDevnet(),
        tx,
        "upsert_musician_profile",
        dataHex,
        wallet,
        ISolanaTxVerifier.accounts(wallet, derived));
    return enrollmentRepository.updateMusicianProfilePda(profileId, derived);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MusicianProfile recoverMusicianProfile(Long profileId, String walletPubkey) {
    MusicianProfile profile = requireOwnedProfile(profileId, walletPubkey);
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    String derived = SolanaPda.walletPda("musician", wallet, cfg.getProgramIdDevnet());
    if (profile.getMusicianProfilePda() != null
        && !profile.getMusicianProfilePda().isBlank()
        && profile.getMusicianProfilePda().equals(derived)) {
      return profile;
    }
    byte[] data = solanaRpcClient.getAccountData(cfg.getRpcUrl(), derived);
    if (data == null || data.length == 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "MusicianProfile PDA not found on DevNet — sign upsert_musician_profile first");
    }
    return enrollmentRepository.updateMusicianProfilePda(profileId, derived);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<MusicianProfile> musicians() {
    return enrollmentRepository.findMusicians();
  }

  /** Anchor data hex for upsert_musician_profile (discriminator + instrument u8 + country[3]). */
  static String upsertMusicianProfileDataHex(int instrumentCode, String countryAlpha3) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("upsert_musician_profile");
    byte[] all = new byte[disc.length + 4];
    System.arraycopy(disc, 0, all, 0, disc.length);
    all[disc.length] = (byte) (instrumentCode & 0xff);
    all[disc.length + 1] = (byte) countryAlpha3.charAt(0);
    all[disc.length + 2] = (byte) countryAlpha3.charAt(1);
    all[disc.length + 3] = (byte) countryAlpha3.charAt(2);
    return HexFormat.of().formatHex(all);
  }

  private MusicianProfile requireOwnedProfile(Long profileId, String walletPubkey) {
    if (profileId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profileId is required");
    }
    MusicianProfile profile =
        enrollmentRepository
            .findMusician(profileId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown musician profile"));
    Account account =
        enrollmentRepository
            .findAccount(profile.getAccountId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown account"));
    String wallet = requiredString(walletPubkey, "walletPubkey");
    if (account.getWalletPubkey() == null || !wallet.equals(account.getWalletPubkey())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Wallet does not own this musician profile");
    }
    return profile;
  }

  private ChainConfig requireChainConfig() {
    return chainConfigRepository
        .findActive()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "No active chain_config"));
  }

  private static String requireCountry(MusicianProfile profile) {
    String country = profile.getCountryCode() == null ? null : profile.getCountryCode().trim();
    if (country == null || !country.matches("[A-Z]{3}")) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Profile countryCode must be set before on-chain confirm");
    }
    return country;
  }

  private static int requireInstrumentCode(MusicianProfile profile) {
    if (profile.getInstrumentId() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Profile instrumentId is required");
    }
    long id = profile.getInstrumentId();
    if (id < 0 || id > 255) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "instrumentId must fit an on-chain u8 instrument_code");
    }
    return (int) id;
  }

  private static String requiredString(String value, String key) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    return value.trim();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AcademySubscription subscribe(AcademySubscription subscription) {
    AcademyPlan plan =
        enrollmentRepository
            .findPlan(subscription.getAcademyPlanId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown plan"));
    int months = subscription.getMonths() == null ? 1 : subscription.getMonths();
    if (months < 1 || months > MAX_MONTHS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "months must be 1..12");
    }
    subscription.setMonths((byte) months);
    BigDecimal total = plan.getUsdcMonthly().multiply(BigDecimal.valueOf(months));
    subscription.setTreasuryUsdc(total.multiply(TREASURY_SHARE).setScale(2, RoundingMode.HALF_UP));
    subscription.setInstructorUsdc(
        total.multiply(INSTRUCTOR_SHARE).setScale(2, RoundingMode.HALF_UP));
    if (subscription.getStartsAt() == null) {
      subscription.setStartsAt(LocalDate.now());
    }
    if (subscription.getExpiresAt() == null) {
      subscription.setExpiresAt(subscription.getStartsAt().plusMonths(months));
    }
    if (subscription.getIntendedInstruction() == null
        || subscription.getIntendedInstruction().isBlank()) {
      subscription.setIntendedInstruction("subscribe_academy");
    }
    return enrollmentRepository.insertSubscription(subscription);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<AcademySubscription> subscriptions() {
    return enrollmentRepository.findSubscriptions();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EnigmaEvaluation evaluate(EnigmaEvaluation evaluation) {
    if (evaluation.getWeekStart() == null) {
      evaluation.setWeekStart(LocalDate.now());
    }
    return enrollmentRepository.insertEvaluation(evaluation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EnigmaEvaluation> evaluations() {
    return enrollmentRepository.findEvaluations();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> challenge(String walletPubkey) {
    if (walletPubkey == null || walletPubkey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey required");
    }
    String wallet = walletPubkey.trim();
    String nonce = UUID.randomUUID().toString();
    challenges.put(wallet, new Challenge(nonce, Instant.now().plusSeconds(NONCE_TTL_SECONDS)));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("walletPubkey", wallet);
    body.put("nonce", nonce);
    body.put("message", new String(WalletMessageVerifier.message(wallet, nonce), StandardCharsets.UTF_8));
    return body;
  }

  @Override
  public Map<String, Object> upsertWalletSession(Account request, String signature) {
    if (request == null || request.getWalletPubkey() == null || request.getWalletPubkey().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey required");
    }
    String wallet = request.getWalletPubkey().trim();
    requireWalletSignature(wallet, signature);
    boolean ownerWallet = isPlatformOwnerWallet(wallet);
    if (!ownerWallet
        && request.getRole() != null
        && ROLE_OWNER.equalsIgnoreCase(request.getRole().trim())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot self-assign owner role");
    }

    Optional<Account> existing = enrollmentRepository.findAccountByWallet(wallet);
    Account account;
    if (existing.isPresent()) {
      account = enrollmentRepository.touchAccount(existing.get().getId());
      if (ownerWallet && !ROLE_OWNER.equalsIgnoreCase(account.getRole())) {
        account = enrollmentRepository.updateRole(account.getId(), ROLE_OWNER);
      }
    } else {
      Account created = new Account();
      created.setWalletPubkey(wallet);
      created.setDisplayName(
          request.getDisplayName() != null && !request.getDisplayName().isBlank()
              ? request.getDisplayName()
              : ownerWallet
                  ? "EH8S Platform Owner"
                  : "Wallet " + wallet.substring(0, Math.min(8, wallet.length())));
      created.setEmail(
          request.getEmail() != null && !request.getEmail().isBlank()
              ? request.getEmail()
              : wallet.toLowerCase() + "@wallet.eh8s.local");
      created.setRole(ownerWallet ? ROLE_OWNER : resolveNonOwnerRole(request.getRole()));
      created.setCountryCode(request.getCountryCode());
      created.setLastSeenAt(LocalDateTime.now());
      account = enrollmentRepository.insertAccount(created);
    }
    return toSession(account);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> currentSession(JwtPrincipal principal) {
    if (principal == null || principal.accountId() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again");
    }
    Account touched = enrollmentRepository.touchAccount(principal.accountId());
    if (touched == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again");
    }
    return toSession(touched);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> findWalletSession(String walletPubkey) {
    Account account =
        enrollmentRepository
            .findAccountByWallet(walletPubkey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account"));
    assertCallerOwns(account);
    Account touched = enrollmentRepository.touchAccount(account.getId());
    if (isPlatformOwnerWallet(walletPubkey) && !ROLE_OWNER.equalsIgnoreCase(touched.getRole())) {
      touched = enrollmentRepository.updateRole(touched.getId(), ROLE_OWNER);
    }
    return toSession(touched);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> updateWalletLocation(String walletPubkey, Account location) {
    if (location == null || location.getLastLat() == null || location.getLastLng() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lastLat and lastLng required");
    }
    Account account =
        enrollmentRepository
            .findAccountByWallet(walletPubkey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account"));
    assertCallerOwns(account);
    Account updated =
        enrollmentRepository.updateLocation(
            account.getId(), location.getLastLat(), location.getLastLng());
    return toSession(updated);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Account> accounts() {
    return enrollmentRepository.findAccounts();
  }

  private String resolveNonOwnerRole(String requested) {
    if (requested == null || requested.isBlank()) {
      return ROLE_MUSICIAN;
    }
    String role = requested.trim().toLowerCase();
    if (ROLE_OWNER.equals(role)) {
      return ROLE_MUSICIAN;
    }
    return role;
  }

  private boolean isPlatformOwnerWallet(String wallet) {
    if (wallet == null || wallet.isBlank()) {
      return false;
    }
    return chainConfigRepository
        .findActive()
        .map(ChainConfig::getOwnerWalletPubkey)
        .filter(owner -> owner != null && !owner.isBlank())
        .map(owner -> owner.equals(wallet.trim()))
        .orElse(false);
  }

  private Map<String, Object> toSession(Account account) {
    ChainConfig cfg = chainConfigRepository.findActive().orElse(null);
    boolean owner =
        cfg != null
            && cfg.getOwnerWalletPubkey() != null
            && cfg.getOwnerWalletPubkey().equals(account.getWalletPubkey());
    Map<String, Object> session = new LinkedHashMap<>();
    session.put("account", account);
    session.put(
        "musicianProfile", enrollmentRepository.findMusicianByAccount(account.getId()).orElse(null));
    boolean studioAdmin =
        owner
            || (roleApplicationRepository != null
                && roleApplicationRepository
                    .findMembership(account.getId(), StudioAccess.STUDIO_ADMIN)
                    .isPresent());
    if (!owner && studioPowerOnchainRepository != null && account.getWalletPubkey() != null) {
      // A confirmed on-chain grant or revoke wins over the MariaDB role cache.
      studioAdmin =
          studioPowerOnchainRepository
              .findRoleGrant(account.getWalletPubkey())
              .map(row -> row.getRevokedAt() == null)
              .orElse(studioAdmin);
    }
    boolean partner =
        roleApplicationRepository != null
            && roleApplicationRepository
                .findMembership(account.getId(), StudioAccess.PARTNER)
                .isPresent();
    if (!partner
        && studioLedgerRepository != null
        && account.getWalletPubkey() != null
        && !account.getWalletPubkey().isBlank()) {
      partner =
          studioLedgerRepository
              .findPartnerByWallet(account.getWalletPubkey())
              .filter(row -> row.getActive() != null && row.getActive() == (byte) 1)
              .isPresent();
    }
    session.put("platformOwner", owner || ROLE_OWNER.equalsIgnoreCase(account.getRole()));
    session.put("studioAdmin", owner || studioAdmin);
    session.put("partner", partner);
    session.put("protocolFeeBps", cfg != null ? cfg.getProtocolFeeBps() : null);
    session.put("accessToken", jwtService.issue(account.getId(), account.getEmail()));
    return session;
  }

  private void requireWalletSignature(String wallet, String signature) {
    if (signature == null || signature.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wallet signature required");
    }
    Challenge challenge = challenges.remove(wallet);
    if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again");
    }
    byte[] message = WalletMessageVerifier.message(wallet, challenge.nonce());
    if (!WalletMessageVerifier.verify(wallet, message, signature.trim())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wallet signature rejected");
    }
  }

  private void assertCallerOwns(Account account) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof JwtPrincipal principal)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to this wallet");
    }
    if (account.getId() == null || !account.getId().equals(principal.accountId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to this wallet");
    }
  }

  private record Challenge(String nonce, Instant expiresAt) {}
}
