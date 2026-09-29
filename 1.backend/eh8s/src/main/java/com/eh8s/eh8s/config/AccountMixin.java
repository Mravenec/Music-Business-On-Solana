package com.eh8s.eh8s.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Hides BCrypt hashes on JOOQ {@code Account} JSON.
 */
@JsonIgnoreProperties({"passwordHash"})
public abstract class AccountMixin {}
