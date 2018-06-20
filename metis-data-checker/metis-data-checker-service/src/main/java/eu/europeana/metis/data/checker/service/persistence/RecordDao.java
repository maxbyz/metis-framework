package eu.europeana.metis.data.checker.service.persistence;

import java.io.IOException;
import java.util.regex.Pattern;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.WriteConcern;
import eu.europeana.corelib.definitions.jibx.RDF;
import eu.europeana.corelib.mongo.server.EdmMongoServer;
import eu.europeana.indexing.Indexer;
import eu.europeana.indexing.IndexerFactory;
import eu.europeana.indexing.exception.IndexerConfigurationException;
import eu.europeana.indexing.exception.IndexingException;

/**
 * Record persistence DAO
 * Created by ymamakis on 9/2/16.
 */
@Service
public class RecordDao {

  public static final String ABOUT_STRING = "about";
  private final SolrClient solrServer;
  private final EdmMongoServer mongoServer;
  private final Indexer indexer;

  /**
   * Constructor with required fields.
   *
   * @param solrServer {@link SolrClient}
   * @param mongoServer {@link EdmMongoServer}
   * @throws IndexerConfigurationException In case of problems setting up the indexer.
   */
  @Autowired
  public RecordDao(SolrClient solrServer, EdmMongoServer mongoServer)
      throws IndexerConfigurationException {
    this(solrServer, mongoServer, new IndexerFactory(mongoServer, solrServer).getIndexer());
  }
  
  RecordDao(SolrClient solrServer, EdmMongoServer mongoServer, Indexer indexer) {
    this.solrServer = solrServer;
    this.mongoServer = mongoServer;
    this.indexer = indexer;
  }
  
  Indexer getIndexer() {
    return indexer;
  }

  /**
   * Persist a record in mongo and solr
   *
   * @param rdf The record
   * @throws IndexingException In case indexing failed. 
   */
  public void createRecord(RDF rdf) throws IndexingException {
    getIndexer().indexRdf(rdf);
  }
  
  public void commit() throws IOException, SolrServerException {
    solrServer.commit();
  }

  /**
   * Delete the records persisted over the last 24h
   */
  public void deleteRecordIdsByTimestamp() throws SolrServerException, IOException {
    SolrQuery query = new SolrQuery();
    query.setQuery("*:*");
    solrServer.deleteByQuery(query.getQuery());
    solrServer.commit();
    clearAll();
  }

  private void clearAll() {
    this.mongoServer.getDatastore().getDB().getCollection("record")
        .remove(new BasicDBObject());
    this.mongoServer.getDatastore().getDB().getCollection("Proxy")
        .remove(new BasicDBObject());
    this.mongoServer.getDatastore().getDB().getCollection("Aggregation")
        .remove(new BasicDBObject());
    this.mongoServer.getDatastore().getDB().getCollection("EuropeanaAggregation")
        .remove(new BasicDBObject());
    this.mongoServer.getDatastore().getDB().getCollection("PhysicalThing")
        .remove(new BasicDBObject());
    this.mongoServer.getDatastore().getDB().getCollection("Agent")
        .remove(new BasicDBObject());
    this.mongoServer.getDatastore().getDB().getCollection("Concept")
        .remove(new BasicDBObject());
    this.mongoServer.getDatastore().getDB().getCollection("Place")
        .remove(new BasicDBObject());
    this.mongoServer.getDatastore().getDB().getCollection("Timespan")
        .remove(new BasicDBObject());
    this.mongoServer.getDatastore().getDB().getCollection("WebResource")
        .remove(new BasicDBObject());
    this.mongoServer.getDatastore().getDB().getCollection("Service")
        .remove(new BasicDBObject());
    this.mongoServer.getDatastore().getDB().getCollection("License")
        .remove(new BasicDBObject());
  }
}
