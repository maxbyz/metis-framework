package eu.europeana.indexing.solr;

import static eu.europeana.indexing.utils.IndexingSettingsUtils.nonNullIllegal;

import eu.europeana.indexing.AbstractConnectionProvider;
import eu.europeana.indexing.FullBeanPublisher;
import eu.europeana.indexing.IndexerImpl.IndexingSupplier;
import eu.europeana.indexing.SimpleIndexer;
import eu.europeana.indexing.exception.IndexerRelatedIndexingException;
import eu.europeana.indexing.exception.IndexingException;
import eu.europeana.indexing.exception.RecordRelatedIndexingException;
import eu.europeana.indexing.exception.SetupRelatedIndexingException;
import eu.europeana.indexing.fullbean.StringToFullBeanConverter;
import eu.europeana.indexing.utils.RdfWrapper;
import eu.europeana.metis.schema.jibx.RDF;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a record for indexing in Solr
 */
public class SolrIndexer implements SimpleIndexer {

  private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final AbstractConnectionProvider connectionProvider;
  private final IndexingSupplier<StringToFullBeanConverter> stringToRdfConverterSupplier;

  /**
   * Create SolrIndexer object indicating solr connection properties
   *
   * @param settings solr settings
   * @throws SetupRelatedIndexingException in case an error occurred during indexing setup
   */
  public SolrIndexer(SolrIndexingSettings settings) throws SetupRelatedIndexingException {
    this.connectionProvider = new SolrConnectionProvider(settings);
    this.stringToRdfConverterSupplier = StringToFullBeanConverter::new;
  }

  /**
   * Index to Solr a rdf record object
   *
   * @param rdfRecord An RDF record object
   * @throws IndexingException which can be one of:
   * <ul>
   * <li>{@link IndexerRelatedIndexingException} In case an error occurred during publication.</li>
   * <li>{@link SetupRelatedIndexingException} in case an error occurred during indexing setup</li>
   * <li>{@link RecordRelatedIndexingException} in case an error occurred related to record
   * contents</li>
   * </ul>
   */
  @Override
  public void indexRecord(RDF rdfRecord) throws IndexingException {
    // Sanity checks
    rdfRecord = nonNullIllegal(rdfRecord, "Input RDF cannot be null.");

    LOGGER.info("Processing {} record...", rdfRecord);
    final FullBeanPublisher publisher = connectionProvider.getFullBeanPublisher(false);
    publisher.publishSolr(new RdfWrapper(rdfRecord), Date.from(Instant.now()));
  }

  /**
   * Index to Solr a string rdf record
   *
   * @param stringRdfRecord A rdf record in string format
   * @throws IndexingException which can be one of:
   * <ul>
   * <li>{@link IndexerRelatedIndexingException} In case an error occurred during publication.</li>
   * <li>{@link SetupRelatedIndexingException} in case an error occurred during indexing setup</li>
   * <li>{@link RecordRelatedIndexingException} in case an error occurred related to record
   * contents</li>
   * </ul>
   */
  @Override
  public void indexRecord(String stringRdfRecord) throws IndexingException {
    final RDF rdfRecord = stringToRdfConverterSupplier.get().convertStringToRdf(stringRdfRecord);
    indexRecord(rdfRecord);
  }
}
