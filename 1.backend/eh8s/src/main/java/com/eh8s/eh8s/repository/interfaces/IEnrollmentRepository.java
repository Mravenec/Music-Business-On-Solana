package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for musician enrollment, subscriptions, and scores.
 */
public interface IEnrollmentRepository {

  /**
   * Inserts an account and returns the stored row.
   *
   * @param account row without id
   * @return stored account
   */
  Account insertAccount(Account account);

  /**
   * Inserts a musician profile.
   *
   * @param profile row without id
   * @return stored profile
   */
  MusicianProfile insertMusician(MusicianProfile profile);

  /**
   * Lists musician profiles.
   *
   * @return profiles
   */
  List<MusicianProfile> findMusicians();

  /**
   * Loads a plan by id.
   *
   * @param planId plan primary key
   * @return plan if present
   */
  Optional<AcademyPlan> findPlan(Long planId);

  /**
   * Inserts a subscription.
   *
   * @param subscription row without id
   * @return stored subscription
   */
  AcademySubscription insertSubscription(AcademySubscription subscription);

  /**
   * Lists subscriptions.
   *
   * @return subscriptions
   */
  List<AcademySubscription> findSubscriptions();

  /**
   * Inserts a weekly Enigma evaluation and updates the musician score.
   *
   * @param evaluation row without id
   * @return stored evaluation
   */
  EnigmaEvaluation insertEvaluation(EnigmaEvaluation evaluation);

  /**
   * Lists evaluations.
   *
   * @return evaluations
   */
  List<EnigmaEvaluation> findEvaluations();

  /**
   * Finds an account by Solana wallet pubkey.
   *
   * @param walletPubkey base58 pubkey
   * @return account if present
   */
  Optional<Account> findAccountByWallet(String walletPubkey);

  /**
   * Finds an account by email (unique key {@code uk_account_email}).
   *
   * @param email account email
   * @return account if present
   */
  Optional<Account> findAccountByEmail(String email);

  /**
   * Updates last_seen_at for an existing account.
   *
   * @param accountId account primary key
   * @return updated account
   */
  Account touchAccount(Long accountId);

  /**
   * Finds a musician profile for an account.
   *
   * @param accountId account primary key
   * @return profile if present
   */
  Optional<MusicianProfile> findMusicianByAccount(Long accountId);

  /**
   * Primary key of an Enigma level by its number (0 = Orientation).
   *
   * @param levelNumber level number 0..5
   * @return enigma_level id if seeded
   */
  Optional<Long> findEnigmaLevelId(int levelNumber);

  /**
   * Lists accounts.
   *
   * @return accounts
   */
  List<Account> findAccounts();

  /**
   * Stores last known WGS84 position for a wallet account.
   *
   * @param accountId account id
   * @param latitude latitude
   * @param longitude longitude
   * @return updated account
   */
  Account updateLocation(Long accountId, java.math.BigDecimal latitude, java.math.BigDecimal longitude);

  /**
   * Forces role (and display) when the platform owner wallet reconnects.
   *
   * @param accountId account id
   * @param role role value (typically owner)
   * @return updated account
   */
  Account updateRole(Long accountId, String role);

  /**
   * Stores a BCrypt hash for email/password login.
   *
   * @param accountId account id
   * @param passwordHash BCrypt hash
   * @return updated account
   */
  Account updatePasswordHash(Long accountId, String passwordHash);
}
