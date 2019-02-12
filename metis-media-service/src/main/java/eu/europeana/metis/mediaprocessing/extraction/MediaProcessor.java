package eu.europeana.metis.mediaprocessing.extraction;

import eu.europeana.metis.mediaprocessing.exception.MediaExtractionException;
import eu.europeana.metis.mediaprocessing.model.Resource;
import eu.europeana.metis.mediaprocessing.model.ResourceExtractionResult;
import eu.europeana.metis.mediaprocessing.model.UrlType;

/**
 * Implementations of this interface are capable of processing a particular {@link ResourceType}.
 */
interface MediaProcessor {

  /**
   * Process a resource.
   *
   * @param resource The resource to process. Note that the resource may not have content (see
   * {@link ResourceType#shouldDownloadMimetype(String)}).
   * @return The result of the extraction.
   * @throws MediaExtractionException In case something went wrong during the extraction.
   */
  ResourceExtractionResult process(Resource resource) throws MediaExtractionException;

  /**
   * @param resource The resource for which to extract metadata.
   * @return Whether metadata is to be extracted for this resource.
   */
  default boolean shouldExtractMetadata(Resource resource) {
    return UrlType.shouldExtractMetadata(resource.getUrlTypes());
  }
}
