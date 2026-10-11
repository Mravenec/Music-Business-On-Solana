package com.eh8s.eh8s.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartner;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartnerAllocation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.PartnerShare;
import com.eh8s.eh8s.service.interfaces.IStudioLedgerService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Partner mutate is principal-only; studio admin keeps read of books and own earnings.
 */
class StudioLedgerControllerTest {

  private static final JwtPrincipal PRINCIPAL = new JwtPrincipal(1L, "principal@eh8s.test");
  private static final JwtPrincipal STUDIO_ADMIN = new JwtPrincipal(2L, "admin@eh8s.test");
  private static final JwtPrincipal MUSICIAN = new JwtPrincipal(3L, "musician@eh8s.test");

  @Test
  void principalMayMutatePartnerShares() {
    FakeLedger ledger = new FakeLedger();
    StudioLedgerController controller = new StudioLedgerController(ledger);
    StudioPartner draft = new StudioPartner(null, "Wallet1111111111111111111111111111111111", "Pat", 500, null, null, null, null);

    StudioPartner saved = controller.savePartner(PRINCIPAL, draft);
    assertEquals(1, ledger.saveCalls.get());
    assertEquals("Pat", saved.getDisplayName());

    StudioPartner removed = controller.deactivatePartner(PRINCIPAL, 9L);
    assertEquals(1, ledger.deactivateCalls.get());
    assertEquals(9L, removed.getId());
  }

  @Test
  void studioAdminGets403OnMutateButKeepsBooksAndEarningsRead() {
    FakeLedger ledger = new FakeLedger();
    StudioLedgerController controller = new StudioLedgerController(ledger);
    StudioPartner draft = new StudioPartner(null, "Wallet2222222222222222222222222222222222", "Sam", 250, null, null, null, null);

    ResponseStatusException saveDenied =
        assertThrows(ResponseStatusException.class, () -> controller.savePartner(STUDIO_ADMIN, draft));
    assertEquals(HttpStatus.FORBIDDEN, saveDenied.getStatusCode());
    assertEquals(0, ledger.saveCalls.get());

    ResponseStatusException deleteDenied =
        assertThrows(ResponseStatusException.class, () -> controller.deactivatePartner(STUDIO_ADMIN, 9L));
    assertEquals(HttpStatus.FORBIDDEN, deleteDenied.getStatusCode());
    assertEquals(0, ledger.deactivateCalls.get());

    assertEquals(List.of(), controller.partners(STUDIO_ADMIN));
    assertEquals(List.of(), controller.allocations(STUDIO_ADMIN, 2026, 10));
    assertEquals(List.of(), controller.myAllocations(STUDIO_ADMIN, 2026, 10));
    assertEquals(List.of(), controller.soloClaims(STUDIO_ADMIN, 2026, 10));
  }

  @Test
  void ordinaryWalletCannotReadPartnerBooks() {
    FakeLedger ledger = new FakeLedger();
    StudioLedgerController controller = new StudioLedgerController(ledger);
    ResponseStatusException denied =
        assertThrows(ResponseStatusException.class, () -> controller.partners(MUSICIAN));
    assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
  }

  private static final class FakeLedger implements IStudioLedgerService {
    private final AtomicInteger saveCalls = new AtomicInteger();
    private final AtomicInteger deactivateCalls = new AtomicInteger();

    @Override
    public boolean isOwner(Long accountId) {
      return Long.valueOf(1L).equals(accountId);
    }

    @Override
    public boolean isBooksEditor(Long accountId) {
      return Long.valueOf(1L).equals(accountId) || Long.valueOf(2L).equals(accountId);
    }

    @Override
    public List<PendingClaim> soloClaims(Long accountId, int year, int month) {
      return List.of();
    }

    @Override
    public List<PendingClaim> bandClaims(Long accountId, int year, int month) {
      return List.of();
    }

    @Override
    public List<ConcertSettlement> venueExpenses(Long accountId, int year, int month) {
      return List.of();
    }

    @Override
    public List<AcademySubscription> instructorShares(Long accountId, int year, int month) {
      return List.of();
    }

    @Override
    public List<StudioPartnerAllocation> myAllocations(Long accountId, int year, int month) {
      return List.of();
    }

    @Override
    public List<StudioPartner> partners() {
      return List.of();
    }

    @Override
    public StudioPartner savePartner(StudioPartner draft) {
      saveCalls.incrementAndGet();
      return draft;
    }

    @Override
    public StudioPartner deactivatePartner(Long partnerId) {
      deactivateCalls.incrementAndGet();
      return new StudioPartner(partnerId, "w", "gone", 1, (byte) 0, null, null, null);
    }

    @Override
    public StudioPartner syncPartnerFromChain(PartnerShare mirror) {
      return null;
    }

    @Override
    public List<ConcertSettlement> showFees(int year, int month) {
      return List.of();
    }

    @Override
    public List<AcademySubscription> academyFees(int year, int month) {
      return List.of();
    }

    @Override
    public List<SyncLicenseDeal> syncFees(int year, int month) {
      return List.of();
    }

    @Override
    public List<StudioPartnerAllocation> allocations(int year, int month) {
      return List.of();
    }
  }
}
