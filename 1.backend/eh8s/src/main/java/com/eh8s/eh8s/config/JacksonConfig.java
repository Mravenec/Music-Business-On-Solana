package com.eh8s.eh8s.config;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson mixins for generated JOOQ types.
 */
@Configuration
public class JacksonConfig {

  /**
   * Omits {@code passwordHash} from account JSON.
   *
   * @return customizer
   */
  @Bean
  public Jackson2ObjectMapperBuilderCustomizer accountPasswordMixin() {
    return builder -> builder.mixIn(Account.class, AccountMixin.class);
  }
}
