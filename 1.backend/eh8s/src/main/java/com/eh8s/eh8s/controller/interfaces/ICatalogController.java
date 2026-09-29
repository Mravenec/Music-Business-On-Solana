package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Instrument;
import java.util.List;

/**
 * HTTP contract for academy catalog resources.
 */
public interface ICatalogController {

  /**
   * @return instruments
   */
  List<Instrument> instruments();

  /**
   * @return Enigma levels
   */
  List<EnigmaLevel> levels();

  /**
   * @return academy plans
   */
  List<AcademyPlan> plans();
}
