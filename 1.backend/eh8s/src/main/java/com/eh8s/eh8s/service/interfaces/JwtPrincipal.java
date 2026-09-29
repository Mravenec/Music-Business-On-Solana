package com.eh8s.eh8s.service.interfaces;

/**
 * Subject extracted from a valid JWT.
 *
 * @param accountId account primary key
 * @param email account email
 */
public record JwtPrincipal(Long accountId, String email) {}
