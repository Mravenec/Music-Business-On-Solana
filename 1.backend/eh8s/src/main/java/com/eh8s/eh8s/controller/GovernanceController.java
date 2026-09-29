package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.controller.interfaces.IGovernanceController;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.Governance;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceApproval;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceProposal;
import com.eh8s.eh8s.service.interfaces.IGovernanceService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner multisig routes under {@code /api/governance}.
 */
@RestController
@RequestMapping("/api/governance")
public class GovernanceController implements IGovernanceController {

  private final IGovernanceService governanceService;

  /**
   * Creates the controller.
   *
   * @param governanceService governance service
   */
  public GovernanceController(IGovernanceService governanceService) {
    this.governanceService = governanceService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping
  public Map<String, Object> overview(@RequestParam(required = false) String walletPubkey) {
    return governanceService.overview(walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/init/build")
  public Map<String, Object> buildInit(@RequestBody Map<String, Object> body) {
    return governanceService.buildInit(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.textList(body, "signers"),
        HttpBody.intValue(body, "threshold"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/init/confirm")
  public Governance confirmInit(@RequestBody Map<String, Object> body) {
    return governanceService.confirmInit(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.textList(body, "signers"),
        HttpBody.intValue(body, "threshold"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/proposals/build")
  public Map<String, Object> buildPropose(@RequestBody Map<String, Object> body) {
    return governanceService.buildPropose(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "kind"),
        HttpBody.decimal(body, "amountUsdc"),
        HttpBody.raw(body, "destinationWalletPubkey"),
        HttpBody.raw(body, "agentCode"),
        HttpBody.raw(body, "agentWalletPubkey"),
        HttpBody.intValue(body, "permissions"),
        HttpBody.textList(body, "newSigners"),
        HttpBody.intValue(body, "newThreshold"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/proposals/confirm")
  public GovernanceProposal confirmPropose(@RequestBody Map<String, Object> body) {
    return governanceService.confirmPropose(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "kind"),
        HttpBody.decimal(body, "amountUsdc"),
        HttpBody.raw(body, "destinationWalletPubkey"),
        HttpBody.raw(body, "agentCode"),
        HttpBody.raw(body, "agentWalletPubkey"),
        HttpBody.intValue(body, "permissions"),
        HttpBody.textList(body, "newSigners"),
        HttpBody.intValue(body, "newThreshold"),
        HttpBody.longValue(body, "proposalId"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/proposals/{id}/approve/build")
  public Map<String, Object> buildApprove(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    return governanceService.buildApprove(
        id, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/proposals/{id}/approve/confirm")
  public GovernanceApproval confirmApprove(
      @PathVariable Long id, @RequestBody Map<String, Object> body) {
    return governanceService.confirmApprove(
        id, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/proposals/{id}/execute/build")
  public Map<String, Object> buildExecute(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    return governanceService.buildExecute(
        id, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/proposals/{id}/execute/confirm")
  public GovernanceProposal confirmExecute(
      @PathVariable Long id, @RequestBody Map<String, Object> body) {
    return governanceService.confirmExecute(
        id, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }
}
