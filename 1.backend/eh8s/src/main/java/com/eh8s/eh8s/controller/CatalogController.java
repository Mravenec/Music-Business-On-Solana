package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.ICatalogController;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Instrument;
import com.eh8s.eh8s.service.interfaces.ICatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves academy catalog JSON for the React shell.
 */
@RestController
@RequestMapping("/api")
public class CatalogController implements ICatalogController {

  private final ICatalogService catalogService;

  /**
   * Creates the controller.
   *
   * @param catalogService catalog use cases
   */
  public CatalogController(ICatalogService catalogService) {
    this.catalogService = catalogService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/instruments")
  public List<Instrument> instruments() {
    return catalogService.instruments();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/enigma-levels")
  public List<EnigmaLevel> levels() {
    return catalogService.levels();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/academy-plans")
  public List<AcademyPlan> plans() {
    return catalogService.plans();
  }
}
