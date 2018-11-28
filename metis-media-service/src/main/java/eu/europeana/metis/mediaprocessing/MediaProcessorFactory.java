package eu.europeana.metis.mediaprocessing;

import eu.europeana.metis.mediaprocessing.temp.TemporaryMediaProcessor;

public class MediaProcessorFactory {

  private static final int DEFAULT_REDIRECT_COUNT = 3;
  private static final int DEFAULT_GENERAL_CONNECTION_LIMIT = 200;
  private static final int DEFAULT_CONNECTION_LIMIT_PER_SOURCE = 4;

  private int redirectCount = DEFAULT_REDIRECT_COUNT;
  private int generalConnectionLimit = DEFAULT_GENERAL_CONNECTION_LIMIT;
  private int connectionLimitPerSource = DEFAULT_CONNECTION_LIMIT_PER_SOURCE;

  public void setRedirectCount(int redirectCount) {
    this.redirectCount = redirectCount;
  }

  public void setGeneralConnectionLimit(int generalConnectionLimit) {
    this.generalConnectionLimit = generalConnectionLimit;
  }

  public void setConnectionLimitPerSource(int connectionLimitPerSource) {
    this.connectionLimitPerSource = connectionLimitPerSource;
  }

  public MediaProcessor createMediaProcessor() throws MediaProcessorException {
    return new TemporaryMediaProcessor(redirectCount, generalConnectionLimit,
        connectionLimitPerSource);
  }

}
