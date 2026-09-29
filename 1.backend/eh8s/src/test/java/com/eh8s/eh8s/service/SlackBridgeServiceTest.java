package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.ISlackBridgeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for owner-only {@link SlackBridgeService} gate.
 */
class SlackBridgeServiceTest {

  private static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";

  @Test
  void statusMarksNonOwnerUnauthorized() {
    SlackBridgeService service = newService(new ArrayList<>());
    Map<String, Object> status = service.status("SomeOtherWallet111111111111111111111111");
    assertEquals(true, status.get("ownerOnly"));
    assertEquals(false, status.get("authorized"));
    assertEquals(OWNER, status.get("ownerWalletPubkey"));
    assertEquals("skip", status.get("mode"));
  }

  @Test
  void notifyRejectsNonOwner() {
    SlackBridgeService service = newService(new ArrayList<>());
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                service.notify("NonOwner1111111111111111111111111111111111", "nope", null, null));
    assertEquals(403, ex.getStatusCode().value());
  }

  @Test
  void notifyRequiresText() {
    SlackBridgeService service = newService(new ArrayList<>());
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.notify(OWNER, "  ", null, null));
    assertEquals(400, ex.getStatusCode().value());
    assertEquals("text is required", ex.getReason());
  }

  @Test
  void notifyOwnerSkipsWhenSecretsUnset() {
    List<SlackDeliveryLog> store = new ArrayList<>();
    SlackBridgeService service = newService(store);
    SlackDeliveryLog row =
        service.notify(OWNER, "owner smoke notify", null, null);
    assertEquals("skipped", row.getStatus());
    assertEquals(OWNER, row.getOwnerWalletPubkey());
    assertEquals(OWNER, row.getRequesterWalletPubkey());
    assertEquals("skip", row.getBridgeMode());
    assertEquals(1, store.size());
  }

  @Test
  void deliveriesRequireOwner() {
    SlackBridgeService service = newService(new ArrayList<>());
    assertThrows(
        ResponseStatusException.class,
        () -> service.deliveries("NonOwner1111111111111111111111111111111111"));
    assertTrue(service.deliveries(OWNER).isEmpty());
  }

  @Test
  void decisionBlocksCarryApproveRejectButtonsWithDecisionId() {
    List<Map<String, Object>> blocks = SlackBridgeService.decisionBlocks(7L, "Owner decision #7 (yellow): review");
    assertEquals("section", blocks.get(0).get("type"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> buttons = (List<Map<String, Object>>) blocks.get(1).get("elements");
    assertEquals(SlackBridgeService.APPROVE_ACTION, buttons.get(0).get("action_id"));
    assertEquals(SlackBridgeService.REJECT_ACTION, buttons.get(1).get("action_id"));
    assertEquals("7", buttons.get(0).get("value"));
    assertEquals("7", buttons.get(1).get("value"));
    assertEquals("primary", buttons.get(0).get("style"));
    assertEquals("danger", buttons.get(1).get("style"));
  }

  @Test
  void notifyDecisionLogsSkippedRowForDecisionAndStatusReportsInteractivity() {
    List<SlackDeliveryLog> store = new ArrayList<>();
    SlackBridgeService service = newService(store);
    OwnerDecision d = new OwnerDecision();
    d.setId(3L);
    d.setSemaphore("yellow");
    d.setTitle("Score Enigma 91: recommends level 5");
    d.setPayload("Score Enigma 91: recommends level 5");
    SlackDeliveryLog row = service.notifyDecision(d);
    assertEquals("skipped", row.getStatus());
    assertEquals(3L, row.getOwnerDecisionId());
    assertEquals(null, row.getRequesterWalletPubkey());
    assertEquals("Owner decision #3 (yellow): Score Enigma 91: recommends level 5", row.getPayloadPreview());
    Map<String, Object> status = service.status(OWNER);
    assertEquals(false, status.get("interactive"));
    assertEquals("/api/slack/interactions", status.get("interactionsPath"));
  }

  private static SlackBridgeService newService(List<SlackDeliveryLog> store) {
    ChainConfig cfg = new ChainConfig();
    cfg.setOwnerWalletPubkey(OWNER);
    IChainConfigRepository chain = () -> Optional.of(cfg);
    ISlackBridgeRepository slack =
        new ISlackBridgeRepository() {
          @Override
          public List<SlackDeliveryLog> findByOwnerWallet(String ownerWalletPubkey) {
            return store.stream()
                .filter(r -> ownerWalletPubkey.equals(r.getOwnerWalletPubkey()))
                .toList();
          }

          @Override
          public SlackDeliveryLog insert(SlackDeliveryLog row) {
            store.add(row);
            return row;
          }
        };
    return new SlackBridgeService(slack, chain, "", "", "", "#eh8s-owner", "");
  }
}
