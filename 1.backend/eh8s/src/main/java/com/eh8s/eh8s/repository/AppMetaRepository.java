package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.AppMeta;
import com.eh8s.eh8s.repository.interfaces.IAppMetaRepository;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.APP_META;

/**
 * JOOQ persistence for {@code app_meta}.
 */
@Repository
public class AppMetaRepository implements IAppMetaRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context bound to MariaDB
   */
  public AppMetaRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<AppMeta> findByKey(String metaKey) {
    return dsl.selectFrom(APP_META)
        .where(APP_META.META_KEY.eq(metaKey))
        .fetchOptionalInto(AppMeta.class);
  }
}
