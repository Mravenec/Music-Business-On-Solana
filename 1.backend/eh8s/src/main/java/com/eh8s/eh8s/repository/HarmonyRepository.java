package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.BandMatchSuggestion;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Instrument;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.repository.interfaces.IHarmonyRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND_MATCH_SUGGESTION;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND_MEMBER;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ENIGMA_LEVEL;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.INSTRUMENT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_PROFILE;

/**
 * JOOQ persistence for HARMONY band match suggestions.
 */
@Repository
public class HarmonyRepository implements IHarmonyRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public HarmonyRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Account> findAccount(Long accountId) {
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOptionalInto(Account.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Long> findProfileIdByAccount(Long accountId) {
    return dsl.select(MUSICIAN_PROFILE.ID)
        .from(MUSICIAN_PROFILE)
        .where(MUSICIAN_PROFILE.ACCOUNT_ID.eq(accountId))
        .fetchOptional(MUSICIAN_PROFILE.ID);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Band> findBand(Long bandId) {
    return dsl.selectFrom(BAND).where(BAND.ID.eq(bandId)).fetchOptionalInto(Band.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<MusicianProfile> findMembers(Long bandId) {
    return dsl.select(MUSICIAN_PROFILE.fields())
        .from(MUSICIAN_PROFILE)
        .join(BAND_MEMBER)
        .on(BAND_MEMBER.MUSICIAN_PROFILE_ID.eq(MUSICIAN_PROFILE.ID))
        .where(BAND_MEMBER.BAND_ID.eq(bandId))
        .orderBy(MUSICIAN_PROFILE.ID)
        .fetchInto(MusicianProfile.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<MusicianProfile> findCandidates(Long bandId) {
    return dsl.selectFrom(MUSICIAN_PROFILE)
        .where(
            MUSICIAN_PROFILE.ID.notIn(
                dsl.select(BAND_MEMBER.MUSICIAN_PROFILE_ID)
                    .from(BAND_MEMBER)
                    .where(BAND_MEMBER.BAND_ID.eq(bandId))))
        .orderBy(MUSICIAN_PROFILE.ID)
        .fetchInto(MusicianProfile.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Instrument> findInstruments() {
    return dsl.selectFrom(INSTRUMENT).fetchInto(Instrument.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EnigmaLevel> findLevels() {
    return dsl.selectFrom(ENIGMA_LEVEL).orderBy(ENIGMA_LEVEL.LEVEL_NUMBER).fetchInto(EnigmaLevel.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void upsertSuggestion(BandMatchSuggestion s) {
    LocalDateTime now = LocalDateTime.now();
    dsl.insertInto(BAND_MATCH_SUGGESTION)
        .set(BAND_MATCH_SUGGESTION.BAND_ID, s.getBandId())
        .set(BAND_MATCH_SUGGESTION.MUSICIAN_PROFILE_ID, s.getMusicianProfileId())
        .set(BAND_MATCH_SUGGESTION.SCORE, s.getScore())
        .set(BAND_MATCH_SUGGESTION.INSTRUMENT_POINTS, s.getInstrumentPoints())
        .set(BAND_MATCH_SUGGESTION.LEVEL_POINTS, s.getLevelPoints())
        .set(BAND_MATCH_SUGGESTION.COUNTRY_POINTS, s.getCountryPoints())
        .set(BAND_MATCH_SUGGESTION.GENRE_POINTS, s.getGenrePoints())
        .set(BAND_MATCH_SUGGESTION.REASON, s.getReason())
        .set(BAND_MATCH_SUGGESTION.AI_RATIONALE, s.getAiRationale())
        .set(BAND_MATCH_SUGGESTION.CREATED_AT, now)
        .onDuplicateKeyUpdate()
        .set(BAND_MATCH_SUGGESTION.SCORE, s.getScore())
        .set(BAND_MATCH_SUGGESTION.INSTRUMENT_POINTS, s.getInstrumentPoints())
        .set(BAND_MATCH_SUGGESTION.LEVEL_POINTS, s.getLevelPoints())
        .set(BAND_MATCH_SUGGESTION.COUNTRY_POINTS, s.getCountryPoints())
        .set(BAND_MATCH_SUGGESTION.GENRE_POINTS, s.getGenrePoints())
        .set(BAND_MATCH_SUGGESTION.REASON, s.getReason())
        .set(BAND_MATCH_SUGGESTION.AI_RATIONALE, s.getAiRationale())
        .set(BAND_MATCH_SUGGESTION.CREATED_AT, now)
        .execute();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteMemberSuggestions(Long bandId) {
    dsl.deleteFrom(BAND_MATCH_SUGGESTION)
        .where(
            BAND_MATCH_SUGGESTION
                .BAND_ID
                .eq(bandId)
                .and(
                    BAND_MATCH_SUGGESTION.MUSICIAN_PROFILE_ID.in(
                        dsl.select(BAND_MEMBER.MUSICIAN_PROFILE_ID)
                            .from(BAND_MEMBER)
                            .where(BAND_MEMBER.BAND_ID.eq(bandId)))))
        .execute();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<BandMatchSuggestion> findSuggestions(Long bandId) {
    return dsl.selectFrom(BAND_MATCH_SUGGESTION)
        .where(BAND_MATCH_SUGGESTION.BAND_ID.eq(bandId))
        .orderBy(BAND_MATCH_SUGGESTION.SCORE.desc(), BAND_MATCH_SUGGESTION.MUSICIAN_PROFILE_ID)
        .fetchInto(BandMatchSuggestion.class);
  }
}
