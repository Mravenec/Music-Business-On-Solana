package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.repository.interfaces.IEnrollmentRepository;
import com.eh8s.eh8s.service.interfaces.IAuthService;
import com.eh8s.eh8s.service.interfaces.IJwtService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * BCrypt credential checks and JWT issue for email/password auth.
 */
@Service
public class AuthService implements IAuthService {

  private final IEnrollmentRepository enrollmentRepository;
  private final IJwtService jwtService;
  private final PasswordEncoder passwordEncoder;

  /**
   * Creates the service.
   *
   * @param enrollmentRepository account persistence
   * @param jwtService token issue
   * @param passwordEncoder BCrypt encoder
   */
  public AuthService(
      IEnrollmentRepository enrollmentRepository,
      IJwtService jwtService,
      PasswordEncoder passwordEncoder) {
    this.enrollmentRepository = enrollmentRepository;
    this.jwtService = jwtService;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> register(String email, String password, String displayName) {
    requireCredentials(email, password);
    String normalized = email.trim().toLowerCase();
    if (enrollmentRepository.findAccountByEmail(normalized).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    }
    Account created = new Account();
    created.setEmail(normalized);
    created.setDisplayName(
        displayName != null && !displayName.isBlank() ? displayName.trim() : normalized);
    created.setRole("musician");
    created.setPasswordHash(passwordEncoder.encode(password));
    Account stored = enrollmentRepository.insertAccount(created);
    return toToken(stored);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> login(String email, String password) {
    requireCredentials(email, password);
    String normalized = email.trim().toLowerCase();
    Account account =
        enrollmentRepository
            .findAccountByEmail(normalized)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials"));
    String hash = account.getPasswordHash();
    if (hash == null || hash.isBlank() || !passwordEncoder.matches(password, hash)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials");
    }
    return toToken(account);
  }

  private void requireCredentials(String email, String password) {
    if (email == null || email.isBlank() || password == null || password.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password required");
    }
    if (password.length() < 8) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password must be at least 8 characters");
    }
  }

  private Map<String, Object> toToken(Account account) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("accessToken", jwtService.issue(account.getId(), account.getEmail()));
    body.put("tokenType", "Bearer");
    body.put("expiresIn", jwtService.ttlSeconds());
    body.put("accountId", account.getId());
    body.put("email", account.getEmail());
    return body;
  }
}
