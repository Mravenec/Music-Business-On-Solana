package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentAuthority;
import com.eh8s.eh8s.repository.interfaces.IAgentAuthorityRepository;
import com.eh8s.eh8s.repository.interfaces.IBandVaultRepository;
import com.eh8s.eh8s.repository.interfaces.IVenueBookingOnchainRepository;
import com.eh8s.eh8s.service.interfaces.IBandVaultService;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.interfaces.IVenueService;
import com.eh8s.eh8s.service.solana.AgentAuthorityIxBuilder;
import com.eh8s.eh8s.service.solana.SettleClaimIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.VenueBookingIxBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class VenueBookingOnchainServiceTest {

  static final String PROGRAM = "GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG";
  static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
  static final String STAGE = "YmTYQJifjP2DawdxDUYuNCZJ5to9ge5nNGaWGW2ozJU";
  static final String VAULT = "9ptwWxWj5YQKP3pD5b4UEDksARBzkSQPtS3Kyvjnff4Z";
  static final String VENUE_WALLET = "U9mzuiHMVUsCw9BLKGibq55BPZuLaxipnvKFvn76Vjq";
  static final String MEMBER = "CoP8dydvHXTDUoxxB6tAQk6yu3Q6YRkqNj8SY3vtR53o";
  static final String MINT = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU";
  static final String CONFIG_PDA = "GgpAKj9FZppC8gckF6SzevirNbgkfhpEbqiz1GE6oKLF";

  private final Map<Long, Venue> venues = new HashMap<>();
  private final Map<Long, Booking> bookings = new HashMap<>();
  private final Map<Long, Concert> concerts = new HashMap<>();
  private final List<AgentAuthority> authorizations = new ArrayList<>();
  private final List<String> recorded = new ArrayList<>();
  private final Map<String, Object> verified = new HashMap<>();
  private ConcertSettlement settlement;

  @BeforeEach
  void seed() {
    Venue v = new Venue();
    v.setId(1L);
    v.setCode("bar-la-cueva");
    v.setName("Bar La Cueva");
    v.setCountryCode("MEX");
    v.setCapacity(180);
    v.setContractTypeId(3L);
    venues.put(1L, v);
    Booking b = new Booking();
    b.setId(5L);
    b.setVenueId(1L);
    b.setBandId(2L);
    b.setShowDate(LocalDate.of(2026, 12, 5));
    bookings.put(5L, b);
  }

  @Test
  void builderEncodesRegisterProposeAndSettle() {
    VenueBookingIxBuilder b = new VenueBookingIxBuilder();
    Map<String, Object> reg =
        b.buildRegister(PROGRAM, "rpc", VENUE_WALLET, 1L, "Bar La Cueva", "MEX", 180, 3);
    ByteBuffer args = ByteBuffer.allocate(8 + 4 + 12 + 3 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
    args.putLong(1L).putInt(12).put("Bar La Cueva".getBytes(StandardCharsets.UTF_8));
    args.put("MEX".getBytes(StandardCharsets.US_ASCII)).putInt(180).put((byte) 3);
    assertEquals(disc("register_venue") + HexFormat.of().formatHex(args.array()), reg.get("dataHex"));
    assertEquals(VenueBookingIxBuilder.listingPda(VENUE_WALLET, 1L, PROGRAM), reg.get("venueListingPda"));

    Map<String, Object> prop =
        b.buildPropose(PROGRAM, "rpc", MINT, VENUE_WALLET, 1L, 9L, 2L, 20261205, 1_000_000_000L);
    assertEquals((8 + 8 + 8 + 4 + 8) * 2, ((String) prop.get("dataHex")).length());
    String access = VenueBookingIxBuilder.accessPda(VENUE_WALLET, 2L, 20261205, PROGRAM);
    assertEquals(access, prop.get("venueAccessTokenPda"));
    assertEquals(VenueBookingIxBuilder.escrowPda(access, PROGRAM), prop.get("escrowPda"));
    assertEquals(SolanaPda.associatedTokenAddress(VENUE_WALLET, MINT), prop.get("venueUsdcAta"));
    assertEquals(10, ((List<?>) prop.get("accounts")).size());

    Map<String, Object> settle =
        b.buildSettle(PROGRAM, "rpc", MINT, VAULT, VENUE_WALLET, access, 2L, 9L, 10L, 1L, PROGRAM, List.of(MEMBER));
    List<?> accounts = (List<?>) settle.get("accounts");
    assertEquals(13, accounts.size());
    assertEquals(
        SolanaPda.walletPda("musician", MEMBER, PROGRAM), ((Map<?, ?>) accounts.get(12)).get("pubkey"));
    assertEquals(PROGRAM, ((Map<?, ?>) accounts.get(11)).get("pubkey"));
  }

  @Test
  void registerIsFirstComeByWalletAndVerifiesTheListingPda() {
    VenueBookingOnchainService s = service();
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildRegister(s, 1L, body("not-a-wallet")));
    venues.get(1L).setCountryCode("mex");
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildRegister(s, 1L, body(VENUE_WALLET)));
    venues.get(1L).setCountryCode("MEX");
    assertEquals("register_venue", OnchainCalls.buildRegister(s, 1L, body(VENUE_WALLET)).get("instruction"));

    Venue listed = OnchainCalls.confirmRegister(s, 1L, signed(body(VENUE_WALLET), "sig-reg"));
    assertEquals("pending", listed.getListingStatus());
    assertEquals(
        ISolanaTxVerifier.accounts(
            VENUE_WALLET,
            VenueBookingIxBuilder.listingPda(VENUE_WALLET, 1L, PROGRAM),
            SolanaPda.SYSTEM_PROGRAM),
        verified.get("accounts"));
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildRegister(s, 1L, body(STAGE)));
  }

  @Test
  void onlyOwnerOrStageAgentApprovesAPendingListing() {
    VenueBookingOnchainService s = service();
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildApprove(s, 1L, body(OWNER)));
    OnchainCalls.confirmRegister(s, 1L, signed(body(VENUE_WALLET), "sig-reg"));
    grant(VAULT, AgentAuthorityIxBuilder.PERM_VAULT);
    assertStatus(HttpStatus.FORBIDDEN, () -> OnchainCalls.buildApprove(s, 1L, body(VAULT)));
    assertStatus(HttpStatus.FORBIDDEN, () -> s.stageQueue(VENUE_WALLET));
    assertEquals(PROGRAM, OnchainCalls.buildApprove(s, 1L, body(OWNER)).get("agentAuthorityAccount"));

    grant(STAGE, AgentAuthorityIxBuilder.PERM_STAGE);
    assertEquals("stage_agent", s.stageQueue(STAGE).get("signerRole"));
    Venue approved = OnchainCalls.confirmApprove(s, 1L, signed(body(STAGE), "sig-approve"));
    assertEquals("approved", approved.getListingStatus());
    assertEquals(STAGE, approved.getApprovedByWallet());
    String listing = VenueBookingIxBuilder.listingPda(VENUE_WALLET, 1L, PROGRAM);
    assertEquals(
        ISolanaTxVerifier.accounts(
            STAGE,
            CONFIG_PDA,
            listing,
            SolanaPda.walletPda("agent", STAGE, PROGRAM)),
        verified.get("accounts"));
  }

  @Test
  void proposeNeedsApprovedVenueSignerAndGrossAndCreatesTheConcertOnce() {
    VenueBookingOnchainService s = service();
    OnchainCalls.confirmRegister(s, 1L, signed(body(VENUE_WALLET), "sig-reg"));
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildPropose(s, 5L, gross(body(VENUE_WALLET), "1000")));
    OnchainCalls.confirmApprove(s, 1L, signed(body(OWNER), "sig-approve"));
    assertStatus(HttpStatus.FORBIDDEN, () -> OnchainCalls.buildPropose(s, 5L, gross(body(STAGE), "1000")));
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildPropose(s, 5L, gross(body(VENUE_WALLET), "0")));
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildPropose(s, 5L, gross(body(VENUE_WALLET), "1.005")));

    Map<String, Object> ix = OnchainCalls.buildPropose(s, 5L, gross(body(VENUE_WALLET), "1000"));
    OnchainCalls.buildPropose(s, 5L, gross(body(VENUE_WALLET), "1000"));
    assertEquals(1, concerts.size());
    assertEquals(20261205, ix.get("dateYmd"));
    assertEquals(1_000_000_000L, ix.get("grossAtomic"));

    Booking proposed = OnchainCalls.confirmPropose(s, 5L, signed(gross(body(VENUE_WALLET), "1000"), "sig-prop"));
    assertEquals("proposed", proposed.getOnchainStatus());
    assertEquals(new BigDecimal("1000.00"), proposed.getGrossUsdc());
    assertEquals(9L, proposed.getOnchainConcertId());
    @SuppressWarnings("unchecked")
    List<String> accounts = (List<String>) verified.get("accounts");
    assertEquals(10, accounts.size());
    assertEquals(10, accounts.stream().filter(a -> a != null).count());
    assertEquals(MINT, accounts.get(5));
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildPropose(s, 5L, gross(body(VENUE_WALLET), "1000")));
  }

  @Test
  void confirmPinsTheSha256OfTheCanonicalContractText() {
    VenueBookingOnchainService s = proposedBooking();
    assertStatus(HttpStatus.FORBIDDEN, () -> OnchainCalls.buildConfirm(s, 5L, body(VENUE_WALLET)));
    grant(STAGE, AgentAuthorityIxBuilder.PERM_STAGE);
    Map<String, Object> ix = OnchainCalls.buildConfirm(s, 5L, body(STAGE));
    String text = (String) ix.get("contractText");
    assertTrue(text.startsWith("EH8S BOOKING CONTRACT v1\nbooking_id=5\n"));
    assertTrue(text.contains("gross_usdc=1000.000000\n"));
    String hash = HexFormat.of().formatHex(VenueBookingOnchainService.sha256(text));
    assertEquals(hash, ix.get("contractHashHex"));
    assertEquals(disc("confirm_booking") + hash, ix.get("dataHex"));

    Booking confirmed = OnchainCalls.confirmConfirm(s, 5L, signed(body(STAGE), "sig-confirm"));
    assertEquals("confirmed", confirmed.getOnchainStatus());
    assertEquals(hash, confirmed.getContractHash());
    assertEquals(text, confirmed.getContractText());
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildCancel(s, 5L, body(VENUE_WALLET)));
  }

  @Test
  void onlyTheVenueCancelsWhileProposed() {
    VenueBookingOnchainService s = proposedBooking();
    assertStatus(HttpStatus.FORBIDDEN, () -> OnchainCalls.buildCancel(s, 5L, body(OWNER)));
    Map<String, Object> ix = OnchainCalls.buildCancel(s, 5L, body(VENUE_WALLET));
    assertEquals(new BigDecimal("1000.00"), ix.get("refundUsdc"));
    Booking cancelled = OnchainCalls.confirmCancel(s, 5L, signed(body(VENUE_WALLET), "sig-cancel"));
    assertEquals("cancelled", cancelled.getOnchainStatus());
    assertEquals(null, cancelled.getVenueAccessTokenPda());
    assertEquals(5, ((List<?>) verified.get("accounts")).stream().filter(a -> a != null).count());
  }

  @Test
  void onlyOwnerOrVaultAgentSettlesAConfirmedEscrow() {
    VenueBookingOnchainService s = proposedBooking();
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildSettle(s, 5L, body(OWNER)));
    OnchainCalls.confirmConfirm(s, 5L, signed(body(OWNER), "sig-confirm"));
    grant(STAGE, AgentAuthorityIxBuilder.PERM_STAGE);
    assertStatus(HttpStatus.FORBIDDEN, () -> OnchainCalls.buildSettle(s, 5L, body(STAGE)));
    assertStatus(HttpStatus.FORBIDDEN, () -> s.vaultQueue(STAGE));
    grant(VAULT, AgentAuthorityIxBuilder.PERM_VAULT);
    assertEquals(1, ((List<?>) s.vaultQueue(VAULT).get("confirmedBookings")).size());

    Map<String, Object> ix = OnchainCalls.buildSettle(s, 5L, body(VAULT));
    assertEquals(new BigDecimal("100.000000"), ix.get("expensesUsdc"));
    assertEquals(new BigDecimal("135.000000"), ix.get("eh8sFeeUsdc"));
    assertEquals(new BigDecimal("765.000000"), ix.get("bandPoolUsdc"));

    ConcertSettlement done = OnchainCalls.confirmSettle(s, 5L, signed(body(VAULT), "sig-settle"));
    assertEquals("settle_booking", done.getSettledVia());
    assertEquals(VAULT, done.getSettledByWallet());
    assertEquals("settled", bookings.get(5L).getOnchainStatus());
    @SuppressWarnings("unchecked")
    List<String> accounts = (List<String>) verified.get("accounts");
    assertEquals(SolanaPda.walletPda("agent", VAULT, PROGRAM), accounts.get(11));
    assertEquals(SolanaPda.walletPda("musician", MEMBER, PROGRAM), accounts.get(12));
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildSettle(s, 5L, body(VAULT)));
  }

  private VenueBookingOnchainService proposedBooking() {
    VenueBookingOnchainService s = service();
    OnchainCalls.confirmRegister(s, 1L, signed(body(VENUE_WALLET), "sig-reg"));
    OnchainCalls.confirmApprove(s, 1L, signed(body(OWNER), "sig-approve"));
    OnchainCalls.buildPropose(s, 5L, gross(body(VENUE_WALLET), "1000"));
    OnchainCalls.confirmPropose(s, 5L, signed(gross(body(VENUE_WALLET), "1000"), "sig-prop"));
    return s;
  }

  private static String disc(String name) {
    return HexFormat.of().formatHex(SettleClaimIxBuilder.anchorDiscriminator(name));
  }

  private void grant(String wallet, int bits) {
    AgentAuthority a = new AgentAuthority();
    a.setAgentWalletPubkey(wallet);
    a.setPermissions((byte) bits);
    authorizations.add(0, a);
  }

  private static Map<String, Object> body(String wallet) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("walletPubkey", wallet);
    return body;
  }

  private static Map<String, Object> gross(Map<String, Object> body, String gross) {
    body.put("grossUsdc", gross);
    return body;
  }

  private static Map<String, Object> signed(Map<String, Object> body, String sig) {
    body.put("txSignature", sig);
    return body;
  }

  private static void assertStatus(HttpStatus status, Runnable call) {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, call::run);
    assertEquals(status, ex.getStatusCode());
  }

  private VenueBookingOnchainService service() {
    IAgentAuthorityRepository agents = mock(IAgentAuthorityRepository.class);
    ChainConfig cfg = new ChainConfig();
    cfg.setProgramIdDevnet(PROGRAM);
    cfg.setRpcUrl("https://api.devnet.solana.com");
    cfg.setOwnerWalletPubkey(OWNER);
    cfg.setUsdcMint(MINT);
    cfg.setProtocolFeeBps(1500);
    when(agents.findActiveChainConfig()).thenReturn(Optional.of(cfg));
    when(agents.findLatestAuthorization(any()))
        .thenAnswer(
            inv ->
                authorizations.stream()
                    .filter(a -> a.getAgentWalletPubkey().equals(inv.getArgument(0)))
                    .findFirst());
    Band band = new Band();
    band.setId(2L);
    band.setBandVaultPda(VenueBookingIxBuilder.bandVaultPda(2L, PROGRAM));
    IBandVaultRepository bands = mock(IBandVaultRepository.class);
    when(bands.findBand(2L)).thenReturn(Optional.of(band));
    IBandVaultService vaults = mock(IBandVaultService.class);
    when(vaults.syncedWeights(band)).thenReturn(List.of(Map.of("wallet", MEMBER, "bps", 10_000)));
    IVenueService venueService = mock(IVenueService.class);
    when(venueService.settle(eq(9L), any()))
        .thenAnswer(
            inv -> {
              settlement = new ConcertSettlement();
              settlement.setId(40L);
              settlement.setConcertId(9L);
              settlement.setGrossUsdc(inv.getArgument(1));
              return settlement;
            });
    return new VenueBookingOnchainService(
        repo(), agents, bands, vaults, venueService, new VenueBookingIxBuilder(), verifier());
  }

  private ISolanaTxVerifier verifier() {
    return new ISolanaTxVerifier() {
      @Override
      public void verify(
          String rpcUrl,
          String programId,
          String txSignature,
          String instruction,
          String expectedDataHex,
          String expectedSigner,
          List<String> expectedAccounts) {
        verified.put("instruction", instruction);
        verified.put("signer", expectedSigner);
        verified.put("accounts", expectedAccounts);
      }

      @Override
      public void verifyTransaction(
          JsonNode tx,
          String programId,
          String instruction,
          String expectedDataHex,
          String expectedSigner,
          List<String> expectedAccounts) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private IVenueBookingOnchainRepository repo() {
    return new IVenueBookingOnchainRepository() {
      @Override
      public Optional<Venue> findVenue(Long venueId) {
        return Optional.ofNullable(venues.get(venueId));
      }

      @Override
      public List<Venue> findVenuesByListingStatus(String listingStatus) {
        return venues.values().stream().filter(v -> listingStatus.equals(v.getListingStatus())).toList();
      }

      @Override
      public Venue markRegistered(Long venueId, String wallet, String listingPda, String tx) {
        Venue v = venues.get(venueId);
        v.setWalletPubkey(wallet);
        v.setVenueListingPda(listingPda);
        v.setListingStatus("pending");
        recorded.add(tx);
        return v;
      }

      @Override
      public Venue markApproved(Long venueId, String approverWallet, String tx) {
        Venue v = venues.get(venueId);
        v.setListingStatus("approved");
        v.setApprovedByWallet(approverWallet);
        recorded.add(tx);
        return v;
      }

      @Override
      public Optional<Booking> findBooking(Long bookingId) {
        return Optional.ofNullable(bookings.get(bookingId));
      }

      @Override
      public List<Booking> findBookingsByVenue(Long venueId) {
        return bookings.values().stream().filter(b -> venueId.equals(b.getVenueId())).toList();
      }

      @Override
      public List<Booking> findBookingsByOnchainStatus(List<String> statuses) {
        return bookings.values().stream()
            .filter(b -> b.getOnchainStatus() != null && statuses.contains(b.getOnchainStatus()))
            .toList();
      }

      @Override
      public Optional<Concert> findConcertByBooking(Long bookingId) {
        return concerts.values().stream().filter(c -> bookingId.equals(c.getBookingId())).findFirst();
      }

      @Override
      public Optional<Long> findLatestCycleId(Long bandId) {
        return Optional.of(3L);
      }

      @Override
      public Concert createConcertForBooking(Booking booking, Long sppCycleId) {
        Concert c = new Concert();
        c.setId(9L);
        c.setBookingId(booking.getId());
        c.setSppCycleId(sppCycleId);
        concerts.put(9L, c);
        booking.setOnchainConcertId(9L);
        return c;
      }

      @Override
      public Booking markProposed(
          Long bookingId, Long concertId, BigDecimal gross, String access, String escrow, String tx) {
        Booking b = bookings.get(bookingId);
        b.setOnchainConcertId(concertId);
        b.setGrossUsdc(gross);
        b.setVenueAccessTokenPda(access);
        b.setEscrowPda(escrow);
        b.setOnchainStatus("proposed");
        recorded.add(tx);
        return b;
      }

      @Override
      public Booking markConfirmed(Long bookingId, String text, String hashHex, String tx) {
        Booking b = bookings.get(bookingId);
        b.setContractText(text);
        b.setContractHash(hashHex);
        b.setOnchainStatus("confirmed");
        recorded.add(tx);
        return b;
      }

      @Override
      public Booking markCancelled(Long bookingId, String tx) {
        Booking b = bookings.get(bookingId);
        b.setOnchainStatus("cancelled");
        b.setVenueAccessTokenPda(null);
        b.setEscrowPda(null);
        recorded.add(tx);
        return b;
      }

      @Override
      public BigDecimal sumExpenses(Long concertId) {
        return new BigDecimal("100.00");
      }

      @Override
      public Optional<ConcertSettlement> findSettlementByConcert(Long concertId) {
        return Optional.ofNullable(settlement);
      }

      @Override
      public ConcertSettlement markSettledFromEscrow(
          Long settlementId, Long bookingId, String signer, String tx, String pda) {
        settlement.setOnChainStatus("confirmed");
        settlement.setSettledVia("settle_booking");
        settlement.setSettledByWallet(signer);
        bookings.get(bookingId).setOnchainStatus("settled");
        recorded.add(tx);
        return settlement;
      }

      @Override
      public boolean isRecorded(String txSignature) {
        return recorded.contains(txSignature);
      }
    };
  }
}
