package eu.europeana.indexing.tiers.media;

import static java.util.Objects.requireNonNullElse;

import eu.europeana.indexing.tiers.model.MediaTier;
import eu.europeana.indexing.tiers.model.Tier;
import eu.europeana.indexing.tiers.model.TierClassifier;
import eu.europeana.indexing.tiers.view.ContentTierBreakdown;
import eu.europeana.indexing.tiers.view.MediaResourceTechnicalMetadata;
import eu.europeana.indexing.tiers.view.MediaResourceTechnicalMetadata.MediaResourceTechnicalMetadataBuilder;
import eu.europeana.indexing.tiers.view.ResolutionTierMetadata;
import eu.europeana.indexing.utils.LicenseType;
import eu.europeana.indexing.utils.RdfWrapper;
import eu.europeana.indexing.utils.WebResourceLinkType;
import eu.europeana.indexing.utils.WebResourceWrapper;
import eu.europeana.metis.schema.model.MediaType;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is the superclass of all classifiers for specific media types. Classification happens both for the entity as a whole and
 * on individual web resources. This class provides methods for both cases so that subclasses can implement them.
 */
public abstract class AbstractMediaClassifier implements TierClassifier<MediaTier, ContentTierBreakdown> {

  @Override
  public final TierClassification<MediaTier, ContentTierBreakdown> classify(RdfWrapper entity) {

    // Look at the entity as a whole: we may classify without considering the web resources.
    final MediaTier entityTier = preClassifyEntity(entity);
    if (entityTier != null) {
      return new TierClassification<>(entityTier, new ContentTierBreakdown.Builder()
                      .setMediaResourceTechnicalMetadataList(Collections.emptyList())
                      .build());
    }

    // Find candidate web resources
    final List<WebResourceWrapper> webResources = entity.getWebResourceWrappers(EnumSet.of(
        WebResourceLinkType.HAS_VIEW, WebResourceLinkType.IS_SHOWN_BY));
    //Collect common flags
    final boolean hasLandingPage = entity.hasLandingPage();
    final boolean hasThumbnails = entity.hasThumbnails();
    final boolean hasEmbeddableMedia = EmbeddableMedia.hasEmbeddableMedia(entity);
    LicenseType entityLicenseType = entity.getLicenseType();

    // Compute the media tier based on whether it has suitable web resources.
    final MediaTier mediaTier;
    final MediaTier mediaTierBeforeLicenseCorrection;
    List<MediaResourceTechnicalMetadata> mediaResourceTechnicalMetadataList = new LinkedList<>();
    if (webResources.isEmpty()) {
      mediaTier = classifyEntityWithoutWebResources(entity, hasLandingPage);
      mediaTierBeforeLicenseCorrection = mediaTier;
    } else {
      final List<MediaResourceTechnicalMetadata> descendingMediaResourceTechnicalMetadata =
          webResources.stream().map(
                          resource -> classifyWebResourceAndLicense(resource, entityLicenseType, hasLandingPage, hasEmbeddableMedia))
                      .sorted(Comparator.comparing(MediaResourceTechnicalMetadata::getMediaTier, Tier.getComparator().reversed()))
                      .collect(Collectors.toList());
      //Get the highest value or else default
      mediaTier = descendingMediaResourceTechnicalMetadata.stream().map(MediaResourceTechnicalMetadata::getMediaTier).findFirst()
                                                          .orElse(MediaTier.T0);
      //Get the highest value of content tier before license correction or else default
      mediaTierBeforeLicenseCorrection = descendingMediaResourceTechnicalMetadata.stream()
          .map(MediaResourceTechnicalMetadata::getMediaTierBeforeLicenseCorrection)
          .findFirst()
          .orElse(MediaTier.T0);
      mediaResourceTechnicalMetadataList = descendingMediaResourceTechnicalMetadata;
    }
    MediaType mediaTypeResult = getMediaType();
    boolean hasMediaResource3DAvailable = mediaTypeResult == MediaType.THREE_D && (mediaTier != MediaTier.T0 && mediaTier != MediaTier.T1);

    final ContentTierBreakdown contentTierBreakdown = new ContentTierBreakdown.Builder()
            .setRecordType(mediaTypeResult)
            .setMediaTierBeforeLicenseCorrection(mediaTierBeforeLicenseCorrection)
            .setLicenseType(entityLicenseType)
            .setThumbnailAvailable(hasThumbnails)
            .setLandingPageAvailable(hasLandingPage)
            .setMediaResource3DAvailable(hasMediaResource3DAvailable)
            .setEmbeddableMediaAvailable(hasEmbeddableMedia)
            .setMediaResourceTechnicalMetadataList(mediaResourceTechnicalMetadataList).build();

    return new TierClassification<>(mediaTier, contentTierBreakdown);
  }

