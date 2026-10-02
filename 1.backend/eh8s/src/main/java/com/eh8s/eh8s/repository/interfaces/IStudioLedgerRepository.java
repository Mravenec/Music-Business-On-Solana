package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartner;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartnerAllocation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reads studio credits already stored for one wallet, and the owner's partner books.
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
   * Confirmed show claims for a band with exactly one member.
   *
   * @param walletPubkey claimer wallet
   * @param start inclusive start
   * @param end exclusive end
   * @return matching claims
   */
  List<PendingClaim> soloClaims(String walletPubkey, LocalDateTime start, LocalDateTime end);

  /**
   * Confirmed show claims for a band that does not have exactly one member.
   *
   * @param walletPubkey claimer wallet
   * @param start inclusive start
   * @param end exclusive end
   * @return matching claims
   */
  List<PendingClaim> bandClaims(String walletPubkey, LocalDateTime start, LocalDateTime end);

  /**
   * Confirmed settlements whose venue wallet matches, with expenses greater than zero.
   *
   * @param walletPubkey venue wallet
   * @param start inclusive start
   * @param end exclusive end
   * @return matching settlements
   */
  List<ConcertSettlement> venueExpenses(String walletPubkey, LocalDateTime start, LocalDateTime end);

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

  /**
   * Active partners, ordered by name.
   *
   * @return active partner rows
   */
  List<StudioPartner> activePartners();

  /**
   * Every partner, including those who have left, ordered by id.
   *
   * @return all partner rows
   */
  List<StudioPartner> allPartners();

  /**
   * One partner by wallet.
   *
   * @param walletPubkey partner wallet
   * @return the row when it exists
   */
  Optional<StudioPartner> findPartnerByWallet(String walletPubkey);

  /**
   * One partner by id.
   *
   * @param partnerId partner id
   * @return the row when it exists
   */
  Optional<StudioPartner> findPartner(Long partnerId);

  /**
   * Sum of share_bps for active partners, optionally skipping one id.
   *
   * @param exceptPartnerId partner to leave out, or null
   * @return the sum, or zero
   */
  int activeShareBps(Long exceptPartnerId);

  /**
   * Inserts a partner and opens their share window at the current time.
   *
   * @param walletPubkey wallet
   * @param displayName name shown to the owner
   * @param shareBps share of studio fees, 1 through 10000
   * @return the stored row
   */
  StudioPartner insertPartner(String walletPubkey, String displayName, int shareBps);

  /**
   * Updates name and share. A partner who was inactive starts a new window from now.
   *
   * @param partnerId partner id
   * @param displayName name shown to the owner
   * @param shareBps share of studio fees
   * @return the stored row
   */
  StudioPartner updatePartner(Long partnerId, String displayName, int shareBps);

  /**
   * Marks a partner inactive and closes their window so later fees are not theirs.
   *
   * @param partnerId partner id
   * @return the stored row
   */
  StudioPartner deactivatePartner(Long partnerId);

  /**
   * Confirmed show fees recorded in the range.
   *
   * @param start inclusive start
   * @param end exclusive end
   * @return settlements with a studio fee
   */
  List<ConcertSettlement> showFees(LocalDateTime start, LocalDateTime end);

  /**
   * Confirmed academy treasury amounts recorded in the range.
   *
   * @param start inclusive start
   * @param end exclusive end
   * @return subscriptions with a treasury amount
   */
  List<AcademySubscription> academyFees(LocalDateTime start, LocalDateTime end);

  /**
   * Confirmed sync-license studio cuts recorded in the range.
   *
   * @param start inclusive start
   * @param end exclusive end
   * @return deals with a studio cut
   */
  List<SyncLicenseDeal> syncFees(LocalDateTime start, LocalDateTime end);

  /**
   * Removes allocation rows for one month so they can be written again.
   *
   * @param year calendar year
   * @param month calendar month, 1 through 12
   */
  void clearAllocations(int year, int month);

  /**
   * Stores one partner's slice of one source for one month.
   *
   * @param partnerId partner id
   * @param year calendar year
   * @param month calendar month
   * @param sourceCode show_fee, academy, or sync
   * @param amountUsdc that partner's slice
   */
  void insertAllocation(Long partnerId, int year, int month, String sourceCode, BigDecimal amountUsdc);

  /**
   * Allocation rows for one month.
   *
   * @param year calendar year
   * @param month calendar month
   * @return every partner's rows for that month
   */
  List<StudioPartnerAllocation> allocations(int year, int month);

  /**
   * Allocation rows for one wallet in one month.
   *
   * @param walletPubkey partner wallet
   * @param year calendar year
   * @param month calendar month
   * @return that wallet's rows
   */
  List<StudioPartnerAllocation> allocationsForWallet(String walletPubkey, int year, int month);
}
