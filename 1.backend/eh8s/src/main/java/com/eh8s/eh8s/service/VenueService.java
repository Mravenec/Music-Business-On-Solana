package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertExpense;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ContractType;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.repository.interfaces.IVenueRepository;
import com.eh8s.eh8s.service.interfaces.IBandVaultService;
import com.eh8s.eh8s.service.interfaces.IVenueService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Off-chain settle_concert mirror. On-chain confirm via SettleClaimOnchain.
 */
@Service
public class VenueService implements IVenueService {

  static final BigDecimal EH8S_FEE_RATE = new BigDecimal("0.15");
  static final int SHARE_TOTAL_BPS = 10_000;

  private final IVenueRepository venueRepository;
  private final IBandVaultService bandVaultService;

  /**
   * Creates the service.
   *
   * @param venueRepository venue persistence
   * @param bandVaultService on-chain BandVault weights (SPP split mirror)
   */
  public VenueService(IVenueRepository venueRepository, IBandVaultService bandVaultService) {
    this.venueRepository = venueRepository;
    this.bandVaultService = bandVaultService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Venue> venues() {
    return venueRepository.findVenues();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Venue createVenue(Venue venue) {
    if (venue == null
        || blank(venue.getCode())
        || blank(venue.getName())
        || blank(venue.getCity())
        || blank(venue.getCountryCode())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "code, name, city, and countryCode are required");
    }
    if (venue.getCapacity() == null || venue.getCapacity() < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity must be at least 1");
    }
    if (venue.getSuggestedTicketUsdc() == null || venue.getSuggestedTicketUsdc().signum() < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "suggestedTicketUsdc is required");
    }
    if (venue.getLatitude() == null || venue.getLongitude() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude and longitude are required");
    }
    if (venue.getContractTypeId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contractTypeId is required");
    }
    if (venue.getPinStatus() == null || venue.getPinStatus().isBlank()) {
      venue.setPinStatus("active");
    }
    if (venue.getEh8sRating() == null) {
      venue.setEh8sRating((byte) 5);
    }
    if (venue.getSoundIncluded() == null) {
      venue.setSoundIncluded((byte) 1);
    }
    return venueRepository.insertVenue(venue);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ContractType> contractTypes() {
    return venueRepository.findContractTypes();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Booking> bookings() {
    return venueRepository.findBookings();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Booking createBooking(Booking booking) {
    if (booking.getStatus() == null || booking.getStatus().isBlank()) {
      booking.setStatus("booked");
    }
    if (booking.getPipelineWeek() == null) {
      booking.setPipelineWeek((byte) 1);
    }
    return venueRepository.insertBooking(booking);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Concert> concerts() {
    return venueRepository.findConcerts();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Concert createConcert(Concert concert) {
    if (concert.getStatus() == null || concert.getStatus().isBlank()) {
      concert.setStatus("scheduled");
    }
    return venueRepository.insertConcert(concert);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConcertSettlement settle(Long concertId, BigDecimal grossUsdc) {
    Concert concert =
        venueRepository
            .findConcert(concertId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown concert"));
    ConcertSettlement existing = venueRepository.findSettlementByConcert(concertId).orElse(null);
    if (existing != null && "settled".equals(existing.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Concert already settled");
    }
    BigDecimal expenses =
        venueRepository.findExpenses(concertId).stream()
            .map(ConcertExpense::getAmountUsdc)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    List<SppMemberScore> scores = venueRepository.findScores(concert.getSppCycleId());
    ConcertSettlement settlement = buildSettlement(concertId, grossUsdc, expenses);
    List<PendingClaim> claims =
        bandVaultService.claimsFromVault(
            bandVaultService.vault(concert.getBandId()), settlement.getNetUsdc());
    if (claims.isEmpty()) {
      claims = buildClaims(scores, settlement.getBandPoolUsdc());
    }
    if (claims.isEmpty()) {
      List<Long> memberIds = venueRepository.findBandMemberMusicianIds(concert.getBandId());
      claims = buildEqualMemberClaims(memberIds, settlement.getBandPoolUsdc());
    }
    if (claims.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "No pending claims — add the musician to the band (or close an SPP cycle) before settle");
    }
    ConcertSettlement stored = venueRepository.insertSettlement(settlement);
    for (PendingClaim claim : claims) {
      claim.setConcertSettlementId(stored.getId());
    }
    venueRepository.insertClaims(claims);
    venueRepository.markConcertSettled(concertId);
    return stored;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<PendingClaim> claims(Long settlementId) {
    return venueRepository.findClaims(settlementId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Off-chain Mark claimed is blocked. Claims require a confirmed DevNet claim_royalties
   * signature via {@code POST /api/pending-claims/{id}/claim-royalties/confirm}.
   */
  @Override
  public PendingClaim markClaim(Long claimId) {
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Mark claimed without on-chain confirmation is disabled. Use POST /api/pending-claims/"
            + claimId
            + "/claim-royalties/confirm with a DevNet-confirmed txSignature and claimPda");
  }

  /**
   * Builds settlement amounts: net = gross − expenses; fee 15% of net rounded up to the cent,
   * so the stored pool (and the claims split from it) never exceeds the on-chain pool, which the
   * program computes as net − floor(net × bps / 10000) in 6-decimal units.
   *
   * @param concertId concert id
   * @param gross venue deposit
   * @param expenses summed expense lines
   * @return unsaved settlement
   */
  static ConcertSettlement buildSettlement(Long concertId, BigDecimal gross, BigDecimal expenses) {
    BigDecimal net = gross.subtract(expenses).setScale(2, RoundingMode.HALF_UP);
    BigDecimal fee = net.multiply(EH8S_FEE_RATE).setScale(2, RoundingMode.CEILING);
    BigDecimal pool = net.subtract(fee).setScale(2, RoundingMode.HALF_UP);
    ConcertSettlement settlement = new ConcertSettlement();
    settlement.setConcertId(concertId);
    settlement.setGrossUsdc(gross.setScale(2, RoundingMode.HALF_UP));
    settlement.setExpensesUsdc(expenses.setScale(2, RoundingMode.HALF_UP));
    settlement.setNetUsdc(net);
    settlement.setEh8sFeeUsdc(fee);
    settlement.setBandPoolUsdc(pool);
    settlement.setStatus("settled");
    return settlement;
  }

  /**
   * Splits the band pool by SPP share_bps. Last row receives the remainder.
   *
   * @param scores cycle scores
   * @param pool band pool USDC
   * @return unsaved claims
   */
  static List<PendingClaim> buildClaims(List<SppMemberScore> scores, BigDecimal pool) {
    List<PendingClaim> claims = new ArrayList<>();
    if (scores == null || scores.isEmpty()) {
      return claims;
    }
    boolean anyShare =
        scores.stream().anyMatch(s -> s.getShareBps() != null && s.getShareBps() > 0);
    if (!anyShare) {
      return claims;
    }
    BigDecimal allocated = BigDecimal.ZERO;
    for (int i = 0; i < scores.size(); i++) {
      SppMemberScore score = scores.get(i);
      PendingClaim claim = new PendingClaim();
      claim.setMusicianProfileId(score.getMusicianProfileId());
      claim.setShareBps(score.getShareBps());
      claim.setStatus("pending");
      BigDecimal amount;
      if (i == scores.size() - 1) {
        amount = pool.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
      } else {
        amount =
            pool.multiply(BigDecimal.valueOf(score.getShareBps()))
                .divide(BigDecimal.valueOf(SHARE_TOTAL_BPS), 2, RoundingMode.HALF_UP);
        allocated = allocated.add(amount);
      }
      claim.setAmountUsdc(amount);
      claims.add(claim);
    }
    return claims;
  }

  /**
   * Equal-split claims for band members when SPP scores are missing (no-mock smoke path).
   *
   * @param musicianProfileIds band member profile ids
   * @param pool band pool USDC
   * @return unsaved claims
   */
  static List<PendingClaim> buildEqualMemberClaims(
      List<Long> musicianProfileIds, BigDecimal pool) {
    List<PendingClaim> claims = new ArrayList<>();
    if (musicianProfileIds == null || musicianProfileIds.isEmpty()) {
      return claims;
    }
    int n = musicianProfileIds.size();
    int baseBps = SHARE_TOTAL_BPS / n;
    int remainder = SHARE_TOTAL_BPS - (baseBps * n);
    BigDecimal allocated = BigDecimal.ZERO;
    for (int i = 0; i < n; i++) {
      int bps = baseBps + (i == n - 1 ? remainder : 0);
      PendingClaim claim = new PendingClaim();
      claim.setMusicianProfileId(musicianProfileIds.get(i));
      claim.setShareBps(bps);
      claim.setStatus("pending");
      BigDecimal amount;
      if (i == n - 1) {
        amount = pool.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
      } else {
        amount =
            pool.multiply(BigDecimal.valueOf(bps))
                .divide(BigDecimal.valueOf(SHARE_TOTAL_BPS), 2, RoundingMode.HALF_UP);
        allocated = allocated.add(amount);
      }
      claim.setAmountUsdc(amount);
      claims.add(claim);
    }
    return claims;
  }
}