  /**
   * This method is used to classify a web resource. If this method is called for an entity, the method {@link
   * #classifyEntityWithoutWebResources(RdfWrapper, boolean)} is not called for it. Otherwise, it is, once for each suitable web
   * resource.
   *
   * @param webResource The web resource.
   * @param entityLicense The license type that holds for the entity. If the web resource does not have a license, then this
   * license will be used for the web resource during the tier calculation. If it's null it will be set to {@link
   * LicenseType#CLOSED}.
   * @param hasLandingPage Whether the entity has a landing page.
   * @param hasEmbeddableMedia Whether the entity has embeddable media.
   * @return The classification.
   */
  MediaResourceTechnicalMetadata classifyWebResourceAndLicense(WebResourceWrapper webResource, LicenseType entityLicense,
      boolean hasLandingPage, boolean hasEmbeddableMedia) {

    // Perform the classification on the basis of the media.
    final MediaTier resourceTier = classifyWebResource(webResource, hasLandingPage, hasEmbeddableMedia);
    // Get web resource license, if not present use entity license for the calculation
    final LicenseType webResourceLicense = webResource.getLicenseType()
                                                      .orElse(requireNonNullElse(entityLicense, LicenseType.CLOSED));
    // Lower the media tier if forced by the license - find the minimum of the two.
    final MediaTier mediaTier = Tier.min(resourceTier, webResourceLicense.getMediaTier());

    //Verify and prepare builder with resolution initialized data
    final ResolutionTierMetadata resolutionTierMetadata = extractResolutionTierMetadata(webResource, resourceTier);
    final MediaResourceTechnicalMetadataBuilder mediaResourceTechnicalMetadataBuilder = new MediaResourceTechnicalMetadataBuilder(
        resolutionTierMetadata);

    //Set common fields
    mediaResourceTechnicalMetadataBuilder.setResourceUrl(webResource.getAbout())
                                         .setMediaType(webResource.getMediaType())
                                         .setMimeType(webResource.getMimeType())
                                         .setElementLinkTypes(webResource.getLinkTypes())
                                         .setLicenseType(webResourceLicense)
                                         .setMediaTier(mediaTier)
                                         .setMediaTierBeforeLicenseCorrection(resourceTier);

    return mediaResourceTechnicalMetadataBuilder.build();
  }

  /**
   * This method is used to 'preprocess' the entity: we may be able to classify the record without looking at the web resources.
   * This method is always called.
   *
   * @param entity The entity to classify.
   * @return The classification of this entity. Returns null if we can not do it without looking at the web resources.
   */
  abstract MediaTier preClassifyEntity(RdfWrapper entity);

  /**
   * This method is used to classify an entity without suitable web resources. If this method is called for an entity, the method
   * {@link #classifyWebResource(WebResourceWrapper, boolean, boolean)} is not called for it. Otherwise, it is.
   *
   * @param entity The entity to classify.
   * @param hasLandingPage Whether the entity has a landing page.
   * @return The classification of this entity. Is not null.
   */
  abstract MediaTier classifyEntityWithoutWebResources(RdfWrapper entity, boolean hasLandingPage);

  /**
   * This method is used to classify a web resource. It does not need to concern itself with the license, it should be concerned
   * with the media only (and returns the highest classification that the media could get under any license). If this method is
   * called for an entity, the method {@link #classifyEntityWithoutWebResources(RdfWrapper, boolean)} is not called for it.
   * Otherwise, it is, once for each suitable web resource.
   *
   * @param webResource The web resource.
   * @param hasEmbeddableMedia Whether the entity has embeddable media.
   * @param hasLandingPage Whether the entity has a landing page.
   * @return The classification.
   */
  abstract MediaTier classifyWebResource(WebResourceWrapper webResource, boolean hasLandingPage, boolean hasEmbeddableMedia);

  abstract ResolutionTierMetadata extractResolutionTierMetadata(WebResourceWrapper webResource, MediaTier mediaTier);

  abstract MediaType getMediaType();
}
