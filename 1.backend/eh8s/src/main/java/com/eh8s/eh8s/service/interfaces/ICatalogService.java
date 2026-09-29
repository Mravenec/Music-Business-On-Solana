package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Instrument;
import java.util.List;

/**
 * Academy catalog use cases.
 */
public interface ICatalogService {

  /**
   * @return instruments
   */
  List<Instrument> instruments();

  /**
   * @return Enigma levels
   */
  List<EnigmaLevel> levels();

  /**
   * @return USDC subscription plans
   */
  List<AcademyPlan> plans();
}
