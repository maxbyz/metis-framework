package eu.europeana.enrichment.service.dao;

import com.mongodb.client.MongoClient;
import dev.morphia.Datastore;
import dev.morphia.DeleteOptions;
import dev.morphia.Morphia;
import dev.morphia.mapping.DiscriminatorFunction;
import dev.morphia.mapping.Mapper;
import dev.morphia.mapping.MapperOptions;
import dev.morphia.mapping.NamingStrategy;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.experimental.filters.Filter;
import dev.morphia.query.experimental.filters.Filters;
import eu.europeana.enrichment.internal.model.AbstractEnrichmentEntity;
import eu.europeana.enrichment.internal.model.EnrichmentTerm;
import eu.europeana.enrichment.utils.EntityType;
import eu.europeana.metis.mongo.utils.MorphiaUtils;
import eu.europeana.metis.network.ExternalRequestUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;

/**
 * Data Access Object for accessing enrichment entities from Mongo.
 *
 * @author Simon Tzanakis
 * @since 2020-07-07
 */
public class EnrichmentDao {

  private final Datastore datastore;

  public static final String ID_FIELD = "_id";
  public static final String ENTITY_TYPE_FIELD = "entityType";
  public static final String ENTITY_ABOUT_FIELD = "enrichmentEntity.about";
  public static final String ENTITY_OWL_SAME_AS_FIELD = "enrichmentEntity.owlSameAs";
  private static final String UPDATED_FIELD = "updated";
  public static final String LABEL_INFOS_FIELD = "labelInfos";
  public static final String LABEL_FIELD = "lowerCaseLabel";
  public static final String LANG_FIELD = "lang";

  /**
   * Parameter constructor.
   *
   * @param mongoClient the previously initialized mongo client
   * @param databaseName the database name
   */
  public EnrichmentDao(MongoClient mongoClient, String databaseName) {
    final MapperOptions mapperOptions = MapperOptions.builder().discriminatorKey("className")
        .discriminator(DiscriminatorFunction.className())
        .collectionNaming(NamingStrategy.identity()).build();
    this.datastore = Morphia.createDatastore(mongoClient, databaseName, mapperOptions);
    final Mapper mapper = this.datastore.getMapper();
    mapper.map(EnrichmentTerm.class);
    this.datastore.ensureIndexes();
  }

  /**
   * Get the enrichmentTerm by using a provided field name and it's value.
   * <p>Returns the first entity found</p>
   *
   * @param fieldName the field name
   * @param fieldValue the field value
   * @return the retrieved enrichment term
   */
  public Optional<EnrichmentTerm> getEnrichmentTermByField(String fieldName, Object fieldValue) {
    return ExternalRequestUtil.retryableExternalRequestForNetworkExceptions(() -> Optional
        .ofNullable(
            this.datastore.find(EnrichmentTerm.class).filter(Filters.eq(fieldName, fieldValue))
                .first()));
  }

  /**
   * Get the enrichmentTerm {@link ObjectId} by using a provided field name and it's value.
   * <p>Returns the first entity found</p>
   *
   * @param fieldName the field name
   * @param fieldValue the field value
   * @return the retrieved enrichment term object id if present
   */
  public Optional<ObjectId> getEnrichmentTermObjectIdByField(String fieldName, Object fieldValue) {
    return ExternalRequestUtil.retryableExternalRequestForNetworkExceptions(() -> {
      final Optional<EnrichmentTerm> enrichmentTerm = Optional.ofNullable(
          this.datastore.find(EnrichmentTerm.class).filter(Filters.eq(fieldName, fieldValue))
              .first(new FindOptions().projection().include("_id")));
      return enrichmentTerm.map(EnrichmentTerm::getId);
    });
  }

  /**
   * Get a list of enrichmentTerm by using a provided pair of field names and values.
   * <p>Convenience method to avoid needless list generation if {@link
   * #getAllEnrichmentTermsByFieldsInList} was used. Order of supplied field pairs matter on the
   * query performance.</p>
   * <p>It accepts a map that contains a mapping of a field(used for searching in a field that is
   * a list with internal fields), with the list of pairs of fields inside that key. If we are
   * searching for fields only and not inside a list with internal fields(not confused with values),
   * then the key should be null and the corresponding list should contain the field name and value
   * pairs.</p>
   *
   * @param containingListFieldNameAndValues the map of fields and an internal list of pairs with
   * key being the fieldName and value being the fieldValue.
   * @return the retrieved list of enrichmentTerm
   */
  public List<EnrichmentTerm> getAllEnrichmentTermsByFields(
      Map<String, List<Pair<String, String>>> containingListFieldNameAndValues) {
    final Query<EnrichmentTerm> query = datastore.find(EnrichmentTerm.class);
    for (Map.Entry<String, List<Pair<String, String>>> entry : containingListFieldNameAndValues
        .entrySet()) {

      final List<Filter> filters = new ArrayList<>(entry.getValue().size());
      for (Pair<String, String> fieldNameAndValue : entry.getValue()) {
        filters.add(Filters.eq(fieldNameAndValue.getKey(), fieldNameAndValue.getValue()));
      }
      if (StringUtils.isNotBlank(entry.getKey())) {
        query.filter(Filters.elemMatch(entry.getKey(), filters.toArray(Filter[]::new)));
      } else {
        query.filter(filters.toArray(Filter[]::new));
      }
    }
    return MorphiaUtils.getListOfQueryRetryable(query);
  }

