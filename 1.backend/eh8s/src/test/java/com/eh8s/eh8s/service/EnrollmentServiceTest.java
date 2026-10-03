package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.IEnrollmentRepository;
import com.eh8s.eh8s.service.interfaces.IJwtService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A public key alone does not receive a session token.
 */
class EnrollmentServiceTest {

  @Test
  void unsignedWalletIsRejectedBeforeAnyAccountWrite() {
    EnrollmentService service = new EnrollmentService(null, null, null, null, null);
    Account request = new Account();
    request.setWalletPubkey("WalletWithoutASignature11111111111111111111");

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.upsertWalletSession(request, null));
    assertEquals(401, ex.getStatusCode().value());
  }

  @Test
  void aSignatureWithoutAFreshNonceIsRejected() {
    EnrollmentService service = new EnrollmentService(null, null, null, null, null);
    Account request = new Account();
    request.setWalletPubkey("WalletWithoutASignature11111111111111111111");

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.upsertWalletSession(request, "not-a-signature"));
    assertEquals(401, ex.getStatusCode().value());
  }

  @Test
  void currentSessionWithoutAPrincipalIsRejected() {
    EnrollmentService service = new EnrollmentService(null, null, null, null, null);

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.currentSession(null));
    assertEquals(401, ex.getStatusCode().value());
  }

  @Test
  void currentSessionLoadsTheAccountFromTheJwtWithoutANewSignature() {
    IEnrollmentRepository accounts = mock(IEnrollmentRepository.class);
    IChainConfigRepository chain = mock(IChainConfigRepository.class);
    IJwtService jwt = mock(IJwtService.class);
    Account account = new Account();
    account.setId(7L);
    account.setEmail("musician@wallet.eh8s.local");
    account.setWalletPubkey("WalletPubkey11111111111111111111111111111111");
    when(accounts.touchAccount(7L)).thenReturn(account);
    when(accounts.findMusicianByAccount(7L)).thenReturn(Optional.empty());
    when(chain.findActive()).thenReturn(Optional.empty());
    when(jwt.issue(7L, "musician@wallet.eh8s.local")).thenReturn("stored-token");
    EnrollmentService service = new EnrollmentService(accounts, chain, jwt, null, null);

    Map<String, Object> session =
        service.currentSession(new JwtPrincipal(7L, "musician@wallet.eh8s.local"));

    assertEquals(account, session.get("account"));
    assertEquals("stored-token", session.get("accessToken"));
  }
}
