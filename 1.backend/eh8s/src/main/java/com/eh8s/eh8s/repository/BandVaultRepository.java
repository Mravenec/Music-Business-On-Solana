package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND_MEMBER;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_PROFILE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.SPP_CYCLE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.SPP_MEMBER_SCORE;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.repository.interfaces.IBandVaultRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/**
 * JOOQ persistence for BandVault activation and SPP weight sync.
 */
@Repository
public class BandVaultRepository implements IBandVaultRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public BandVaultRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Band> findBand(Long bandId) {
    return dsl.selectFrom(BAND).where(BAND.ID.eq(bandId)).fetchOptionalInto(Band.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Band> findBandByConcert(Long concertId) {
    return dsl.select(BAND.fields())
        .from(BAND)
        .join(CONCERT)
        .on(CONCERT.BAND_ID.eq(BAND.ID))
        .where(CONCERT.ID.eq(concertId))
        .fetchOptionalInto(Band.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<MusicianProfile> findMemberProfiles(Long bandId) {
    return dsl.select(MUSICIAN_PROFILE.fields())
        .from(BAND_MEMBER)
        .join(MUSICIAN_PROFILE)
        .on(MUSICIAN_PROFILE.ID.eq(BAND_MEMBER.MUSICIAN_PROFILE_ID))
        .where(BAND_MEMBER.BAND_ID.eq(bandId))
        .orderBy(BAND_MEMBER.ID)
        .fetchInto(MusicianProfile.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<MusicianProfile> findProfilesByIds(List<Long> musicianProfileIds) {
    if (musicianProfileIds == null || musicianProfileIds.isEmpty()) {
      return List.of();
    }
    return dsl.selectFrom(MUSICIAN_PROFILE)
        .where(MUSICIAN_PROFILE.ID.in(musicianProfileIds))
        .fetchInto(MusicianProfile.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<Account> findAccountsByIds(List<Long> accountIds) {
    if (accountIds == null || accountIds.isEmpty()) {
      return List.of();
    }
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.in(accountIds)).fetchInto(Account.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<SppMemberScore> findLatestClosedScores(Long bandId) {
    Long cycleId =
        dsl.select(SPP_CYCLE.ID)
            .from(SPP_CYCLE)
            .where(SPP_CYCLE.BAND_ID.eq(bandId).and(SPP_CYCLE.STATUS.eq("closed")))
            .orderBy(SPP_CYCLE.ID.desc())
            .limit(1)
            .fetchOptional(SPP_CYCLE.ID)
            .orElse(null);
    if (cycleId == null) {
      return List.of();
    }
    return dsl.selectFrom(SPP_MEMBER_SCORE)
        .where(SPP_MEMBER_SCORE.SPP_CYCLE_ID.eq(cycleId))
        .orderBy(SPP_MEMBER_SCORE.ID)
        .fetchInto(SppMemberScore.class);
  }

  /** {@inheritDoc} */
  @Override
  public Band markVaultActivated(
      Long bandId, String bandVaultPda, String txSignature, String weightsJson) {
    LocalDateTime now = LocalDateTime.now();
    dsl.update(BAND)
        .set(BAND.BAND_VAULT_PDA, bandVaultPda)
        .set(BAND.VAULT_TX_SIGNATURE, txSignature)
        .set(BAND.VAULT_ACTIVATED_AT, now)
        .set(BAND.SPP_WEIGHTS_JSON, weightsJson)
        .set(BAND.WEIGHTS_SYNCED_AT, now)
        .where(BAND.ID.eq(bandId))
        .execute();
    return requireBand(bandId);
  }

  /** {@inheritDoc} */
  @Override
  public Band markWeightsSynced(Long bandId, String txSignature, String weightsJson) {
    dsl.update(BAND)
        .set(BAND.SPP_WEIGHTS_JSON, weightsJson)
        .set(BAND.WEIGHTS_TX_SIGNATURE, txSignature)
        .set(BAND.WEIGHTS_SYNCED_AT, LocalDateTime.now())
        .where(BAND.ID.eq(bandId))
        .execute();
    return requireBand(bandId);
  }

  private Band requireBand(Long bandId) {
    return findBand(bandId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update failed"));
  }
}
