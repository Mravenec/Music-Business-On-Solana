package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.controller.interfaces.ITreasuryController;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import com.eh8s.eh8s.service.interfaces.ITreasuryService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the owner treasury balance and withdraw build/confirm JSON.
 */
@RestController
@RequestMapping("/api/owner/treasury")
public class TreasuryController implements ITreasuryController {

  private final ITreasuryService treasuryService;

  /**
   * Creates the controller.
   *
   * @param treasuryService owner treasury use cases
   */
  public TreasuryController(ITreasuryService treasuryService) {
    this.treasuryService = treasuryService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping
  public Map<String, Object> treasury(@RequestParam(required = false) String walletPubkey) {
    return treasuryService.treasury(walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/withdraw/build")
  public Map<String, Object> buildWithdraw(@RequestBody Map<String, Object> body) {
    return treasuryService.buildWithdraw(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.decimal(body, "amountUsdc"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/withdraw/confirm")
  public TreasuryWithdrawal confirmWithdraw(@RequestBody Map<String, Object> body) {
    return treasuryService.confirmWithdraw(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.decimal(body, "amountUsdc"),
        HttpBody.raw(body, "txSignature"));
  }
}
