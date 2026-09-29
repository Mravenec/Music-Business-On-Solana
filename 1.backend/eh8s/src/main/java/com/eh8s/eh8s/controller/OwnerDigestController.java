package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IOwnerDigestController;
import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDigest;
import com.eh8s.eh8s.service.interfaces.IOwnerDigestService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner digest endpoints under {@code /api/owner/digest}.
 */
@RestController
@RequestMapping("/api/owner/digest")
public class OwnerDigestController implements IOwnerDigestController {

  private final IOwnerDigestService digestService;

  /**
   * Creates the controller.
   *
   * @param digestService owner digest use cases
   */
  public OwnerDigestController(IOwnerDigestService digestService) {
    this.digestService = digestService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/latest")
  public Map<String, Object> latest(@RequestParam(required = false) String walletPubkey) {
    return digestService.latest(walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/run")
  public OwnerDigest run(@RequestBody Map<String, Object> body) {
    return digestService.run(HttpBody.raw(body, "walletPubkey"));
  }
}
