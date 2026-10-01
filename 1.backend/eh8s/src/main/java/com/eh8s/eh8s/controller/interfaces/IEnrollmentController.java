package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import java.util.Map;

/**
 * HTTP contract for academy enrollment.
 */
public interface IEnrollmentController {

  /**
   * Creates an account.
   *
   * @param account body
   * @return stored account
   */
  Account createAccount(Account account);

  /**
   * Creates a musician profile at Enigma level 0 (level in the body is ignored).
   *
   * @param profile body: accountId, instrumentId, optional countryCode (ISO-3166 alpha-3)
   * @return stored profile
   */
  MusicianProfile createMusician(MusicianProfile profile);

  /**
   * @return musicians
   */
  List<MusicianProfile> musicians();

  /**
   * Creates a subscription with USDC split.
   *
   * @param subscription body
   * @return stored subscription
   */
  AcademySubscription subscribe(AcademySubscription subscription);

  /**
   * @return subscriptions
   */
  List<AcademySubscription> subscriptions();

  /**
   * Stores an Enigma evaluation.
   *
   * @param evaluation body
   * @return stored evaluation
   */
  EnigmaEvaluation evaluate(EnigmaEvaluation evaluation);

  /**
   * @return evaluations
   */
  List<EnigmaEvaluation> evaluations();

  /**
   * Issues a one-time nonce the wallet must sign.
   *
   * @param body JSON ({@code walletPubkey})
   * @return nonce and message
   */
  Map<String, Object> challenge(Map<String, Object> body);

  /**
   * Upserts session after the wallet signs the nonce.
   *
   * @param body JSON ({@code walletPubkey}, {@code signature}, optional {@code displayName},
   *     {@code email}, {@code role}, {@code countryCode})
   * @return session JSON
   */
  Map<String, Object> upsertWalletSession(Map<String, Object> body);

  /**
   * Loads session by wallet.
   *
   * @param walletPubkey pubkey
   * @return session JSON
   */
  Map<String, Object> findWalletSession(String walletPubkey);

  /**
   * Loads the signed-in studio session from the bearer token. No wallet signature.
   *
   * @param principal JWT account
   * @return session JSON
   */
  Map<String, Object> currentSession(JwtPrincipal principal);

  /**
   * Stores geolocation for a wallet session.
   *
   * @param walletPubkey pubkey
   * @param location account JSON ({@code lastLat}, {@code lastLng})
   * @return session JSON
   */
  Map<String, Object> updateWalletLocation(String walletPubkey, Account location);

  /**
   * Lists accounts.
   *
   * @return accounts
   */
  List<Account> accounts();
}
