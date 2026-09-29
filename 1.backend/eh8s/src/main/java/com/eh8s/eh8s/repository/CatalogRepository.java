package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Instrument;
import com.eh8s.eh8s.repository.interfaces.ICatalogRepository;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_PLAN;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ENIGMA_LEVEL;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.INSTRUMENT;

/**
 * JOOQ reads for academy catalog tables.
 */
@Repository
public class CatalogRepository implements ICatalogRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public CatalogRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Instrument> findInstruments() {
    return dsl.selectFrom(INSTRUMENT).orderBy(INSTRUMENT.ID).fetchInto(Instrument.class);
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
  public List<AcademyPlan> findPlans() {
    return dsl.selectFrom(ACADEMY_PLAN).orderBy(ACADEMY_PLAN.ID).fetchInto(AcademyPlan.class);
  }
}
