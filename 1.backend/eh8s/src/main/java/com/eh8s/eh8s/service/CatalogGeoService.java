package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.ChannelPiece;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoRegion;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltySplit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyType;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.TourPlan;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import com.eh8s.eh8s.repository.interfaces.ICatalogGeoRepository;
import com.eh8s.eh8s.service.interfaces.ICatalogGeoService;
import com.eh8s.eh8s.service.solana.SubscribeAcademyIxBuilder;
import com.eh8s.eh8s.service.solana.SubscribeGeographicIxBuilder;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Off-chain catalog and geo CMS mutations. DevNet deposit/subscribe confirm lives on
 * {@link RoyaltyGeoOnchainService}.
 */
@Service
public class CatalogGeoService implements ICatalogGeoService {

  private final ICatalogGeoRepository catalogGeoRepository;

  public CatalogGeoService(ICatalogGeoRepository catalogGeoRepository) {
    this.catalogGeoRepository = catalogGeoRepository;
  }

  @Override
  public List<Track> tracks() {
    return catalogGeoRepository.findTracks();
  }

  @Override
  public Track createTrack(Track track) {
    if (track.getTitle() == null || track.getTitle().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
    }
    if (track.getProvider() == null || track.getProvider().isBlank()) {
      track.setProvider("eh8s");
    }
    return catalogGeoRepository.insertTrack(track);
  }

  @Override
  public List<RoyaltySplit> splits(Long trackId) {
    return catalogGeoRepository.findSplits(trackId);
  }

  @Override
  public List<RoyaltyType> royaltyTypes() {
    return catalogGeoRepository.findRoyaltyTypes();
  }

  @Override
  public List<ChannelPiece> channelPieces() {
    return catalogGeoRepository.findChannelPieces();
  }

  @Override
  public ChannelPiece createChannelPiece(ChannelPiece piece) {
    if (piece.getArtistShareBps() == null) {
      piece.setArtistShareBps(8000);
    }
    if (piece.getEh8sShareBps() == null) {
      piece.setEh8sShareBps(2000);
    }
    return catalogGeoRepository.insertChannelPiece(piece);
  }

  @Override
  public List<GeoTier> geoTiers() {
    return catalogGeoRepository.findGeoTiers();
  }

  @Override
  public List<GeographicSubscription> geoSubscriptions() {
    return catalogGeoRepository.findGeoSubscriptions();
  }

  @Override
  public List<GeoRegion> geoRegions() {
    return catalogGeoRepository.findGeoRegions();
  }

  /**
   * {@inheritDoc}
   *
   * <p>The zone seed defaults to the region's on-chain code; a finer code must extend it (for
   * example {@code MEX_CDMX_CENTRO} under {@code MEX_CDMX}).
   */
  @Override
  public GeographicSubscription subscribeGeo(GeographicSubscription subscription) {
    if (subscription.getGeoTierId() == null
        || catalogGeoRepository.findGeoTier(subscription.getGeoTierId()).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown geo tier");
    }
    GeoRegion region =
        subscription.getGeoRegionId() == null
            ? null
            : catalogGeoRepository.findGeoRegion(subscription.getGeoRegionId()).orElse(null);
    if (region == null || region.getOnchainGeoCode() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown geo region");
    }
    String geoCode =
        subscription.getGeoCode() == null || subscription.getGeoCode().isBlank()
            ? region.getOnchainGeoCode()
            : subscription.getGeoCode().trim().toUpperCase();
    if (!SubscribeGeographicIxBuilder.GEO_CODE.matcher(geoCode).matches()
        || !geoCode.startsWith(region.getOnchainGeoCode())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "geoCode must be 2..24 of A-Z 0-9 _ and extend the region code " + region.getOnchainGeoCode());
    }
    subscription.setGeoCode(geoCode);
    int months = subscription.getMonths() == null ? 1 : subscription.getMonths();
    if (months < 1 || months > SubscribeAcademyIxBuilder.MAX_MONTHS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "months must be 1..12");
    }
    subscription.setMonths((byte) months);
    if (subscription.getStartsAt() == null) {
      subscription.setStartsAt(LocalDate.now());
    }
    if (subscription.getExpiresAt() == null) {
      subscription.setExpiresAt(subscription.getStartsAt().plusMonths(months));
    }
    if (subscription.getIntendedInstruction() == null
        || subscription.getIntendedInstruction().isBlank()) {
      subscription.setIntendedInstruction("subscribe_geographic");
    }
    return catalogGeoRepository.insertGeoSubscription(subscription);
  }

  @Override
  public List<TourPlan> tourPlans() {
    return catalogGeoRepository.findTourPlans();
  }

  @Override
  public TourPlan createTourPlan(TourPlan plan) {
    if (plan.getRouteNote() == null) {
      plan.setRouteNote("ATLAS draft route");
    }
    return catalogGeoRepository.insertTourPlan(plan);
  }

  @Override
  public List<RoyaltyDeposit> royaltyDeposits() {
    return catalogGeoRepository.findRoyaltyDeposits();
  }

  @Override
  public RoyaltyDeposit depositRoyalty(RoyaltyDeposit deposit) {
    if (deposit.getIntendedInstruction() == null || deposit.getIntendedInstruction().isBlank()) {
      deposit.setIntendedInstruction("deposit_royalties");
    }
    return catalogGeoRepository.insertRoyaltyDeposit(deposit);
  }
}
