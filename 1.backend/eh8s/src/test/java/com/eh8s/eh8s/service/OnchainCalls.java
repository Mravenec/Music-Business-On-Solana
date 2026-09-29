package com.eh8s.eh8s.service;

import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltySplit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.DevnetPayMarker;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.Governance;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceApproval;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceProposal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentAuthority;
import com.eh8s.eh8s.service.interfaces.IAcademyOnchainService;
import com.eh8s.eh8s.service.interfaces.IAgentAuthorityService;
import com.eh8s.eh8s.service.interfaces.IBandVaultService;
import com.eh8s.eh8s.service.interfaces.IDevnetPayService;
import com.eh8s.eh8s.service.interfaces.IGovernanceService;
import com.eh8s.eh8s.service.interfaces.IRoyaltyGeoOnchainService;
import com.eh8s.eh8s.service.interfaces.ISettleClaimOnchainService;
import com.eh8s.eh8s.service.interfaces.ISongRoyaltyService;
import com.eh8s.eh8s.service.interfaces.ITreasuryService;
import com.eh8s.eh8s.service.interfaces.IVenueBookingOnchainService;
import java.util.Map;

/**
 * Test twin of the on-chain controllers: reads a fixture JSON body with {@link HttpBody} and calls
 * the service with primitives, so service tests keep their readable map fixtures.
 */
final class OnchainCalls {

  private OnchainCalls() {}

