package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reads studio credits already stored for one wallet.
 */
public interface IStudioLedgerRepository {

  /**
   * Loads one account.
   *
   * @param accountId account id
   * @return the account when it exists
   */
  Optional<Account> findAccount(Long accountId);

  /**
   * Confirmed royalty claims paid to this wallet in the half-open range.
   *
   * @param walletPubkey claimer wallet
   * @param start inclusive start
   * @param end exclusive end
   * @return matching claims
   */
  List<PendingClaim> confirmedClaims(String walletPubkey, LocalDateTime start, LocalDateTime end);

  /**
   * Confirmed instructor shares paid to this wallet in the half-open range.
   *
   * @param walletPubkey instructor account wallet
   * @param start inclusive start
   * @param end exclusive end
   * @return matching subscriptions
   */
  List<AcademySubscription> instructorShares(
      String walletPubkey, LocalDateTime start, LocalDateTime end);
}
