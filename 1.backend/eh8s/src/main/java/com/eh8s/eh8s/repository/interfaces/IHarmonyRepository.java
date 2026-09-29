package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.BandMatchSuggestion;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Instrument;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for HARMONY band match suggestions.
 */
public interface IHarmonyRepository {

  /**
   * Loads an account.
   *
   * @param accountId account id
   * @return account when present
   */
  Optional<Account> findAccount(Long accountId);

  /**
   * Resolves the musician profile owned by an account.
   *
   * @param accountId account id
   * @return profile id when the account is a musician
   */
  Optional<Long> findProfileIdByAccount(Long accountId);

  /**
   * Loads a band.
   *
   * @param bandId band id
   * @return band when present
   */
  Optional<Band> findBand(Long bandId);

  /**
   * Current members of a band.
   *
   * @param bandId band id
   * @return member profiles
   */
  List<MusicianProfile> findMembers(Long bandId);

  /**
   * Musicians who are not in the band yet.
   *
   * @param bandId band id
   * @return candidate profiles
   */
  List<MusicianProfile> findCandidates(Long bandId);

  /**
   * Instrument catalog.
   *
   * @return instruments
   */
  List<Instrument> findInstruments();

  /**
   * Enigma level ladder.
   *
   * @return levels
   */
  List<EnigmaLevel> findLevels();

  /**
   * Inserts or refreshes one suggestion (unique per band and musician). Status is kept on refresh.
   *
   * @param suggestion scored suggestion
   */
  void upsertSuggestion(BandMatchSuggestion suggestion);

  /**
   * Removes suggestions for musicians who have since joined the band.
   *
   * @param bandId band id
   */
  void deleteMemberSuggestions(Long bandId);

  /**
   * Stored suggestions for a band, best score first.
   *
   * @param bandId band id
   * @return suggestions
   */
  List<BandMatchSuggestion> findSuggestions(Long bandId);
}
