package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartner;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartnerAllocation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.PartnerShare;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.StudioRoleGrant;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.IRoleApplicationRepository;
import com.eh8s.eh8s.repository.interfaces.IStudioLedgerRepository;
import com.eh8s.eh8s.repository.interfaces.IStudioPowerOnchainRepository;
import com.eh8s.eh8s.service.course.CourseRules;
import com.eh8s.eh8s.service.interfaces.IRoleApplicationService;
import com.eh8s.eh8s.service.interfaces.IStudioLedgerService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the signed-in wallet and shares each recorded fee with partners already on the books that day.
 */
@Service
public class StudioLedgerService implements IStudioLedgerService {

  private static final Pattern WALLET = Pattern.compile("^[1-9A-HJ-NP-Za-km-z]{32,44}$");
  private static final BigDecimal BPS = BigDecimal.valueOf(10000);

  private final IStudioLedgerRepository ledgerRepository;
  private final IChainConfigRepository chainConfigRepository;
  private final IRoleApplicationRepository roleApplicationRepository;
  private final IRoleApplicationService roleApplicationService;
  private final IStudioPowerOnchainRepository onchainRepository;

  /**
   * Creates the service.
   *
   * @param ledgerRepository studio credit and partner persistence
   * @param chainConfigRepository active chain config, for the owner wallet
   * @param roleApplicationRepository studio admin membership
   * @param roleApplicationService partner role grant that follows the books
   * @param onchainRepository confirmed on-chain studio role / partner share mirrors
   */
  public StudioLedgerService(
      IStudioLedgerRepository ledgerRepository,
      IChainConfigRepository chainConfigRepository,
      IRoleApplicationRepository roleApplicationRepository,
      IRoleApplicationService roleApplicationService,
      IStudioPowerOnchainRepository onchainRepository) {
    this.ledgerRepository = ledgerRepository;
    this.chainConfigRepository = chainConfigRepository;
    this.roleApplicationRepository = roleApplicationRepository;
    this.roleApplicationService = roleApplicationService;
    this.onchainRepository = onchainRepository;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isOwner(Long accountId) {
    Account account = ledgerRepository.findAccount(accountId).orElse(null);
    if (account == null) {
      return false;
    }
    String ownerWallet =
        chainConfigRepository.findActive().map(ChainConfig::getOwnerWalletPubkey).orElse(null);
    return CourseRules.isOwner(account.getRole(), account.getWalletPubkey(), ownerWallet);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isBooksEditor(Long accountId) {
    if (isOwner(accountId)) {
      return true;
    }
    String wallet = walletOf(accountId);
    StudioRoleGrant chain =
        wallet == null ? null : onchainRepository.findRoleGrant(wallet).orElse(null);
    if (chain != null) {
      // A confirmed on-chain grant or revoke decides; the MariaDB role is only the fallback.
      if (chain.getRevokedAt() == null) {
        roleApplicationService.ensureStudioAdmin(wallet);
        return true;
      }
      roleApplicationService.clearStudioAdmin(wallet);
      return false;
    }
    return roleApplicationRepository
        .findMembership(accountId, StudioAccess.STUDIO_ADMIN)
        .isPresent();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<PendingClaim> soloClaims(Long accountId, int year, int month) {
    String wallet = walletOf(accountId);
    if (wallet == null) {
      return List.of();
    }
    LocalDateTime start = startOfMonth(year, month);
    return ledgerRepository.soloClaims(wallet, start, start.plusMonths(1));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<PendingClaim> bandClaims(Long accountId, int year, int month) {
    String wallet = walletOf(accountId);
    if (wallet == null) {
      return List.of();
    }
    LocalDateTime start = startOfMonth(year, month);
    return ledgerRepository.bandClaims(wallet, start, start.plusMonths(1));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ConcertSettlement> venueExpenses(Long accountId, int year, int month) {
    String wallet = walletOf(accountId);
    if (wallet == null) {
      return List.of();
    }
    LocalDateTime start = startOfMonth(year, month);
    return ledgerRepository.venueExpenses(wallet, start, start.plusMonths(1));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<AcademySubscription> instructorShares(Long accountId, int year, int month) {
    String wallet = walletOf(accountId);
    if (wallet == null) {
      return List.of();
    }
    LocalDateTime start = startOfMonth(year, month);
    return ledgerRepository.instructorShares(wallet, start, start.plusMonths(1));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<StudioPartnerAllocation> myAllocations(Long accountId, int year, int month) {
    refresh(year, month);
    String wallet = walletOf(accountId);
    if (wallet == null) {
      return List.of();
    }
    return ledgerRepository.allocationsForWallet(wallet, year, month);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<StudioPartner> partners() {
    repairFromChain();
    return ledgerRepository.activePartners();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public StudioPartner savePartner(StudioPartner draft) {
    if (draft == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, wallet, and share are required");
    }
    String wallet = draft.getWalletPubkey() == null ? "" : draft.getWalletPubkey().trim();
    String name = draft.getDisplayName() == null ? "" : draft.getDisplayName().trim();
    Integer share = draft.getShareBps();
    if (!WALLET.matcher(wallet).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a Solana wallet");
    }
    if (name.isBlank() || name.length() > 120) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the partner's name");
    }
    if (share == null || share < 1 || share > 10000) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Share must be between 0.01% and 100%");
    }
    if (onchainRepository.findPartnerShare(wallet).isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "This partner's share is signed on-chain. Sign set_partner_share with the principal wallet to change it.");
    }
    StudioPartner existing = ledgerRepository.findPartnerByWallet(wallet).orElse(null);
    Long exceptId = existing == null ? null : existing.getId();
    if (ledgerRepository.activeShareBps(exceptId) + share > 10000) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active shares cannot pass 100%");
    }
    StudioPartner saved =
        existing == null
            ? ledgerRepository.insertPartner(wallet, name, share)
            : ledgerRepository.updatePartner(existing.getId(), name, share);
    roleApplicationService.ensurePartner(wallet);
    return saved;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public StudioPartner deactivatePartner(Long partnerId) {
    StudioPartner current = ledgerRepository.findPartner(partnerId).orElse(null);
    if (current == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That partner is not on the books");
    }
    PartnerShare chain = onchainRepository.findPartnerShare(current.getWalletPubkey()).orElse(null);
    if (chain != null && isLive(chain, LocalDateTime.now())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "This partner's share is signed on-chain. Sign set_partner_share with 0 bps to remove it.");
    }
    StudioPartner removed = ledgerRepository.deactivatePartner(partnerId);
    roleApplicationService.clearPartner(removed.getWalletPubkey());
    return removed;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ConcertSettlement> showFees(int year, int month) {
    refresh(year, month);
    LocalDateTime start = startOfMonth(year, month);
    return ledgerRepository.showFees(start, start.plusMonths(1));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<AcademySubscription> academyFees(int year, int month) {
    refresh(year, month);
    LocalDateTime start = startOfMonth(year, month);
    return ledgerRepository.academyFees(start, start.plusMonths(1));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SyncLicenseDeal> syncFees(int year, int month) {
    refresh(year, month);
    LocalDateTime start = startOfMonth(year, month);
    return ledgerRepository.syncFees(start, start.plusMonths(1));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<StudioPartnerAllocation> allocations(int year, int month) {
    refresh(year, month);
    return ledgerRepository.allocations(year, month);
  }

  private void refresh(int year, int month) {
    LocalDateTime start = startOfMonth(year, month);
    LocalDateTime end = start.plusMonths(1);
    repairFromChain();
    List<StudioPartner> partners = ledgerRepository.allPartners();
    Set<Long> chainIds = chainPartnerIds(partners);
    ledgerRepository.clearAllocations(year, month);
    writeSource(
        partners,
        chainIds,
        year,
        month,
        "show_fee",
        ledgerRepository.showFees(start, end).stream()
            .map(row -> new DatedFee(row.getSettledAt(), row.getEh8sFeeUsdc()))
            .toList());
    writeSource(
        partners,
        chainIds,
        year,
        month,
        "academy",
        ledgerRepository.academyFees(start, end).stream()
            .map(row -> new DatedFee(row.getPaidAt(), row.getTreasuryUsdc()))
            .toList());
    writeSource(
        partners,
        chainIds,
        year,
        month,
        "sync",
        ledgerRepository.syncFees(start, end).stream()
            .map(row -> new DatedFee(row.getPaidAt(), row.getEh8sUsdc()))
            .toList());
  }

  /**
   * Makes the MariaDB partner rows agree with every confirmed on-chain PartnerShare mirror. The
   * chain wins: share, window and active flag are copied from the mirror, and a wallet that only
   * exists on chain gets its row created. Rows already in agreement are not written.
   */
  private void repairFromChain() {
    for (PartnerShare mirror : onchainRepository.findPartnerShares()) {
      syncPartnerFromChain(mirror);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public StudioPartner syncPartnerFromChain(PartnerShare mirror) {
    if (mirror == null || mirror.getWalletPubkey() == null) {
      return null;
    }
    String wallet = mirror.getWalletPubkey();
    StudioPartner existing = ledgerRepository.findPartnerByWallet(wallet).orElse(null);
    int bps = mirror.getShareBps() == null ? 0 : mirror.getShareBps();
    if (existing == null && bps == 0) {
      return null;
    }
    boolean live = isLive(mirror, LocalDateTime.now());
    StudioPartner row = existing;
    if (existing == null
        || !Objects.equals(existing.getShareBps(), bps)
        || !Objects.equals(existing.getStartedAt(), mirror.getStartedAt())
        || !Objects.equals(existing.getEndedAt(), mirror.getEndedAt())
        || (existing.getActive() != null && (existing.getActive() == (byte) 1) != live)) {
      row =
          ledgerRepository.syncPartnerFromChain(
              wallet,
              existing == null ? shortName(wallet) : existing.getDisplayName(),
              bps,
              mirror.getStartedAt(),
              mirror.getEndedAt(),
              live);
    }
    if (live) {
      roleApplicationService.ensurePartner(wallet);
    } else {
      roleApplicationService.clearPartner(wallet);
    }
    return row;
  }

  /**
   * A mirror window is live until it ends; a window that is scheduled to end later still pays.
   *
   * @param mirror on-chain share mirror
   * @param now current time
   * @return whether the partner is still on the books
   */
  static boolean isLive(PartnerShare mirror, LocalDateTime now) {
    return mirror.getEndedAt() == null || mirror.getEndedAt().isAfter(now);
  }

  private Set<Long> chainPartnerIds(List<StudioPartner> partners) {
    Set<String> wallets = new HashSet<>();
    for (PartnerShare mirror : onchainRepository.findPartnerShares()) {
      wallets.add(mirror.getWalletPubkey());
    }
    Set<Long> ids = new HashSet<>();
    for (StudioPartner partner : partners) {
      if (partner.getId() != null && wallets.contains(partner.getWalletPubkey())) {
        ids.add(partner.getId());
      }
    }
    return ids;
  }

  private static String shortName(String wallet) {
    return wallet.length() <= 10
        ? wallet
        : "Partner " + wallet.substring(0, 4) + ".." + wallet.substring(wallet.length() - 4);
  }

  private void writeSource(
      List<StudioPartner> partners,
      Set<Long> chainIds,
      int year,
      int month,
      String source,
      List<DatedFee> fees) {
    Map<Long, BigDecimal> totals = shareOf(partners, chainIds, fees);
    totals.forEach(
        (partnerId, amount) ->
            ledgerRepository.insertAllocation(partnerId, year, month, source, amount));
  }

  /**
   * One recorded fee and the moment it was stored.
   *
   * @param at fee timestamp
   * @param amount studio amount for that fee
   */
  record DatedFee(LocalDateTime at, BigDecimal amount) {}

  /**
   * Gives each partner their own share of each fee whose calendar day falls inside their window.
   * A fee before they joined, or after the day they left, is not theirs. The unassigned percent stays unassigned.
   *
   * @param partners every partner, including those who have left
   * @param fees dated studio amounts
   * @return partner id to the summed slice, skipping zero
   */
  static Map<Long, BigDecimal> shareOf(List<StudioPartner> partners, List<DatedFee> fees) {
    return shareOf(partners, Set.of(), fees);
  }

  /**
   * Same as {@link #shareOf(List, List)}, but a partner whose id is in {@code chainIds} follows the
   * confirmed on-chain window exactly: the fee instant must be at or after the window start and
   * before the exclusive window end (the program's {@code [starts_at, ends_at)} rule).
   *
   * @param partners every partner, including those who have left
   * @param chainIds ids of partners whose window comes from a confirmed on-chain share
   * @param fees dated studio amounts
   * @return partner id to the summed slice, skipping zero
   */
  static Map<Long, BigDecimal> shareOf(
      List<StudioPartner> partners, Set<Long> chainIds, List<DatedFee> fees) {
    Map<Long, BigDecimal> totals = new LinkedHashMap<>();
    if (partners == null || fees == null) {
      return totals;
    }
    for (DatedFee fee : fees) {
      if (fee == null || fee.at == null || fee.amount == null || fee.amount.signum() <= 0) {
        continue;
      }
      for (StudioPartner partner : partners) {
        if (partner.getId() == null || partner.getShareBps() == null) {
          continue;
        }
        boolean inside =
            chainIds.contains(partner.getId())
                ? coversExact(partner, fee.at)
                : covers(partner, fee.at);
        if (!inside) {
          continue;
        }
        BigDecimal part =
            fee.amount
                .multiply(BigDecimal.valueOf(partner.getShareBps()))
                .divide(BPS, 2, RoundingMode.HALF_UP);
        if (part.signum() > 0) {
          totals.merge(partner.getId(), part, BigDecimal::add);
        }
      }
    }
    return totals;
  }

  /**
   * A partner covers a fee when the fee's calendar day is on or after their start day and,
   * if they have left, on or before the day they left.
   *
   * @param partner partner row
   * @param feeAt fee timestamp
   * @return whether that fee is inside the window
   */
  static boolean covers(StudioPartner partner, LocalDateTime feeAt) {
    if (partner.getStartedAt() == null || feeAt == null) {
      return false;
    }
    LocalDate feeDay = feeAt.toLocalDate();
    if (feeDay.isBefore(partner.getStartedAt().toLocalDate())) {
      return false;
    }
    if (partner.getEndedAt() == null) {
      return true;
    }
    return !feeDay.isAfter(partner.getEndedAt().toLocalDate());
  }

  /**
   * On-chain windows are {@code [starts_at, ends_at)}: the instant counts from the start and stops
   * at the end, with no calendar-day rounding.
   *
   * @param partner partner row copied from the on-chain mirror
   * @param feeAt fee timestamp
   * @return whether that fee is inside the on-chain window
   */
  static boolean coversExact(StudioPartner partner, LocalDateTime feeAt) {
    if (partner.getStartedAt() == null || feeAt == null || feeAt.isBefore(partner.getStartedAt())) {
      return false;
    }
    return partner.getEndedAt() == null || feeAt.isBefore(partner.getEndedAt());
  }

  private String walletOf(Long accountId) {
    return ledgerRepository
        .findAccount(accountId)
        .map(Account::getWalletPubkey)
        .filter(value -> value != null && !value.isBlank())
        .orElse(null);
  }

  private static LocalDateTime startOfMonth(int year, int month) {
    return LocalDate.of(year, month, 1).atStartOfDay();
  }
}
