package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartner;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartnerAllocation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.IStudioLedgerRepository;
import com.eh8s.eh8s.service.course.CourseRules;
import com.eh8s.eh8s.service.interfaces.IStudioLedgerService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the signed-in wallet and keeps partner allocations equal to recorded studio fees.
 */
@Service
public class StudioLedgerService implements IStudioLedgerService {

  private static final Pattern WALLET = Pattern.compile("^[1-9A-HJ-NP-Za-km-z]{32,44}$");
  private static final BigDecimal BPS = BigDecimal.valueOf(10000);

  private final IStudioLedgerRepository ledgerRepository;
  private final IChainConfigRepository chainConfigRepository;

  /**
   * Creates the service.
   *
   * @param ledgerRepository studio credit and partner persistence
   * @param chainConfigRepository active chain config, for the owner wallet
   */
  public StudioLedgerService(
      IStudioLedgerRepository ledgerRepository, IChainConfigRepository chainConfigRepository) {
    this.ledgerRepository = ledgerRepository;
    this.chainConfigRepository = chainConfigRepository;
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
    StudioPartner existing = ledgerRepository.findPartnerByWallet(wallet).orElse(null);
    Long exceptId = existing == null ? null : existing.getId();
    if (ledgerRepository.activeShareBps(exceptId) + share > 10000) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active shares cannot pass 100%");
    }
    if (existing == null) {
      return ledgerRepository.insertPartner(wallet, name, share);
    }
    return ledgerRepository.updatePartner(existing.getId(), name, share);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public StudioPartner deactivatePartner(Long partnerId) {
    if (ledgerRepository.findPartner(partnerId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That partner is not on the books");
    }
    return ledgerRepository.deactivatePartner(partnerId);
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
    List<StudioPartner> partners = ledgerRepository.activePartners();
    BigDecimal shows =
        ledgerRepository.showFees(start, end).stream()
            .map(ConcertSettlement::getEh8sFeeUsdc)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal academy =
        ledgerRepository.academyFees(start, end).stream()
            .map(AcademySubscription::getTreasuryUsdc)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal sync =
        ledgerRepository.syncFees(start, end).stream()
            .map(SyncLicenseDeal::getEh8sUsdc)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    ledgerRepository.clearAllocations(year, month);
    writeSource(partners, year, month, "show_fee", shows);
    writeSource(partners, year, month, "academy", academy);
    writeSource(partners, year, month, "sync", sync);
  }

  private void writeSource(
      List<StudioPartner> partners, int year, int month, String source, BigDecimal pool) {
    if (partners.isEmpty() || pool.signum() <= 0) {
      return;
    }
    int assigned = partners.stream().mapToInt(StudioPartner::getShareBps).sum();
    BigDecimal assignedPool =
        pool.multiply(BigDecimal.valueOf(assigned)).divide(BPS, 2, RoundingMode.HALF_UP);
    BigDecimal used = BigDecimal.ZERO;
    for (int i = 0; i < partners.size(); i++) {
      StudioPartner partner = partners.get(i);
      BigDecimal part;
      if (i == partners.size() - 1) {
        part = assignedPool.subtract(used);
      } else {
        part =
            pool.multiply(BigDecimal.valueOf(partner.getShareBps()))
                .divide(BPS, 2, RoundingMode.DOWN);
        used = used.add(part);
      }
      if (part.signum() > 0) {
        ledgerRepository.insertAllocation(partner.getId(), year, month, source, part);
      }
    }
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
