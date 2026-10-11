package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartner;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.StudioRoleGrant;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRole;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.PartnerShare;
import com.eh8s.eh8s.repository.interfaces.IRoleApplicationRepository;
import com.eh8s.eh8s.repository.interfaces.IStudioLedgerRepository;
import com.eh8s.eh8s.repository.interfaces.IStudioPowerOnchainRepository;
import com.eh8s.eh8s.service.StudioLedgerService.DatedFee;
import com.eh8s.eh8s.service.interfaces.IRoleApplicationService;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A partner shares a fee only on calendar days inside their window.
 */
class StudioLedgerServiceTest {

  @Test
  void partnerAddedOnTheTwentiethMissesTheFifthAndSharesTheTwentyFifth() {
    StudioPartner early = partner(1L, 2500, LocalDateTime.of(2026, 10, 1, 9, 0), null);
    StudioPartner late = partner(2L, 2500, LocalDateTime.of(2026, 10, 20, 15, 0), null);
    DatedFee fifth = new DatedFee(LocalDateTime.of(2026, 10, 5, 12, 0), new BigDecimal("100.00"));
    DatedFee twentiethMorning =
        new DatedFee(LocalDateTime.of(2026, 10, 20, 9, 0), new BigDecimal("40.00"));
    DatedFee twentyFifth =
        new DatedFee(LocalDateTime.of(2026, 10, 25, 18, 0), new BigDecimal("100.00"));

    Map<Long, BigDecimal> beforeJoin = StudioLedgerService.shareOf(List.of(early, late), List.of(fifth));
    assertEquals(new BigDecimal("25.00"), beforeJoin.get(1L));
    assertNull(beforeJoin.get(2L));

    Map<Long, BigDecimal> joinDay =
        StudioLedgerService.shareOf(List.of(early, late), List.of(twentiethMorning));
    assertEquals(new BigDecimal("10.00"), joinDay.get(1L));
    assertEquals(new BigDecimal("10.00"), joinDay.get(2L));

    Map<Long, BigDecimal> afterJoin =
        StudioLedgerService.shareOf(List.of(early, late), List.of(twentyFifth));
    assertEquals(new BigDecimal("25.00"), afterJoin.get(1L));
    assertEquals(new BigDecimal("25.00"), afterJoin.get(2L));
  }

  @Test
  void partnerWhoLeftKeepsThatDayAndMissesTheNext() {
    StudioPartner left =
        partner(
            1L,
            2500,
            LocalDateTime.of(2026, 10, 1, 0, 0),
            LocalDateTime.of(2026, 10, 10, 16, 0));
    Map<Long, BigDecimal> lastDay =
        StudioLedgerService.shareOf(
            List.of(left),
            List.of(new DatedFee(LocalDateTime.of(2026, 10, 10, 9, 0), new BigDecimal("100.00"))));
    Map<Long, BigDecimal> nextDay =
        StudioLedgerService.shareOf(
            List.of(left),
            List.of(new DatedFee(LocalDateTime.of(2026, 10, 11, 9, 0), new BigDecimal("100.00"))));
    assertEquals(new BigDecimal("25.00"), lastDay.get(1L));
    assertFalse(nextDay.containsKey(1L));
  }

  @Test
  void onChainWindowIsExactAndEndIsExclusive() {
    LocalDateTime start = LocalDateTime.of(2026, 10, 10, 12, 0);
    LocalDateTime end = LocalDateTime.of(2026, 10, 20, 12, 0);
    StudioPartner chain = partner(1L, 3000, start, end);
    assertFalse(StudioLedgerService.coversExact(chain, start.minusSeconds(1)));
    assertEquals(true, StudioLedgerService.coversExact(chain, start));
    assertEquals(true, StudioLedgerService.coversExact(chain, end.minusSeconds(1)));
    assertFalse(StudioLedgerService.coversExact(chain, end));

    DatedFee sameDayBeforeStart =
        new DatedFee(LocalDateTime.of(2026, 10, 10, 9, 0), new BigDecimal("100.00"));
    DatedFee sameDayAfterEnd =
        new DatedFee(LocalDateTime.of(2026, 10, 20, 18, 0), new BigDecimal("100.00"));
    // The MariaDB day rule would pay both; the chain window pays neither.
    assertEquals(
        new BigDecimal("30.00"),
        StudioLedgerService.shareOf(List.of(chain), List.of(sameDayBeforeStart)).get(1L));
    assertFalse(
        StudioLedgerService.shareOf(List.of(chain), Set.of(1L), List.of(sameDayBeforeStart))
            .containsKey(1L));
    assertFalse(
        StudioLedgerService.shareOf(List.of(chain), Set.of(1L), List.of(sameDayAfterEnd))
            .containsKey(1L));
  }

