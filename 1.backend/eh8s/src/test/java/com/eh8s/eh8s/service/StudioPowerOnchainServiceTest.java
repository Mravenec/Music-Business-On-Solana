package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.PartnerShare;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.StudioRoleGrant;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.IStudioPowerOnchainRepository;
import com.eh8s.eh8s.service.interfaces.IRoleApplicationService;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.interfaces.IStudioLedgerService;
import com.eh8s.eh8s.service.solana.GovernanceIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.StudioPowerIxBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class StudioPowerOnchainServiceTest {

  static final String PROGRAM = "GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG";
  static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
  static final String ADMIN = "CoP8dydvHXTDUoxxB6tAQk6yu3Q6YRkqNj8SY3vtR53o";
  static final String PARTNER = "YmTYQJifjP2DawdxDUYuNCZJ5to9ge5nNGaWGW2ozJU";
  static final String STRANGER = "U9mzuiHMVUsCw9BLKGibq55BPZuLaxipnvKFvn76Vjq";
  static final String CONFIG_PDA = "GgpAKj9FZppC8gckF6SzevirNbgkfhpEbqiz1GE6oKLF";

  private final Map<String, StudioRoleGrant> roles = new HashMap<>();
  private final Map<String, PartnerShare> shares = new HashMap<>();
  private final Map<String, Object> verified = new HashMap<>();
  private final List<PartnerShare> synced = new ArrayList<>();
  private final List<String> roleCalls = new ArrayList<>();
  private boolean verifierRejects;

  @Test
  void buildsAreOwnerOnlyAndRefuseThePrincipalAsGrantee() {
    StudioPowerOnchainService s = service();
    assertStatus(HttpStatus.FORBIDDEN, () -> s.buildGrantStudioRole(STRANGER, ADMIN));
    assertStatus(HttpStatus.FORBIDDEN, () -> s.buildRevokeStudioRole(STRANGER, ADMIN));
    assertStatus(
        HttpStatus.FORBIDDEN, () -> s.buildSetPartnerShare(STRANGER, PARTNER, 500, 0L, 0L));
    assertStatus(HttpStatus.FORBIDDEN, () -> s.console(STRANGER));
    assertStatus(HttpStatus.BAD_REQUEST, () -> s.buildGrantStudioRole(OWNER, OWNER));
    assertStatus(HttpStatus.BAD_REQUEST, () -> s.buildGrantStudioRole(OWNER, "not-a-wallet"));
    assertStatus(HttpStatus.BAD_REQUEST, () -> s.buildGrantStudioRole(null, ADMIN));
    assertStatus(HttpStatus.BAD_REQUEST, () -> s.buildSetPartnerShare(OWNER, PARTNER, null, 0L, 0L));
    assertStatus(
        HttpStatus.BAD_REQUEST, () -> s.buildSetPartnerShare(OWNER, PARTNER, 10001, 0L, 0L));
    assertStatus(HttpStatus.BAD_REQUEST, () -> s.buildSetPartnerShare(OWNER, PARTNER, 500, 50L, 40L));
    Map<String, Object> ix = s.buildGrantStudioRole(OWNER, ADMIN);
    assertEquals("grant_studio_role", ix.get("instruction"));
    assertEquals(StudioPowerIxBuilder.studioRolePda(PROGRAM, ADMIN), ix.get("studioRolePda"));
  }

  @Test
  void confirmGrantVerifiesTheSignatureBeforeTheMirrorWrite() {
    StudioPowerOnchainService s = service();
    verifierRejects = true;
    assertStatus(HttpStatus.BAD_REQUEST, () -> s.confirmGrantStudioRole(OWNER, ADMIN, "sig-grant"));
    assertTrue(roles.isEmpty(), "a rejected signature must not write the mirror");
    assertTrue(roleCalls.isEmpty(), "a rejected signature must not touch the role cache");

    verifierRejects = false;
    StudioRoleGrant row = s.confirmGrantStudioRole(OWNER, ADMIN, "sig-grant");
    assertEquals("grant_studio_role", verified.get("instruction"));
    assertEquals(OWNER, verified.get("signer"));
    assertEquals(
        ISolanaTxVerifier.accounts(
            OWNER,
            CONFIG_PDA,
            StudioPowerIxBuilder.studioRolePda(PROGRAM, ADMIN),
            SolanaPda.SYSTEM_PROGRAM,
            GovernanceIxBuilder.governancePda(PROGRAM)),
        verified.get("accounts"));
    assertEquals(ADMIN, row.getWalletPubkey());
    assertEquals("sig-grant", row.getTxSignature());
    assertNull(row.getRevokedAt());
    assertEquals(List.of("ensureStudioAdmin:" + ADMIN), roleCalls, "the role cache follows the confirmed grant");
    assertStatus(HttpStatus.CONFLICT, () -> s.confirmGrantStudioRole(OWNER, ADMIN, "sig-grant"));
    assertStatus(HttpStatus.BAD_REQUEST, () -> s.confirmGrantStudioRole(OWNER, ADMIN, " "));
  }

  @Test
  void confirmRevokeMarksTheMirrorRevokedAndRegrantReopensIt() {
    StudioPowerOnchainService s = service();
    s.confirmGrantStudioRole(OWNER, ADMIN, "sig-grant");
    StudioRoleGrant revoked = s.confirmRevokeStudioRole(OWNER, ADMIN, "sig-revoke");
    assertEquals("revoke_studio_role", verified.get("instruction"));
    assertEquals(
        ISolanaTxVerifier.accounts(
            OWNER,
            CONFIG_PDA,
            StudioPowerIxBuilder.studioRolePda(PROGRAM, ADMIN),
            GovernanceIxBuilder.governancePda(PROGRAM)),
        verified.get("accounts"));
    assertNotNull(revoked.getRevokedAt());
    assertEquals("sig-revoke", revoked.getTxSignature());
    assertEquals(List.of("ensureStudioAdmin:" + ADMIN, "clearStudioAdmin:" + ADMIN), roleCalls);
    StudioRoleGrant again = s.confirmGrantStudioRole(OWNER, ADMIN, "sig-grant-2");
    assertNull(again.getRevokedAt());
    assertEquals(1, roles.size());
  }

  @Test
  void confirmPartnerShareStoresTheWindowAndZeroBpsEndsIt() {
    StudioPowerOnchainService s = service();
    PartnerShare row = s.confirmSetPartnerShare(OWNER, PARTNER, 2500, 1_700_000_000L, 0L, "sig-share");
    assertEquals("set_partner_share", verified.get("instruction"));
    assertEquals(
        ISolanaTxVerifier.accounts(
            OWNER,
            CONFIG_PDA,
            StudioPowerIxBuilder.partnerSharePda(PROGRAM, PARTNER),
            StudioPowerIxBuilder.partnerBookPda(PROGRAM),
            SolanaPda.SYSTEM_PROGRAM,
            GovernanceIxBuilder.governancePda(PROGRAM)),
        verified.get("accounts"));
    assertEquals(2500, row.getShareBps());
    assertEquals(StudioPowerOnchainService.fromUnix(1_700_000_000L), row.getStartedAt());
    assertNull(row.getEndedAt());
    assertEquals(List.of(row), synced, "the books are repaired from the confirmed mirror");

    PartnerShare bounded =
        s.confirmSetPartnerShare(OWNER, PARTNER, 1000, 1_700_000_000L, 1_800_000_000L, "sig-share-2");
    assertEquals(1000, bounded.getShareBps());
    assertEquals(StudioPowerOnchainService.fromUnix(1_800_000_000L), bounded.getEndedAt());

    PartnerShare ended = s.confirmSetPartnerShare(OWNER, PARTNER, 0, 0L, 0L, "sig-share-3");
    assertEquals(1000, ended.getShareBps());
    assertNotNull(ended.getEndedAt());
    assertEquals("sig-share-3", ended.getTxSignature());
    assertEquals(1, shares.size());
    assertStatus(
        HttpStatus.CONFLICT, () -> s.confirmSetPartnerShare(OWNER, PARTNER, 0, 0L, 0L, "sig-share-3"));
  }

  @Test
  void removingAnUnmirroredPartnerStoresNothing() {
    StudioPowerOnchainService s = service();
    PartnerShare ended = s.confirmSetPartnerShare(OWNER, PARTNER, 0, 0L, 0L, "sig-none");
    assertEquals(0, ended.getShareBps());
    assertTrue(shares.isEmpty());
    assertFalse(ended.getEndedAt() == null);
    assertEquals(1, synced.size(), "a removal still repairs the books");
  }

  private StudioPowerOnchainService service() {
    return new StudioPowerOnchainService(repo(), chainConfigs(), new StudioPowerIxBuilder(), verifier(), ledger(), roles());
  }

  private static void assertStatus(HttpStatus status, Runnable call) {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, call::run);
    assertEquals(status, ex.getStatusCode());
  }

  private IRoleApplicationService roles() {
    return (IRoleApplicationService)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {IRoleApplicationService.class},
            (proxy, method, args) -> {
              roleCalls.add(method.getName() + ":" + args[0]);
              return null;
            });
  }

  private IStudioLedgerService ledger() {
    return (IStudioLedgerService)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {IStudioLedgerService.class},
            (proxy, method, args) -> {
              if (method.getName().equals("syncPartnerFromChain")) {
                synced.add((PartnerShare) args[0]);
                return null;
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static IChainConfigRepository chainConfigs() {
    return () -> {
      ChainConfig cfg = new ChainConfig();
      cfg.setProgramIdDevnet(PROGRAM);
      cfg.setRpcUrl("https://api.devnet.solana.com");
      cfg.setOwnerWalletPubkey(OWNER);
      return Optional.of(cfg);
    };
  }

  private ISolanaTxVerifier verifier() {
    return new ISolanaTxVerifier() {
      @Override
      public void verify(
          String rpcUrl,
          String programId,
          String txSignature,
          String instruction,
          String expectedDataHex,
          String expectedSigner,
          List<String> expectedAccounts) {
        if (verifierRejects) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signature mismatch");
        }
        verified.put("instruction", instruction);
        verified.put("dataHex", expectedDataHex);
        verified.put("signer", expectedSigner);
        verified.put("accounts", expectedAccounts);
      }

      @Override
      public void verifyTransaction(
          JsonNode tx,
          String programId,
          String instruction,
          String expectedDataHex,
          String expectedSigner,
          List<String> expectedAccounts) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private IStudioPowerOnchainRepository repo() {
    return new IStudioPowerOnchainRepository() {
      @Override
      public boolean isRecorded(String txSignature) {
        return roles.values().stream().anyMatch(r -> txSignature.equals(r.getTxSignature()))
            || shares.values().stream().anyMatch(r -> txSignature.equals(r.getTxSignature()));
      }

      @Override
      public Optional<StudioRoleGrant> findRoleGrant(String walletPubkey) {
        return Optional.ofNullable(roles.get(walletPubkey));
      }

      @Override
      public List<StudioRoleGrant> findRoleGrants() {
        return new ArrayList<>(roles.values());
      }

      @Override
      public StudioRoleGrant upsertRoleGranted(
          String walletPubkey, String pda, String txSignature, LocalDateTime grantedAt) {
        StudioRoleGrant row =
            new StudioRoleGrant(
                (long) roles.size() + 1, walletPubkey, "studio_admin", grantedAt, null, txSignature, pda);
        roles.put(walletPubkey, row);
        return row;
      }

      @Override
      public StudioRoleGrant upsertRoleRevoked(
          String walletPubkey, String pda, String txSignature, LocalDateTime revokedAt) {
        StudioRoleGrant old = roles.get(walletPubkey);
        StudioRoleGrant row =
            new StudioRoleGrant(
                (long) roles.size() + 1,
                walletPubkey,
                "studio_admin",
                old == null ? revokedAt : old.getGrantedAt(),
                revokedAt,
                txSignature,
                pda);
        roles.put(walletPubkey, row);
        return row;
      }

      @Override
      public Optional<PartnerShare> findPartnerShare(String walletPubkey) {
        return Optional.ofNullable(shares.get(walletPubkey));
      }

      @Override
      public List<PartnerShare> findPartnerShares() {
        return new ArrayList<>(shares.values());
      }

      @Override
      public PartnerShare upsertPartnerShare(
          String walletPubkey,
          int shareBps,
          LocalDateTime startedAt,
          LocalDateTime endedAt,
          String pda,
          String txSignature) {
        PartnerShare row =
            new PartnerShare(
                (long) shares.size() + 1, walletPubkey, shareBps, startedAt, endedAt, txSignature, pda);
        shares.put(walletPubkey, row);
        return row;
      }

      @Override
      public Optional<PartnerShare> endPartnerShare(
          String walletPubkey, LocalDateTime endedAt, String txSignature) {
        PartnerShare old = shares.get(walletPubkey);
        if (old == null) {
          return Optional.empty();
        }
        PartnerShare row =
            new PartnerShare(
                old.getId(), walletPubkey, old.getShareBps(), old.getStartedAt(), endedAt, txSignature, old.getPda());
        shares.put(walletPubkey, row);
        return Optional.of(row);
      }
    };
  }
}
