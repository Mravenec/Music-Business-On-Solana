package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import java.util.List;
import java.util.Map;

/**
 * Enrollment, USDC subscription split, and Enigma scores.
 */
public interface IEnrollmentService {

  /**
   * Creates a musician account (wallet_pubkey optional until Epic 7).
   *
   * @param account incoming account
   * @return stored account
   */
  Account createAccount(Account account);

  /**
   * Creates a musician profile at Enigma level 0; any client-sent level is ignored because only
   * the owner or a pedagogical agent changes levels (update_musician_level).
   *
   * @param profile incoming profile (accountId, instrumentId, optional countryCode alpha-3)
   * @return stored profile
   * @throws org.springframework.web.server.ResponseStatusException 400 for a malformed countryCode
   */
  MusicianProfile createMusician(MusicianProfile profile);

  /**
   * @return musician profiles
   */
  List<MusicianProfile> musicians();

  /**
   * Creates a subscription and applies 85% treasury / 15% instructor USDC split.
   *
   * @param subscription incoming subscription
   * @return stored subscription
   */
  AcademySubscription subscribe(AcademySubscription subscription);

  /**
   * @return subscriptions
   */
  List<AcademySubscription> subscriptions();

  /**
   * Stores a weekly score.
   *
   * @param evaluation incoming evaluation
   * @return stored evaluation
   */
  EnigmaEvaluation evaluate(EnigmaEvaluation evaluation);

  /**
   * @return evaluations
   */
  List<EnigmaEvaluation> evaluations();

  /**
   * Issues a one-time login nonce for a wallet.
   *
   * @param walletPubkey base58 pubkey
   * @return {@code walletPubkey}, {@code nonce}, and the {@code message} to sign
   */
  Map<String, Object> challenge(String walletPubkey);

  /**
   * Upserts an account by wallet pubkey after the wallet signs {@link #challenge(String)}.
   *
   * @param request account POJO carrying {@code walletPubkey} and optional {@code displayName},
   *     {@code email}, {@code role}, {@code countryCode}
   * @param signature base58 signature of the challenge message
   * @return non-table session JSON: {@code account} and {@code musicianProfile} POJOs, plus
   *     {@code platformOwner}, {@code studioAdmin}, {@code partner}, {@code protocolFeeBps},
   *     {@code accessToken}
   */
  Map<String, Object> upsertWalletSession(Account request, String signature);

  /**
   * Loads a session by wallet pubkey.
   *
   * @param walletPubkey base58 pubkey
   * @return non-table session JSON (same shape as {@link #upsertWalletSession(Account, String)})
   */
  Map<String, Object> findWalletSession(String walletPubkey);

  /**
   * Loads the caller's session from the JWT account id. Does not require a new wallet signature.
   *
   * @param principal authenticated account
   * @return non-table session JSON (same shape as {@link #upsertWalletSession(Account, String)})
   */
  Map<String, Object> currentSession(JwtPrincipal principal);

  /**
   * Stores geolocation for a signed-in wallet.
   *
   * @param walletPubkey base58 pubkey
   * @param location account POJO carrying {@code lastLat} and {@code lastLng}
   * @return non-table session JSON (same shape as {@link #upsertWalletSession(Account, String)})
   */
  Map<String, Object> updateWalletLocation(String walletPubkey, Account location);

  /**
   * Lists accounts for operator consoles.
   *
   * @return accounts
   */
  List<Account> accounts();
}
