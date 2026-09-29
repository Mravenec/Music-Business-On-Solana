package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A public key alone does not receive a session token.
 */
class EnrollmentServiceTest {

  @Test
  void unsignedWalletIsRejectedBeforeAnyAccountWrite() {
    EnrollmentService service = new EnrollmentService(null, null, null);
    Account request = new Account();
    request.setWalletPubkey("WalletWithoutASignature11111111111111111111");

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.upsertWalletSession(request, null));
    assertEquals(401, ex.getStatusCode().value());
  }

  @Test
  void aSignatureWithoutAFreshNonceIsRejected() {
    EnrollmentService service = new EnrollmentService(null, null, null);
    Account request = new Account();
    request.setWalletPubkey("WalletWithoutASignature11111111111111111111");

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.upsertWalletSession(request, "not-a-signature"));
    assertEquals(401, ex.getStatusCode().value());
  }
}
