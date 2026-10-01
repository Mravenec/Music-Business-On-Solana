package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IEnrollmentController;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.service.interfaces.IEnrollmentService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Academy enrollment and wallet session HTTP API.
 */
@RestController
@RequestMapping("/api")
public class EnrollmentController implements IEnrollmentController {

  private final IEnrollmentService enrollmentService;

  /**
   * Creates the controller.
   *
   * @param enrollmentService enrollment use cases
   */
  public EnrollmentController(IEnrollmentService enrollmentService) {
    this.enrollmentService = enrollmentService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/accounts")
  public Account createAccount(@RequestBody Account account) {
    return enrollmentService.createAccount(account);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/musicians")
  public MusicianProfile createMusician(@RequestBody MusicianProfile profile) {
    return enrollmentService.createMusician(profile);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/musicians")
  public List<MusicianProfile> musicians() {
    return enrollmentService.musicians();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/academy-subscriptions")
  public AcademySubscription subscribe(@RequestBody AcademySubscription subscription) {
    return enrollmentService.subscribe(subscription);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/academy-subscriptions")
  public List<AcademySubscription> subscriptions() {
    return enrollmentService.subscriptions();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/enigma-evaluations")
  public EnigmaEvaluation evaluate(@RequestBody EnigmaEvaluation evaluation) {
    return enrollmentService.evaluate(evaluation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/enigma-evaluations")
  public List<EnigmaEvaluation> evaluations() {
    return enrollmentService.evaluations();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/session/challenge")
  public Map<String, Object> challenge(@RequestBody Map<String, Object> body) {
    return enrollmentService.challenge(HttpBody.text(body, "walletPubkey"));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/session/wallet")
  public Map<String, Object> upsertWalletSession(@RequestBody Map<String, Object> body) {
    Account request = new Account();
    request.setWalletPubkey(HttpBody.text(body, "walletPubkey"));
    request.setDisplayName(HttpBody.text(body, "displayName"));
    request.setEmail(HttpBody.text(body, "email"));
    request.setRole(HttpBody.text(body, "role"));
    request.setCountryCode(HttpBody.text(body, "countryCode"));
    return enrollmentService.upsertWalletSession(request, HttpBody.text(body, "signature"));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/session/wallet/{walletPubkey}")
  public Map<String, Object> findWalletSession(@PathVariable String walletPubkey) {
    return enrollmentService.findWalletSession(walletPubkey);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/session/current")
  public Map<String, Object> currentSession(@AuthenticationPrincipal JwtPrincipal principal) {
    return enrollmentService.currentSession(principal);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/session/wallet/{walletPubkey}/location")
  public Map<String, Object> updateWalletLocation(
      @PathVariable String walletPubkey, @RequestBody Account location) {
    return enrollmentService.updateWalletLocation(walletPubkey, location);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/accounts")
  public List<Account> accounts() {
    return enrollmentService.accounts();
  }
}
