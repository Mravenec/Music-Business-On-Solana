package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Instrument;
import com.eh8s.eh8s.repository.interfaces.ICatalogRepository;
import com.eh8s.eh8s.service.interfaces.ICatalogService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Delegates catalog reads to the repository.
 */
@Service
public class CatalogService implements ICatalogService {

  private final ICatalogRepository catalogRepository;

  /**
   * Creates the service.
   *
   * @param catalogRepository catalog persistence
   */
  public CatalogService(ICatalogRepository catalogRepository) {
    this.catalogRepository = catalogRepository;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Instrument> instruments() {
    return catalogRepository.findInstruments();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EnigmaLevel> levels() {
    return catalogRepository.findLevels();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<AcademyPlan> plans() {
    return catalogRepository.findPlans();
  }
}
