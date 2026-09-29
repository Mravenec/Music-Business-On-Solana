package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.AppMeta;
import java.util.Optional;

/**
 * Persistence for application metadata rows (JOOQ POJOs).
 */
public interface IAppMetaRepository {

  /**
   * Load one metadata row by key.
   *
   * @param metaKey primary key
   * @return the row, or empty if missing
   */
  Optional<AppMeta> findByKey(String metaKey);
}
