package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.repository.interfaces.IStudioLedgerRepository;
import com.eh8s.eh8s.service.interfaces.IStudioLedgerService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Resolves the signed-in wallet and reads confirmed studio credits for one month.
 */
@Service
public class StudioLedgerService implements IStudioLedgerService {

  private final IStudioLedgerRepository ledgerRepository;

  /**
   * Creates the service.
   *
   * @param ledgerRepository studio credit reads
   */
  public StudioLedgerService(IStudioLedgerRepository ledgerRepository) {
    this.ledgerRepository = ledgerRepository;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<PendingClaim> confirmedClaims(Long accountId, int year, int month) {
    String wallet = walletOf(accountId);
    if (wallet == null) {
      return List.of();
    }
    LocalDateTime start = startOfMonth(year, month);
    return ledgerRepository.confirmedClaims(wallet, start, start.plusMonths(1));
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
