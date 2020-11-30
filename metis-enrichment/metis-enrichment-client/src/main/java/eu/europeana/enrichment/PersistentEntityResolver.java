package eu.europeana.enrichment;

import eu.europeana.enrichment.api.external.model.EnrichmentBase;
import eu.europeana.enrichment.internal.model.EnrichmentTerm;
import eu.europeana.enrichment.service.Converter;
import eu.europeana.enrichment.service.dao.EnrichmentDao;
import eu.europeana.enrichment.utils.EntityType;
import eu.europeana.metis.schema.jibx.LanguageCodes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistentEntityResolver implements EntityResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(PersistentEntityResolver.class);
  private static final Set<String> ALL_2CODE_LANGUAGES = new HashSet<>();
  private static final Map<String, String> ALL_3CODE_TO_2CODE_LANGUAGES = new HashMap<>();
  private static final Pattern PATTERN_MATCHING_VERY_BROAD_TIMESPANS = Pattern
      .compile("http://semium.org/time/(ChronologicalPeriod$|Time$|(AD|BC)[1-9]x{3}$)");

  static {
    Arrays.stream(Locale.getISOLanguages()).map(Locale::new).forEach(locale -> {
      ALL_2CODE_LANGUAGES.add(locale.getLanguage());
      ALL_3CODE_TO_2CODE_LANGUAGES.put(locale.getISO3Language(), locale.getLanguage());
    });
  }

  private final EnrichmentDao enrichmentDao;

  public PersistentEntityResolver(EnrichmentDao enrichmentDao) {
    this.enrichmentDao = enrichmentDao;
  }

  @Override
  public Map<SearchTerm, List<EnrichmentBase>> resolveByText(Set<SearchTerm> searchTermSet) {
    final Map<SearchTerm, List<EnrichmentBase>> result = new HashMap<>();
    try {
      for (SearchTerm searchTerm : searchTermSet) {
        findEnrichmentEntitiesBySearchTerm(result, searchTerm);
      }
    } catch (RuntimeException e) {
      LOGGER.warn("Unable to retrieve entity from tag", e);
    }
    return result;
  }

  @Override
  public Map<ReferenceTerm, EnrichmentBase> resolveById(Set<ReferenceTerm> referenceTermSet) {
    Map<ReferenceTerm, EnrichmentBase> result = new HashMap<>();
    for (ReferenceTerm value : referenceTermSet) {
      try {
        EnrichmentBase foundEnrichmentBases = getEnrichmentTermAndConvert(
            new ImmutablePair<>(EnrichmentDao.ENTITY_ABOUT_FIELD, value.getReference().toString()));
        if (foundEnrichmentBases != null) {
          result.put(value, foundEnrichmentBases);
        }
      } catch (RuntimeException e) {
        LOGGER.warn("Unable to retrieve entity from entityAbout", e);
      }
    }
    return result;
  }

  @Override
  public Map<ReferenceTerm, List<EnrichmentBase>> resolveByUri(Set<ReferenceTerm> referenceTermSet) {
    Map<ReferenceTerm, List<EnrichmentBase>> result = new HashMap<>();

    for(ReferenceTerm referenceTerm: referenceTermSet){
      try {
        final List<EntityType> entityTypes = referenceTerm.getCandidateTypes();
        List<EnrichmentBase> foundEnrichmentBases = new ArrayList<>();
        if (CollectionUtils.isEmpty(entityTypes)) {
          foundEnrichmentBases = searchBasesFirstAboutThenOwlSameAs(referenceTerm.getReference().toString(),
              null);
        } else {
          for (EntityType entityType : entityTypes) {
            foundEnrichmentBases = searchBasesFirstAboutThenOwlSameAs(referenceTerm.getReference().toString(),
                entityType);
          }
        }

        if (CollectionUtils.isNotEmpty(foundEnrichmentBases)) {
          result.put(referenceTerm, foundEnrichmentBases);
        }
      } catch (RuntimeException e) {
        LOGGER.warn("Unable to retrieve entity from id", e);
      }
    }

    return result;
  }

  private void findEnrichmentEntitiesBySearchTerm(
      Map<SearchTerm, List<EnrichmentBase>> searchTermListMap, SearchTerm searchTerm) {
    final String value = searchTerm.getTextValue().toLowerCase(Locale.US);
    if (!StringUtils.isBlank(value)) {
      final List<EntityType> entityTypes = searchTerm.getCandidateTypes();
      //Language has to be a valid 2 or 3 code, otherwise we do not use it
      final LanguageCodes inputValueLanguage = searchTerm.getLanguage();
      final String language;
      if (inputValueLanguage != null && inputValueLanguage.name().length() == 3) {
        language = ALL_3CODE_TO_2CODE_LANGUAGES.get(inputValueLanguage.name());
      } else if (inputValueLanguage != null && inputValueLanguage.name().length() == 2) {
        language =
            ALL_2CODE_LANGUAGES.contains(inputValueLanguage.name()) ? inputValueLanguage.name()
                : null;
      } else {
        language = null;
      }

      if (CollectionUtils.isEmpty(entityTypes)) {
        searchTermListMap
            .put(searchTerm, findEnrichmentTerms(null, value, language));
      } else {
        for (EntityType entityType : entityTypes) {
          searchTermListMap.put(
              searchTerm, findEnrichmentTerms(entityType, value, language));
        }
      }
    }
  }

  private List<EnrichmentBase> findEnrichmentTerms(EntityType entityType, String termLabel,
      String termLanguage) {

    final HashMap<String, List<Pair<String, String>>> fieldNameMap = new HashMap<>();
    //Find all terms that match label and language. Order of Pairs matter for the query performance.
    final List<Pair<String, String>> labelInfosFields = new ArrayList<>();
    labelInfosFields.add(new ImmutablePair<>(EnrichmentDao.LABEL_FIELD, termLabel));
    //If language not defined we are searching without specifying the language
    if (StringUtils.isNotBlank(termLanguage)) {
      labelInfosFields.add(new ImmutablePair<>(EnrichmentDao.LANG_FIELD, termLanguage));
    }

    final List<Pair<String, String>> enrichmentTermFields = new ArrayList<>();

    if (entityType != null) {
      enrichmentTermFields
          .add(new ImmutablePair<>(EnrichmentDao.ENTITY_TYPE_FIELD, entityType.name()));
    }
    fieldNameMap.put(EnrichmentDao.LABEL_INFOS_FIELD, labelInfosFields);
    fieldNameMap.put(null, enrichmentTermFields);
    final List<EnrichmentTerm> enrichmentTerms = enrichmentDao
        .getAllEnrichmentTermsByFields(fieldNameMap);
    final List<EnrichmentTerm> parentEnrichmentTerms = enrichmentTerms.stream()
        .map(this::findParentEntities).flatMap(List::stream).collect(Collectors.toList());

    final List<EnrichmentBase> enrichmentBases = new ArrayList<>();
    //Convert to EnrichmentBases
    enrichmentBases.addAll(Converter.convert(enrichmentTerms));
    enrichmentBases.addAll(Converter.convert(parentEnrichmentTerms));

    return enrichmentBases;
  }

  private List<EnrichmentTerm> findParentEntities(EnrichmentTerm enrichmentTerm) {
    final Set<String> parentAbouts = new HashSet<>();
    final List<EnrichmentTerm> parentEntities = new ArrayList<>();
    Predicate<String> isTimespanVeryBroad = parent ->
        enrichmentTerm.getEntityType().equals(EntityType.TIMESPAN)
            && PATTERN_MATCHING_VERY_BROAD_TIMESPANS.matcher(parent).matches();
    String parentAbout = enrichmentTerm.getEnrichmentEntity().getIsPartOf();
    while (StringUtils.isNotBlank(parentAbout) && !isTimespanVeryBroad.test(parentAbout)) {
      EnrichmentTerm currentEnrichmentTerm = enrichmentDao
          .getEnrichmentTermByField(EnrichmentDao.ENTITY_ABOUT_FIELD, parentAbout).orElse(null);
      //Break when there is no other parent available or when we have already encountered the
      // same about
      if (currentEnrichmentTerm == null || !parentAbouts.add(parentAbout)) {
        break;
      }
      parentEntities.add(currentEnrichmentTerm);
      parentAbout = currentEnrichmentTerm.getEnrichmentEntity().getIsPartOf();
    }
    return parentEntities;
  }

  private List<EnrichmentBase> searchBasesFirstAboutThenOwlSameAs(String reference,
      EntityType entityType) {
    final Pair<String, String> parameterAbout = new ImmutablePair<>(
        EnrichmentDao.ENTITY_ABOUT_FIELD, reference);
    final Pair<String, String> parameterOwlSameAs = new ImmutablePair<>(
        EnrichmentDao.ENTITY_OWL_SAME_AS_FIELD, reference);

    List<EnrichmentBase> foundEnrichmentBases;
    final List<Pair<String, String>> parametersAbout = new ArrayList<>();
    final List<Pair<String, String>> parametersOwlSameAs = new ArrayList<>();
    if (entityType != null) {
      final ImmutablePair<String, String> parameterEntityType = new ImmutablePair<>(
          EnrichmentDao.ENTITY_TYPE_FIELD, entityType.name());
      parametersAbout.add(parameterEntityType);
      parametersOwlSameAs.add(parameterEntityType);
    }
    // Get by about first
    parametersAbout.add(parameterAbout);
    foundEnrichmentBases = parametersAbout.stream().map(this::getEnrichmentTermAndConvert)
        .collect(Collectors.toList());
    if (CollectionUtils.isEmpty(foundEnrichmentBases)) {
      // If empty try OwlSameAs
      parametersOwlSameAs.add(parameterOwlSameAs);
      foundEnrichmentBases = parametersOwlSameAs.stream().map(this::getEnrichmentTermAndConvert).collect(
          Collectors.toList());
    }
    return foundEnrichmentBases;
  }

  private EnrichmentBase getEnrichmentTermAndConvert(Pair<String, String> pair) {
    final Optional<EnrichmentTerm> enrichmentTerm = enrichmentDao
        .getEnrichmentTermByField(pair.getKey(), pair.getValue());
    return enrichmentTerm.isEmpty() ? null : Converter.convert(enrichmentTerm.get());
  }

}
