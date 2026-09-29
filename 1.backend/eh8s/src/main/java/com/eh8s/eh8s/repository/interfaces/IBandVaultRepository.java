package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import java.util.List;
import java.util.Optional;

/**
 * JOOQ persistence for the on-chain BandVault (create_band / update_spp_weights) of a band.
 */
public interface IBandVaultRepository {

  /**
   * Finds a band row including its vault columns.
   *
   * @param bandId band id
   * @return band when present
   */
  Optional<Band> findBand(Long bandId);

  /**
   * Finds the band of a concert.
   *
   * @param concertId concert id
   * @return band when the concert exists
   */
  Optional<Band> findBandByConcert(Long concertId);

  /**
   * Lists band member musician profiles in vault order ({@code band_member.id}).
   *
   * @param bandId band id
   * @return profiles in member order
   */
  List<MusicianProfile> findMemberProfiles(Long bandId);

  /**
   * Loads musician profiles by id.
   *
   * @param musicianProfileIds profile ids (empty list returns an empty list)
   * @return profiles found (any order)
   */
  List<MusicianProfile> findProfilesByIds(List<Long> musicianProfileIds);

  /**
   * Loads accounts by id (the wallet public key lives on the account).
   *
   * @param accountIds account ids (empty list returns an empty list)
   * @return accounts found (any order)
   */
  List<Account> findAccountsByIds(List<Long> accountIds);

  /**
   * Latest closed SPP cycle scores for the band (empty when no cycle was closed).
   *
   * @param bandId band id
   * @return scores of the newest closed cycle
   */
  List<SppMemberScore> findLatestClosedScores(Long bandId);

  /**
   * Records a DevNet-confirmed create_band.
   *
   * @param bandId band id
   * @param bandVaultPda BandVault PDA
   * @param txSignature confirmed signature
   * @param weightsJson initial weights JSON (vault member order)
   * @return updated band
   */
  Band markVaultActivated(Long bandId, String bandVaultPda, String txSignature, String weightsJson);

  /**
   * Records a DevNet-confirmed update_spp_weights.
   *
   * @param bandId band id
   * @param txSignature confirmed signature
   * @param weightsJson synced weights JSON (vault member order)
   * @return updated band
   */
  Band markWeightsSynced(Long bandId, String txSignature, String weightsJson);
}
