package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.IVenueBookingIxBuilder;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IAgentAuthorityRepository;
import com.eh8s.eh8s.repository.interfaces.IBandVaultRepository;
import com.eh8s.eh8s.repository.interfaces.IVenueBookingOnchainRepository;
import com.eh8s.eh8s.service.interfaces.IBandVaultService;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.interfaces.IVenueBookingOnchainService;
import com.eh8s.eh8s.service.interfaces.IVenueService;
import com.eh8s.eh8s.service.solana.AgentAuthorityIxBuilder;
import com.eh8s.eh8s.service.solana.SettleConcertIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.VenueBookingIxBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Venue listing and booking escrow on DevNet. Venues register and propose (escrow the gross);
 * the owner or a STAGE agent approves venues and confirms bookings; the owner or a VAULT agent
 * settles from the escrow. Every confirm step verifies the signature before writing.
 */
@Service
public class VenueBookingOnchainService implements IVenueBookingOnchainService {

  private static final BigDecimal MICRO = new BigDecimal("1000000");

  private final IVenueBookingOnchainRepository repository;
  private final IAgentAuthorityRepository agentRepository;
  private final IBandVaultRepository bandVaultRepository;
  private final IBandVaultService bandVaultService;
  private final IVenueService venueService;
  private final IVenueBookingIxBuilder ixBuilder;
  private final ISolanaTxVerifier txVerifier;

