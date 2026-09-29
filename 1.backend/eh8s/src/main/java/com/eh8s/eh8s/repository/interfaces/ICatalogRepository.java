package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Instrument;
import java.util.List;

/**
 * Read catalog rows for the academy (instruments, levels, plans).
 */
public interface ICatalogRepository {

  /**
   * Lists every instrument.
   *
   * @return catalog rows
   */
  List<Instrument> findInstruments();

  /**
   * Lists Enigma levels 0–5.
   *
   * @return level rows
   */
  List<EnigmaLevel> findLevels();

  /**
   * Lists academy plans priced in USDC.
   *
   * @return plan rows
   */
  List<AcademyPlan> findPlans();
}
