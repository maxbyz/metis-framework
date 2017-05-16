package eu.europeana.metis.core.rest.config;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-05-16
 */
public enum RequestLimits {
  ORGANIZATIONS_PER_REQUEST(5), DATASETS_PER_REQUEST(2);

  private int limit;

  RequestLimits(int limit) {
    this.limit = limit;
  }

  public int getLimit() {
    return limit;
  }
}