  @Test
  void jwtPartnerSaveCannotOverrideAConfirmedOnChainShare() {
    PartnerShare mirror =
        new PartnerShare(1L, WALLET, 4000, LocalDateTime.of(2026, 10, 1, 0, 0), null, "sig", "pda");
    Harness h = new Harness(mirror, new StudioPartner(5L, WALLET, "Pat", 100, (byte) 1, null, LocalDateTime.of(2026, 9, 1, 0, 0), null));
    StudioPartner draft = new StudioPartner(null, WALLET, "Pat", 9000, null, null, null, null);
    ResponseStatusException save =
        assertThrows(ResponseStatusException.class, () -> h.service().savePartner(draft));
    assertEquals(HttpStatus.CONFLICT, save.getStatusCode());
    ResponseStatusException drop =
        assertThrows(ResponseStatusException.class, () -> h.service().deactivatePartner(5L));
    assertEquals(HttpStatus.CONFLICT, drop.getStatusCode());
    assertEquals(0, h.writes.size(), "a refused JWT mutate must not touch the books");
  }

  @Test
  void jwtPartnerSaveStaysTheFallbackWhenNoOnChainRowExists() {
    Harness h = new Harness(null, null);
    StudioPartner draft = new StudioPartner(null, WALLET, "Pat", 500, null, null, null, null);
    h.service().savePartner(draft);
    assertEquals(List.of("insertPartner:500"), h.writes);
  }

  @Test
  void confirmedMirrorRepairsADisagreeingMariaDbRowAndTheBooksFollowIt() {
    LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
    PartnerShare mirror = new PartnerShare(1L, WALLET, 4000, start, null, "sig", "pda");
    Harness h = new Harness(mirror, new StudioPartner(5L, WALLET, "Pat", 100, (byte) 1, null, LocalDateTime.of(2026, 9, 1, 0, 0), null));
    h.service().syncPartnerFromChain(mirror);
    assertEquals(List.of("syncPartnerFromChain:4000:live"), h.writes);
    assertEquals(List.of("ensurePartner"), h.roleCalls);

    // A chain removal deactivates the row and clears the partner role.
    PartnerShare removed = new PartnerShare(1L, WALLET, 4000, start, LocalDateTime.of(2026, 10, 5, 0, 0), "sig2", "pda");
    Harness gone = new Harness(removed, new StudioPartner(5L, WALLET, "Pat", 4000, (byte) 1, null, start, null));
    gone.service().syncPartnerFromChain(removed);
    assertEquals(List.of("syncPartnerFromChain:4000:ended"), gone.writes);
    assertEquals(List.of("clearPartner"), gone.roleCalls);

    // A chain-only wallet gets a row; an in-agreement row is not rewritten.
    Harness fresh = new Harness(mirror, null);
    fresh.service().syncPartnerFromChain(mirror);
    assertEquals(List.of("syncPartnerFromChain:4000:live"), fresh.writes);
    StudioPartner same = new StudioPartner(5L, WALLET, "Pat", 4000, (byte) 1, null, start, null);
    Harness quiet = new Harness(mirror, same);
    quiet.service().syncPartnerFromChain(mirror);
    assertEquals(List.of(), quiet.writes);
  }