  /**
   * Creates the service.
   *
   * @param repository venue listing / booking escrow persistence
   * @param agentRepository chain config and agent authorizations
   * @param bandVaultRepository bands
   * @param bandVaultService synced BandVault weights
   * @param venueService off-chain settlement + member claims split
   * @param ixBuilder venue / booking instruction builder
   * @param txVerifier DevNet transaction verifier
   */
  public VenueBookingOnchainService(
      IVenueBookingOnchainRepository repository,
      IAgentAuthorityRepository agentRepository,
      IBandVaultRepository bandVaultRepository,
      IBandVaultService bandVaultService,
      IVenueService venueService,
      IVenueBookingIxBuilder ixBuilder,
      ISolanaTxVerifier txVerifier) {
    this.repository = repository;
    this.agentRepository = agentRepository;
    this.bandVaultRepository = bandVaultRepository;
    this.bandVaultService = bandVaultService;
    this.venueService = venueService;
    this.ixBuilder = ixBuilder;
    this.txVerifier = txVerifier;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> venueConsole(Long venueId) {
    ChainConfig cfg = requireChainConfig();
    Venue venue = requireVenue(venueId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("programId", cfg.getProgramIdDevnet());
    out.put("venue", venue);
    out.put("bookings", repository.findBookingsByVenue(venueId));
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildRegister(Long venueId, String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    Venue venue = requireVenue(venueId);
    String wallet = requireWallet(walletPubkey, "walletPubkey");
    requireRegistrable(venue, wallet);
    return registerIx(cfg, venue, wallet);
  }

  /** {@inheritDoc} */
  @Override
  public Venue confirmRegister(Long venueId, String walletPubkey, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    Venue venue = requireVenue(venueId);
    String wallet = requireWallet(walletPubkey, "walletPubkey");
    requireRegistrable(venue, wallet);
    String txSignature = requireFreshSignature(rawTxSignature);
    Map<String, Object> expected = registerIx(cfg, venue, wallet);
    String listing = (String) expected.get("venueListingPda");
    txVerifier.verify(
        cfg.getRpcUrl(),
        cfg.getProgramIdDevnet(),
        txSignature,
        "register_venue",
        (String) expected.get("dataHex"),
        wallet,
        ISolanaTxVerifier.accounts(wallet, listing, SolanaPda.SYSTEM_PROGRAM));
    return repository.markRegistered(venueId, wallet, listing, txSignature);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> stageQueue(String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    String agentAccount =
        agentAccount(cfg, walletPubkey, AgentAuthorityIxBuilder.PERM_STAGE, "STAGE (0x04)");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("signerWalletPubkey", walletPubkey.trim());
    out.put("signerRole", signerRole(cfg, agentAccount, "stage_agent"));
    out.put("pendingVenues", repository.findVenuesByListingStatus("pending"));
    out.put("proposedBookings", repository.findBookingsByOnchainStatus(List.of("proposed")));
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildApprove(Long venueId, String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    String signer = requireWallet(walletPubkey, "walletPubkey");
    String agentAccount =
        agentAccount(cfg, signer, AgentAuthorityIxBuilder.PERM_STAGE, "STAGE (0x04)");
    Venue venue = requirePendingListing(venueId);
    Map<String, Object> ix =
        ixBuilder.buildApprove(
            cfg.getProgramIdDevnet(), cfg.getRpcUrl(), signer, venue.getVenueListingPda(), agentAccount);
    ix.put("venueId", venueId);
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public Venue confirmApprove(Long venueId, String walletPubkey, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    String signer = requireWallet(walletPubkey, "walletPubkey");
    String agentAccount =
        agentAccount(cfg, signer, AgentAuthorityIxBuilder.PERM_STAGE, "STAGE (0x04)");
    Venue venue = requirePendingListing(venueId);
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected =
        ixBuilder.buildApprove(
            programId, cfg.getRpcUrl(), signer, venue.getVenueListingPda(), agentAccount);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "approve_venue",
        (String) expected.get("dataHex"),
        signer,
        ISolanaTxVerifier.accounts(
            signer,
            SolanaPda.configPda(programId),
            venue.getVenueListingPda(),
            agentAccount));
    return repository.markApproved(venueId, signer, txSignature);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildPropose(
      Long bookingId, String walletPubkey, BigDecimal grossUsdc) {
    ChainConfig cfg = requireChainConfig();
    Booking booking = requireBooking(bookingId);
    Venue venue = requireVenueSigner(booking, requireWallet(walletPubkey, "walletPubkey"));
    requireProposable(booking, venue);
    BigDecimal gross = requireGross(grossUsdc);
    Concert concert = ensureConcert(booking);
    Map<String, Object> ix = proposeIx(cfg, booking, venue, concert.getId(), gross);
    ix.put("bookingId", bookingId);
    ix.put("grossUsdc", gross);
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public Booking confirmPropose(
      Long bookingId, String walletPubkey, BigDecimal grossUsdc, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    Booking booking = requireBooking(bookingId);
    Venue venue = requireVenueSigner(booking, requireWallet(walletPubkey, "walletPubkey"));
    requireProposable(booking, venue);
    BigDecimal gross = requireGross(grossUsdc);
    Concert concert =
        repository
            .findConcertByBooking(bookingId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.CONFLICT, "Build propose_booking before confirming it"));
    String txSignature = requireFreshSignature(rawTxSignature);
    Map<String, Object> expected = proposeIx(cfg, booking, venue, concert.getId(), gross);
    String programId = cfg.getProgramIdDevnet();
    String access = (String) expected.get("venueAccessTokenPda");
    String escrow = (String) expected.get("escrowPda");
    List<String> accounts =
        ISolanaTxVerifier.accounts(
            venue.getWalletPubkey(),
            SolanaPda.configPda(programId),
            (String) expected.get("venueListingPda"),
            VenueBookingIxBuilder.bandVaultPda(booking.getBandId(), programId),
            access,
            cfg.getUsdcMint(),
            escrow,
            (String) expected.get("venueUsdcAta"),
            SolanaPda.TOKEN_PROGRAM,
            SolanaPda.SYSTEM_PROGRAM);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "propose_booking",
        (String) expected.get("dataHex"),
        venue.getWalletPubkey(),
        accounts);
    return repository.markProposed(bookingId, concert.getId(), gross, access, escrow, txSignature);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildConfirm(Long bookingId, String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    String signer = requireWallet(walletPubkey, "walletPubkey");
    String agentAccount =
        agentAccount(cfg, signer, AgentAuthorityIxBuilder.PERM_STAGE, "STAGE (0x04)");
    Booking booking = requireStatus(requireBooking(bookingId), "proposed");
    Venue venue = requireVenue(booking.getVenueId());
    String text = contractText(booking, venue);
    Map<String, Object> ix =
        ixBuilder.buildConfirm(
            cfg.getProgramIdDevnet(),
            cfg.getRpcUrl(),
            signer,
            booking.getVenueAccessTokenPda(),
            sha256(text),
            agentAccount);
    ix.put("bookingId", bookingId);
    ix.put("contractText", text);
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public Booking confirmConfirm(Long bookingId, String walletPubkey, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    String signer = requireWallet(walletPubkey, "walletPubkey");
    String agentAccount =
        agentAccount(cfg, signer, AgentAuthorityIxBuilder.PERM_STAGE, "STAGE (0x04)");
    Booking booking = requireStatus(requireBooking(bookingId), "proposed");
    Venue venue = requireVenue(booking.getVenueId());
    String txSignature = requireFreshSignature(rawTxSignature);
    String text = contractText(booking, venue);
    byte[] hash = sha256(text);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected =
        ixBuilder.buildConfirm(
            programId, cfg.getRpcUrl(), signer, booking.getVenueAccessTokenPda(), hash, agentAccount);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "confirm_booking",
        (String) expected.get("dataHex"),
        signer,
        ISolanaTxVerifier.accounts(
            signer,
            SolanaPda.configPda(programId),
            booking.getVenueAccessTokenPda(),
            agentAccount));
    return repository.markConfirmed(bookingId, text, HexFormat.of().formatHex(hash), txSignature);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildCancel(Long bookingId, String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    Booking booking = requireStatus(requireBooking(bookingId), "proposed");
    Venue venue = requireVenueSigner(booking, requireWallet(walletPubkey, "walletPubkey"));
    Map<String, Object> ix =
        ixBuilder.buildCancel(
            cfg.getProgramIdDevnet(),
            cfg.getRpcUrl(),
            cfg.getUsdcMint(),
            venue.getWalletPubkey(),
            booking.getVenueAccessTokenPda());
    ix.put("bookingId", bookingId);
    ix.put("refundUsdc", booking.getGrossUsdc());
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public Booking confirmCancel(Long bookingId, String walletPubkey, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    Booking booking = requireStatus(requireBooking(bookingId), "proposed");
    Venue venue = requireVenueSigner(booking, requireWallet(walletPubkey, "walletPubkey"));
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected =
        ixBuilder.buildCancel(
            programId,
            cfg.getRpcUrl(),
            cfg.getUsdcMint(),
            venue.getWalletPubkey(),
            booking.getVenueAccessTokenPda());
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "cancel_booking",
        (String) expected.get("dataHex"),
        venue.getWalletPubkey(),
        ISolanaTxVerifier.accounts(
            venue.getWalletPubkey(),
            booking.getVenueAccessTokenPda(),
            (String) expected.get("escrowPda"),
            (String) expected.get("venueUsdcAta"),
            SolanaPda.TOKEN_PROGRAM));
    return repository.markCancelled(bookingId, txSignature);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> vaultQueue(String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    String agentAccount =
        agentAccount(cfg, walletPubkey, AgentAuthorityIxBuilder.PERM_VAULT, "VAULT (0x08)");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("signerWalletPubkey", walletPubkey.trim());
    out.put("signerRole", signerRole(cfg, agentAccount, "vault_agent"));
    out.put("confirmedBookings", repository.findBookingsByOnchainStatus(List.of("confirmed")));
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildSettle(Long bookingId, String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    String signer = requireWallet(walletPubkey, "walletPubkey");
    String agentAccount =
        agentAccount(cfg, signer, AgentAuthorityIxBuilder.PERM_VAULT, "VAULT (0x08)");
    Booking booking = requireStatus(requireBooking(bookingId), "confirmed");
    Map<String, Object> ix = settleIx(cfg, booking, signer, agentAccount);
    ix.put("bookingId", bookingId);
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public ConcertSettlement confirmSettle(
      Long bookingId, String walletPubkey, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    String signer = requireWallet(walletPubkey, "walletPubkey");
    String agentAccount =
        agentAccount(cfg, signer, AgentAuthorityIxBuilder.PERM_VAULT, "VAULT (0x08)");
    Booking booking = requireStatus(requireBooking(bookingId), "confirmed");
    String txSignature = requireFreshSignature(rawTxSignature);
    Map<String, Object> expected = settleIx(cfg, booking, signer, agentAccount);
    String programId = cfg.getProgramIdDevnet();
    @SuppressWarnings("unchecked")
    List<String> profiles = (List<String>) expected.get("memberProfilePdas");
    String venueWallet = (String) expected.get("venueWalletPubkey");
    List<String> accounts = new ArrayList<>(Collections.nCopies(12 + profiles.size(), null));
    accounts.set(0, signer);
    accounts.set(1, SolanaPda.configPda(programId));
    accounts.set(2, booking.getVenueAccessTokenPda());
    accounts.set(3, VenueBookingIxBuilder.bandVaultPda(booking.getBandId(), programId));
    accounts.set(4, (String) expected.get("concertSettlementPda"));
    accounts.set(5, (String) expected.get("escrowPda"));
    accounts.set(6, SolanaPda.associatedTokenAddress(venueWallet, cfg.getUsdcMint()));
    accounts.set(7, SolanaPda.associatedTokenAddress(SolanaPda.treasuryPda(programId), cfg.getUsdcMint()));
    accounts.set(
        8,
        SolanaPda.associatedTokenAddress(
            SolanaPda.findProgramAddress(List.of(SolanaPda.seed("vault")), programId),
            cfg.getUsdcMint()));
    accounts.set(9, SolanaPda.TOKEN_PROGRAM);
    accounts.set(10, SolanaPda.SYSTEM_PROGRAM);
    accounts.set(11, agentAccount);
    for (int i = 0; i < profiles.size(); i++) {
      accounts.set(12 + i, profiles.get(i));
    }
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "settle_booking",
        (String) expected.get("dataHex"),
        signer,
        accounts);
    Long concertId = booking.getOnchainConcertId();
    ConcertSettlement settlement =
        repository
            .findSettlementByConcert(concertId)
            .orElseGet(() -> venueService.settle(concertId, booking.getGrossUsdc()));
    return repository.markSettledFromEscrow(
        settlement.getId(),
        bookingId,
        signer,
        txSignature,
        (String) expected.get("concertSettlementPda"));
  }

  /**
   * Canonical contract text whose SHA-256 is pinned by {@code confirm_booking}. Deterministic:
   * the same booking always yields the same text (and hash) at build and at confirm.
   *
   * @param booking proposed booking
   * @param venue its venue
   * @return contract text, LF line endings
   */
  static String contractText(Booking booking, Venue venue) {
    return String.join(
            "\n",
            "EH8S BOOKING CONTRACT v1",
            "booking_id=" + booking.getId(),
            "venue_id=" + venue.getId(),
            "venue_code=" + venue.getCode(),
            "venue_wallet=" + venue.getWalletPubkey(),
            "band_id=" + booking.getBandId(),
            "show_date=" + booking.getShowDate(),
            "concert_id=" + booking.getOnchainConcertId(),
            "contract_type_id=" + venue.getContractTypeId(),
            "gross_usdc=" + booking.getGrossUsdc().setScale(6, RoundingMode.UNNECESSARY),
            "venue_access_token=" + booking.getVenueAccessTokenPda(),
            "escrow=" + booking.getEscrowPda())
        + "\n";
  }

  /**
   * SHA-256 of UTF-8 text.
   *
   * @param text input
   * @return 32-byte digest
   */
  static byte[] sha256(String text) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Show date as the program's {@code date_ymd} (yyyymmdd).
   *
   * @param date show date
   * @return yyyymmdd
   */
  static int dateYmd(LocalDate date) {
    return date.getYear() * 10_000 + date.getMonthValue() * 100 + date.getDayOfMonth();
  }

  private Map<String, Object> registerIx(ChainConfig cfg, Venue venue, String wallet) {
    String name = venue.getName() == null ? "" : venue.getName().trim();
    if (name.isEmpty()
        || name.getBytes(StandardCharsets.UTF_8).length > VenueBookingIxBuilder.MAX_VENUE_NAME) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Venue name must be 1..64 bytes to list it on-chain");
    }
    String country = venue.getCountryCode();
    if (country == null || !country.matches("[A-Z]{3}")) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Venue country must be ISO-3166 alpha-3 (e.g. MEX)");
    }
    if (venue.getCapacity() == null || venue.getCapacity() <= 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Venue capacity must be > 0");
    }
    Long type = venue.getContractTypeId();
    if (type == null || type < 1 || type > VenueBookingIxBuilder.MAX_CONTRACT_TYPE) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Venue contract type must be 1..4");
    }
    Map<String, Object> ix =
        ixBuilder.buildRegister(
            cfg.getProgramIdDevnet(),
            cfg.getRpcUrl(),
            wallet,
            venue.getId(),
            name,
            country,
            venue.getCapacity(),
            type.intValue());
    ix.put("name", name);
    ix.put("countryCode", country);
    ix.put("capacity", venue.getCapacity());
    ix.put("contractTypeId", type);
    return ix;
  }

  private Map<String, Object> proposeIx(
      ChainConfig cfg, Booking booking, Venue venue, Long concertId, BigDecimal gross) {
    return ixBuilder.buildPropose(
        cfg.getProgramIdDevnet(),
        cfg.getRpcUrl(),
        cfg.getUsdcMint(),
        venue.getWalletPubkey(),
        venue.getId(),
        concertId,
        booking.getBandId(),
        dateYmd(booking.getShowDate()),
        toAtomic(gross));
  }

  private Map<String, Object> settleIx(
      ChainConfig cfg, Booking booking, String signer, String agentAccount) {
    Venue venue = requireVenue(booking.getVenueId());
    Long concertId = booking.getOnchainConcertId();
    if (concertId == null || booking.getGrossUsdc() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking has no escrowed concert");
    }
    ConcertSettlement existing = repository.findSettlementByConcert(concertId).orElse(null);
    if (existing != null && "confirmed".equals(existing.getOnChainStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Concert already settled on-chain");
    }
    if (existing != null && existing.getGrossUsdc().compareTo(booking.getGrossUsdc()) != 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "An off-chain settlement with a different gross already exists");
    }
    BigDecimal gross = booking.getGrossUsdc();
    BigDecimal expenses = repository.sumExpenses(concertId);
    long grossAtomic = toAtomic(gross);
    long expensesAtomic = toAtomic(expenses);
    long netAtomic = grossAtomic - expensesAtomic;
    if (netAtomic <= 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Concert expenses must be lower than the escrowed gross");
    }
    Band band =
        bandVaultRepository
            .findBand(booking.getBandId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown band"));
    List<Map<String, Object>> weights = bandVaultService.syncedWeights(band);
    if (weights.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Band vault has no synced member weights");
    }
    List<String> members = weights.stream().map(w -> (String) w.get("wallet")).toList();
    List<Integer> bps = weights.stream().map(w -> ((Number) w.get("bps")).intValue()).toList();
    int feeBps = cfg.getProtocolFeeBps() == null ? 1500 : cfg.getProtocolFeeBps();
    long feeAtomic = SettleConcertIxBuilder.feeAtomic(netAtomic, feeBps);
    long poolAtomic = netAtomic - feeAtomic;
    Map<String, Object> ix =
        ixBuilder.buildSettle(
            cfg.getProgramIdDevnet(),
            cfg.getRpcUrl(),
            cfg.getUsdcMint(),
            signer,
            venue.getWalletPubkey(),
            booking.getVenueAccessTokenPda(),
            booking.getBandId(),
            concertId,
            grossAtomic,
            expensesAtomic,
            agentAccount,
            members);
    List<Long> shares = SettleConcertIxBuilder.splitMembers(poolAtomic, bps);
    ix.put("grossUsdc", fromAtomic(grossAtomic));
    ix.put("expensesUsdc", fromAtomic(expensesAtomic));
    ix.put("netUsdc", fromAtomic(netAtomic));
    ix.put("eh8sFeeUsdc", fromAtomic(feeAtomic));
    ix.put("bandPoolUsdc", fromAtomic(poolAtomic));
    ix.put("protocolFeeBps", feeBps);
    ix.put("memberPendingUsdc", shares.stream().map(VenueBookingOnchainService::fromAtomic).toList());
    return ix;
  }

  private Concert ensureConcert(Booking booking) {
    return repository
        .findConcertByBooking(booking.getId())
        .orElseGet(
            () -> {
              Long cycleId =
                  repository
                      .findLatestCycleId(booking.getBandId())
                      .orElseThrow(
                          () ->
                              new ResponseStatusException(
                                  HttpStatus.CONFLICT,
                                  "Band has no SPP cycle yet; open one before proposing"));
              return repository.createConcertForBooking(booking, cycleId);
            });
  }

  /**
   * AgentAuthority account for the signer: program id (Anchor {@code None}) for the owner, the
   * agent PDA for a live agent holding {@code bit}; otherwise 403.
   */
  private String agentAccount(ChainConfig cfg, String walletPubkey, int bit, String label) {
    if (blank(walletPubkey)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey is required");
    }
    String wallet = walletPubkey.trim();
    String programId = cfg.getProgramIdDevnet();
    if (wallet.equals(cfg.getOwnerWalletPubkey())) {
      return programId;
    }
    boolean hasBit =
        agentRepository
            .findLatestAuthorization(wallet)
            .map(a -> a.getPermissions() != null && (a.getPermissions() & bit) != 0)
            .orElse(false);
    if (!hasBit) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the owner or a " + label + " agent can do this");
    }
    return SolanaPda.walletPda("agent", wallet, programId);
  }

  private static String signerRole(ChainConfig cfg, String agentAccount, String agentRole) {
    return agentAccount.equals(cfg.getProgramIdDevnet()) ? "owner" : agentRole;
  }

  private void requireRegistrable(Venue venue, String wallet) {
    if (venue.getListingStatus() != null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Venue is already listed on-chain (" + venue.getListingStatus() + ")");
    }
    if (!blank(venue.getWalletPubkey()) && !venue.getWalletPubkey().equals(wallet)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Venue belongs to another wallet");
    }
  }

  private Venue requirePendingListing(Long venueId) {
    Venue venue = requireVenue(venueId);
    if (!"pending".equals(venue.getListingStatus()) || blank(venue.getVenueListingPda())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Venue listing is not pending approval");
    }
    return venue;
  }

  private Venue requireVenueSigner(Booking booking, String wallet) {
    Venue venue = requireVenue(booking.getVenueId());
    if (blank(venue.getWalletPubkey()) || !venue.getWalletPubkey().equals(wallet)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the venue wallet can sign this booking step");
    }
    return venue;
  }

  private void requireProposable(Booking booking, Venue venue) {
    if (!"approved".equals(venue.getListingStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Venue listing must be approved before proposing a show");
    }
    String status = booking.getOnchainStatus();
    if (status != null && !"cancelled".equals(status)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Booking escrow is already " + status);
    }
    Band band =
        bandVaultRepository
            .findBand(booking.getBandId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown band"));
    if (blank(band.getBandVaultPda())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Activate the band vault on-chain before proposing");
    }
  }

  private static Booking requireStatus(Booking booking, String status) {
    if (!status.equals(booking.getOnchainStatus()) || blank(booking.getVenueAccessTokenPda())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Booking escrow must be " + status + " (is " + booking.getOnchainStatus() + ")");
    }
    return booking;
  }

  private Venue requireVenue(Long venueId) {
    return repository
        .findVenue(venueId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown venue"));
  }

  private Booking requireBooking(Long bookingId) {
    return repository
        .findBooking(bookingId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown booking"));
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
        agentRepository
            .findActiveChainConfig()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No active chain_config"));
    if (blank(cfg.getOwnerWalletPubkey()) || blank(cfg.getUsdcMint())) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "chain_config needs owner wallet and USDC mint");
    }
    return cfg;
  }

  private static BigDecimal requireGross(BigDecimal gross) {
    if (gross == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "grossUsdc is required");
    }
    if (gross.signum() <= 0 || gross.stripTrailingZeros().scale() > 2) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "grossUsdc must be > 0 with at most 2 decimals");
    }
    return gross.setScale(2, RoundingMode.UNNECESSARY);
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

  private static long toAtomic(BigDecimal usdc) {
    return usdc.setScale(6, RoundingMode.HALF_UP).multiply(MICRO).longValueExact();
  }

  private static BigDecimal fromAtomic(long atomic) {
    return BigDecimal.valueOf(atomic).divide(MICRO).setScale(6, RoundingMode.UNNECESSARY);
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