  /**
   * Get a list of enrichmentTerm by using a provided pair of field names and per field name a list
   * of values.
   * <p>Order of supplied field pairs matter on the query performance.</p>
   *
   * @param fieldNameAndValues the list of pairs with key being the fieldName and value being a list
   * of fieldValues
   * @return the retrieved list of enrichmentTerm
   */
  public List<EnrichmentTerm> getAllEnrichmentTermsByFieldsInList(
      List<Pair<String, List<String>>> fieldNameAndValues) {
    final Query<EnrichmentTerm> query = datastore.find(EnrichmentTerm.class);
    for (Pair<String, List<String>> fieldNameAndValue : fieldNameAndValues) {
      query.filter(Filters.in(fieldNameAndValue.getKey(), fieldNameAndValue.getValue()));
    }
    return MorphiaUtils.getListOfQueryRetryable(query);
  }

  /**
   * Get the date of the latest modified entity in {@link EnrichmentTerm}.
   * <p>The {@code entityType} parameter is used to filter the specific entities</p>
   *
   * @param entityType the entity type
   * @return the date of the latest modified entity
   */
  public Date getDateOfLastUpdatedEnrichmentTerm(EntityType entityType) {
    Query<EnrichmentTerm> query = datastore.find(EnrichmentTerm.class);
    query.filter(Filters.eq(ENTITY_TYPE_FIELD, entityType));
    final EnrichmentTerm enrichmentTerm = ExternalRequestUtil
        .retryableExternalRequestForNetworkExceptions(
            () -> query.first(new FindOptions().sort(Sort.descending(UPDATED_FIELD))));

    Date dateUpdated = null;
    if (Objects.nonNull(enrichmentTerm)) {
      dateUpdated = enrichmentTerm.getUpdated();
    }
    return dateUpdated;
  }

  /**
   * Save an enrichmentTerm in the database
   *
   * @param enrichmentTerm the item to save
   * @return the key of the saved item
   */
  public String saveEnrichmentTerm(EnrichmentTerm enrichmentTerm) {
    EnrichmentTerm enrichmentTermSaved = ExternalRequestUtil
        .retryableExternalRequestForNetworkExceptions(() -> this.datastore.save(enrichmentTerm));
    return enrichmentTermSaved == null ? StringUtils.EMPTY : enrichmentTermSaved.getId().toString();
  }

  /**
   * Delete enrichmentTerms that match the provided entity abouts.
   * <p>Removes entities from the corresponding enrichmentTerm using {@code entityType}.
   * It also removes entities that match with the provided entity abouts with owlSameAs.</p>
   *
   * @param entityType the entity type string
   * @param entityAbout the entity abouts to match
   * @return a list of all the removed uris except the provided ones
   */
  public List<String> deleteEnrichmentTerms(EntityType entityType, List<String> entityAbout) {
    //Remove from EnrichmentTerm
    deleteEnrichmentTerms(entityAbout);

    //Find all TermLists that have owlSameAs equals with entity about
    final Query<EnrichmentTerm> enrichmentTermsSameAsQuery = this.datastore
        .find(EnrichmentTerm.class).filter(Filters.eq(ENTITY_TYPE_FIELD, entityType))
        .filter(Filters.in(ENTITY_OWL_SAME_AS_FIELD, entityAbout));
    final List<EnrichmentTerm> enrichmentTermsOwlSameAs = MorphiaUtils
        .getListOfQueryRetryable(enrichmentTermsSameAsQuery);
    final List<String> sameAsUris = enrichmentTermsOwlSameAs.stream()
        .map(EnrichmentTerm::getEnrichmentEntity).map(AbstractEnrichmentEntity::getAbout)
        .collect(Collectors.toList());
    //Remove from EnrichmentTerm
    deleteEnrichmentTerms(sameAsUris);
    return sameAsUris;
  }

  private void deleteEnrichmentTerms(List<String> entityAbout) {
    ExternalRequestUtil.retryableExternalRequestForNetworkExceptions(
        () -> this.datastore.find(EnrichmentTerm.class)
            .filter(Filters.in(ENTITY_ABOUT_FIELD, entityAbout))
            .delete(new DeleteOptions().multi(true)));
  }

  /**
   * Get the total number of documents
   *
   * @return the total number of documents
   */
  protected long count() {
    return datastore.find(EnrichmentTerm.class).count();
  }

  /**
   * Get the connected mapper
   *
   * @return the mapper
   */
  protected Mapper getMapper() {
    return datastore.getMapper();
  }

  /**
   * Purge the database
   */
  protected void purgeDatabase() {
    datastore.find(EnrichmentTerm.class).delete(new DeleteOptions().multi(true));
  }
}