  @Test
  void booksAccessFollowsTheConfirmedOnChainRoleAndFallsBackToTheCache() {
    LocalDateTime now = LocalDateTime.now();
    Harness active = new Harness(null, null);
    active.role = new StudioRoleGrant(1L, WALLET, "studio_admin", now, null, "sig", "pda");
    assertEquals(true, active.service().isBooksEditor(2L), "confirmed grant opens the books even without a cached role");
    assertEquals(List.of("ensureStudioAdmin"), active.roleCalls);

    Harness revoked = new Harness(null, null);
    revoked.membership = true;
    revoked.role = new StudioRoleGrant(1L, WALLET, "studio_admin", now, now, "sig2", "pda");
    assertFalse(revoked.service().isBooksEditor(2L), "a confirmed revoke beats a stale cached role");
    assertEquals(List.of("clearStudioAdmin"), revoked.roleCalls);

    Harness fallback = new Harness(null, null);
    fallback.membership = true;
    assertEquals(true, fallback.service().isBooksEditor(2L), "no on-chain row: the MariaDB role still works");
    Harness none = new Harness(null, null);
    assertFalse(none.service().isBooksEditor(2L));
  }

  static final String WALLET = "CoP8dydvHXTDUoxxB6tAQk6yu3Q6YRkqNj8SY3vtR53o";

  /** Stub collaborators that record the writes a service call makes. */
  private static final class Harness {
    final List<String> writes = new ArrayList<>();
    final List<String> roleCalls = new ArrayList<>();
    private final PartnerShare mirror;
    private final StudioPartner row;
    StudioRoleGrant role;
    boolean membership;

    Harness(PartnerShare mirror, StudioPartner row) {
      this.mirror = mirror;
      this.row = row;
    }

    StudioLedgerService service() {
      return new StudioLedgerService(
          proxy(
              IStudioLedgerRepository.class,
              (name, args) -> {
                switch (name) {
                  case "findPartnerByWallet":
                    return Optional.ofNullable(row);
                  case "findPartner":
                    return Optional.ofNullable(row);
                  case "findAccount":
                    Account account = new Account();
                    account.setId((Long) args[0]);
                    account.setWalletPubkey(WALLET);
                    return Optional.of(account);
                  case "activeShareBps":
                    return 0;
                  case "insertPartner":
                    writes.add("insertPartner:" + args[2]);
                    return new StudioPartner(9L, (String) args[0], (String) args[1], (Integer) args[2], (byte) 1, null, null, null);
                  case "syncPartnerFromChain":
                    writes.add(
                        "syncPartnerFromChain:" + args[2] + ":" + ((boolean) args[5] ? "live" : "ended"));
                    return new StudioPartner(5L, (String) args[0], "Pat", (Integer) args[2], (byte) 1, null, null, null);
                  default:
                    throw new UnsupportedOperationException(name);
                }
              }),
          () -> Optional.empty(),
          proxy(
              IRoleApplicationRepository.class,
              (name, args) -> {
                if (name.equals("findMembership")) {
                  return membership ? Optional.of(new AccountRole()) : Optional.empty();
                }
                throw new UnsupportedOperationException(name);
              }),
          proxy(
              IRoleApplicationService.class,
              (name, args) -> {
                roleCalls.add(name);
                return null;
              }),
          proxy(
              IStudioPowerOnchainRepository.class,
              (name, args) -> {
                if (name.equals("findPartnerShare")) {
                  return Optional.ofNullable(mirror);
                }
                if (name.equals("findRoleGrant")) {
                  return Optional.ofNullable(role);
                }
                if (name.equals("findPartnerShares")) {
                  return mirror == null ? List.of() : List.of(mirror);
                }
                throw new UnsupportedOperationException(name);
              }));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Call call) {
      return (T)
          Proxy.newProxyInstance(
              type.getClassLoader(),
              new Class<?>[] {type},
              (p, method, args) -> call.handle(method.getName(), args));
    }

    interface Call {
      Object handle(String name, Object[] args) throws Throwable;
    }
  }

  private static StudioPartner partner(
      Long id, int shareBps, LocalDateTime startedAt, LocalDateTime endedAt) {
    return new StudioPartner(id, "wallet", "Partner", shareBps, (byte) 1, null, startedAt, endedAt);
  }
}