  static Map<String, Object> buildSubscribeAcademy(
      IAcademyOnchainService service, Long subscriptionId, Map<String, Object> body) {
    return service.buildSubscribeAcademy(
        subscriptionId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "instructorUsdcAta"),
        HttpBody.raw(body, "payerUsdcAta"));
  }

  static AcademySubscription confirmSubscribeAcademy(
      IAcademyOnchainService service, Long subscriptionId, Map<String, Object> body) {
    return service.confirmSubscribeAcademy(
        subscriptionId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.raw(body, "academySubscriptionPda"));
  }

  static Map<String, Object> buildAuthorize(
      IAgentAuthorityService service, Map<String, Object> body) {
    return service.buildAuthorize(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "agentCode"),
        HttpBody.raw(body, "agentWalletPubkey"),
        HttpBody.intValue(body, "permissions"));
  }

  static AgentAuthority confirmAuthorize(IAgentAuthorityService service, Map<String, Object> body) {
    return service.confirmAuthorize(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "agentCode"),
        HttpBody.raw(body, "agentWalletPubkey"),
        HttpBody.intValue(body, "permissions"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildLevel(IAgentAuthorityService service, Map<String, Object> body) {
    return service.buildLevel(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.longValue(body, "musicianProfileId"),
        HttpBody.intValue(body, "newLevel"));
  }

  static MusicianLevelChange confirmLevel(
      IAgentAuthorityService service, Map<String, Object> body) {
    return service.confirmLevel(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.longValue(body, "musicianProfileId"),
        HttpBody.intValue(body, "newLevel"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildActivate(
      IBandVaultService service, Long bandId, Map<String, Object> body) {
    return service.buildActivate(bandId, HttpBody.raw(body, "walletPubkey"));
  }

  static Band confirmActivate(IBandVaultService service, Long bandId, Map<String, Object> body) {
    return service.confirmActivate(
        bandId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.raw(body, "bandVaultPda"));
  }

  static Map<String, Object> buildSyncWeights(
      IBandVaultService service, Long bandId, Map<String, Object> body) {
    return service.buildSyncWeights(bandId, HttpBody.raw(body, "walletPubkey"));
  }

  static Band confirmSyncWeights(IBandVaultService service, Long bandId, Map<String, Object> body) {
    return service.confirmSyncWeights(
        bandId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  static PendingClaim recordClaim(
      IDevnetPayService service, Long claimId, Map<String, Object> body) {
    return service.recordClaim(claimId, HttpBody.raw(body, "walletPubkey"));
  }

  static AcademySubscription recordPay(
      IDevnetPayService service, Long subscriptionId, Map<String, Object> body) {
    return service.recordPay(subscriptionId, HttpBody.raw(body, "walletPubkey"));
  }

  static Map<String, Object> buildInit(IGovernanceService service, Map<String, Object> body) {
    return service.buildInit(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.textList(body, "signers"),
        HttpBody.intValue(body, "threshold"));
  }

  static Governance confirmInit(IGovernanceService service, Map<String, Object> body) {
    return service.confirmInit(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.textList(body, "signers"),
        HttpBody.intValue(body, "threshold"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildPropose(IGovernanceService service, Map<String, Object> body) {
    return service.buildPropose(
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

  static GovernanceProposal confirmPropose(IGovernanceService service, Map<String, Object> body) {
    return service.confirmPropose(
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

  static Map<String, Object> buildApprove(
      IGovernanceService service, Long proposalId, Map<String, Object> body) {
    return service.buildApprove(proposalId, HttpBody.raw(body, "walletPubkey"));
  }

  static GovernanceApproval confirmApprove(
      IGovernanceService service, Long proposalId, Map<String, Object> body) {
    return service.confirmApprove(
        proposalId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildExecute(
      IGovernanceService service, Long proposalId, Map<String, Object> body) {
    return service.buildExecute(proposalId, HttpBody.raw(body, "walletPubkey"));
  }

  static GovernanceProposal confirmExecute(
      IGovernanceService service, Long proposalId, Map<String, Object> body) {
    return service.confirmExecute(
        proposalId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildDepositRoyalties(
      IRoyaltyGeoOnchainService service, Long depositId, Map<String, Object> body) {
    return service.buildDepositRoyalties(depositId, HttpBody.raw(body, "walletPubkey"));
  }

  static RoyaltyDeposit confirmDepositRoyalties(
      IRoyaltyGeoOnchainService service, Long depositId, Map<String, Object> body) {
    return service.confirmDepositRoyalties(
        depositId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildSubscribeGeographic(
      IRoyaltyGeoOnchainService service, Long subscriptionId, Map<String, Object> body) {
    return service.buildSubscribeGeographic(
        subscriptionId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "payerUsdcAta"));
  }

  static GeographicSubscription confirmSubscribeGeographic(
      IRoyaltyGeoOnchainService service, Long subscriptionId, Map<String, Object> body) {
    return service.confirmSubscribeGeographic(
        subscriptionId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.raw(body, "geographicSubscriptionPda"));
  }

  static Map<String, Object> buildSettleConcert(
      ISettleClaimOnchainService service, Long settlementId, Map<String, Object> body) {
    return service.buildSettleConcert(settlementId, HttpBody.raw(body, "walletPubkey"));
  }

  static ConcertSettlement confirmSettleConcert(
      ISettleClaimOnchainService service, Long settlementId, Map<String, Object> body) {
    return service.confirmSettleConcert(
        settlementId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.raw(body, "concertSettlementPda"));
  }

  static Map<String, Object> buildClaimRoyalties(
      ISettleClaimOnchainService service, Long claimId, Map<String, Object> body) {
    return service.buildClaimRoyalties(
        claimId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.decimal(body, "amountUsdc"),
        HttpBody.raw(body, "vaultUsdcAta"),
        HttpBody.raw(body, "musicianUsdcAta"));
  }

  static PendingClaim confirmClaimRoyalties(
      ISettleClaimOnchainService service, Long claimId, Map<String, Object> body) {
    return service.confirmClaimRoyalties(
        claimId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.raw(body, "claimPda"));
  }

  static Map<String, Object> buildActivatePool(
      ISongRoyaltyService service, Long trackId, Map<String, Object> body) {
    return service.buildActivatePool(
        trackId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.royaltySplits(body, "splits"));
  }

  static Track confirmActivatePool(
      ISongRoyaltyService service, Long trackId, Map<String, Object> body) {
    return service.confirmActivatePool(
        trackId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.royaltySplits(body, "splits"));
  }

  static Map<String, Object> buildDeposit(
      ISongRoyaltyService service, Long depositId, Map<String, Object> body) {
    return service.buildDeposit(depositId, HttpBody.raw(body, "walletPubkey"));
  }

  static RoyaltyDeposit confirmDeposit(
      ISongRoyaltyService service, Long depositId, Map<String, Object> body) {
    return service.confirmDeposit(
        depositId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildPaySync(
      ISongRoyaltyService service, Long dealId, Map<String, Object> body) {
    return service.buildPaySync(dealId, HttpBody.raw(body, "walletPubkey"));
  }

  static SyncLicenseDeal confirmPaySync(
      ISongRoyaltyService service, Long dealId, Map<String, Object> body) {
    return service.confirmPaySync(
        dealId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildWithdraw(ITreasuryService service, Map<String, Object> body) {
    return service.buildWithdraw(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.decimal(body, "amountUsdc"));
  }

  static TreasuryWithdrawal confirmWithdraw(ITreasuryService service, Map<String, Object> body) {
    return service.confirmWithdraw(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.decimal(body, "amountUsdc"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildRegister(
      IVenueBookingOnchainService service, Long venueId, Map<String, Object> body) {
    return service.buildRegister(venueId, HttpBody.raw(body, "walletPubkey"));
  }

  static Venue confirmRegister(
      IVenueBookingOnchainService service, Long venueId, Map<String, Object> body) {
    return service.confirmRegister(
        venueId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildApprove(
      IVenueBookingOnchainService service, Long venueId, Map<String, Object> body) {
    return service.buildApprove(venueId, HttpBody.raw(body, "walletPubkey"));
  }

  static Venue confirmApprove(
      IVenueBookingOnchainService service, Long venueId, Map<String, Object> body) {
    return service.confirmApprove(
        venueId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildPropose(
      IVenueBookingOnchainService service, Long bookingId, Map<String, Object> body) {
    return service.buildPropose(
        bookingId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.decimal(body, "grossUsdc"));
  }

  static Booking confirmPropose(
      IVenueBookingOnchainService service, Long bookingId, Map<String, Object> body) {
    return service.confirmPropose(
        bookingId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.decimal(body, "grossUsdc"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildConfirm(
      IVenueBookingOnchainService service, Long bookingId, Map<String, Object> body) {
    return service.buildConfirm(bookingId, HttpBody.raw(body, "walletPubkey"));
  }

  static Booking confirmConfirm(
      IVenueBookingOnchainService service, Long bookingId, Map<String, Object> body) {
    return service.confirmConfirm(
        bookingId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildCancel(
      IVenueBookingOnchainService service, Long bookingId, Map<String, Object> body) {
    return service.buildCancel(bookingId, HttpBody.raw(body, "walletPubkey"));
  }

  static Booking confirmCancel(
      IVenueBookingOnchainService service, Long bookingId, Map<String, Object> body) {
    return service.confirmCancel(
        bookingId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  static Map<String, Object> buildSettle(
      IVenueBookingOnchainService service, Long bookingId, Map<String, Object> body) {
    return service.buildSettle(bookingId, HttpBody.raw(body, "walletPubkey"));
  }

  static ConcertSettlement confirmSettle(
      IVenueBookingOnchainService service, Long bookingId, Map<String, Object> body) {
    return service.confirmSettle(
        bookingId,
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }
}
