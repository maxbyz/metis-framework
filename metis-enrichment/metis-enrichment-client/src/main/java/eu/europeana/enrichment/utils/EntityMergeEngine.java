package eu.europeana.enrichment.utils;

import static eu.europeana.enrichment.utils.RdfEntityUtils.appendLinkToEuropeanaProxy;
import static eu.europeana.enrichment.utils.RdfEntityUtils.replaceResourceWithLinkInAggregation;
import static eu.europeana.enrichment.utils.RdfEntityUtils.replaceValueWithLinkInAggregation;

import eu.europeana.enrichment.api.external.model.Agent;
import eu.europeana.enrichment.api.external.model.Concept;
import eu.europeana.enrichment.api.external.model.EnrichmentBase;
import eu.europeana.enrichment.api.external.model.Organization;
import eu.europeana.enrichment.api.external.model.Part;
import eu.europeana.enrichment.api.external.model.Place;
import eu.europeana.enrichment.api.external.model.TimeSpan;
import eu.europeana.enrichment.api.internal.AggregationFieldType;
import eu.europeana.enrichment.api.internal.FieldType;
import eu.europeana.enrichment.api.internal.ProxyFieldType;
import eu.europeana.enrichment.api.internal.ReferenceTerm;
import eu.europeana.enrichment.api.internal.ReferenceTermContext;
import eu.europeana.enrichment.api.internal.SearchTermContext;
import eu.europeana.enrichment.rest.client.dereference.DereferencedEntities;
import eu.europeana.metis.schema.jibx.AboutType;
import eu.europeana.metis.schema.jibx.AgentType;
import eu.europeana.metis.schema.jibx.Aggregation;
import eu.europeana.metis.schema.jibx.Alt;
import eu.europeana.metis.schema.jibx.AltLabel;
import eu.europeana.metis.schema.jibx.Begin;
import eu.europeana.metis.schema.jibx.BiographicalInformation;
import eu.europeana.metis.schema.jibx.BroadMatch;
import eu.europeana.metis.schema.jibx.Broader;
import eu.europeana.metis.schema.jibx.CloseMatch;
import eu.europeana.metis.schema.jibx.Concept.Choice;
import eu.europeana.metis.schema.jibx.Date;
import eu.europeana.metis.schema.jibx.DateOfBirth;
import eu.europeana.metis.schema.jibx.DateOfDeath;
import eu.europeana.metis.schema.jibx.DateOfEstablishment;
import eu.europeana.metis.schema.jibx.DateOfTermination;
import eu.europeana.metis.schema.jibx.End;
import eu.europeana.metis.schema.jibx.ExactMatch;
import eu.europeana.metis.schema.jibx.Gender;
import eu.europeana.metis.schema.jibx.HasMet;
import eu.europeana.metis.schema.jibx.HasPart;
import eu.europeana.metis.schema.jibx.HiddenLabel;
import eu.europeana.metis.schema.jibx.Identifier;
import eu.europeana.metis.schema.jibx.InScheme;
import eu.europeana.metis.schema.jibx.IsNextInSequence;
import eu.europeana.metis.schema.jibx.IsPartOf;
import eu.europeana.metis.schema.jibx.IsRelatedTo;
import eu.europeana.metis.schema.jibx.Lat;
import eu.europeana.metis.schema.jibx.NarrowMatch;
import eu.europeana.metis.schema.jibx.Narrower;
import eu.europeana.metis.schema.jibx.Notation;
import eu.europeana.metis.schema.jibx.Note;
import eu.europeana.metis.schema.jibx.PlaceOfBirth;
import eu.europeana.metis.schema.jibx.PlaceOfDeath;
import eu.europeana.metis.schema.jibx.PlaceType;
import eu.europeana.metis.schema.jibx.PrefLabel;
import eu.europeana.metis.schema.jibx.ProfessionOrOccupation;
import eu.europeana.metis.schema.jibx.RDF;
import eu.europeana.metis.schema.jibx.Related;
import eu.europeana.metis.schema.jibx.RelatedMatch;
import eu.europeana.metis.schema.jibx.SameAs;
import eu.europeana.metis.schema.jibx.TimeSpanType;
import eu.europeana.metis.schema.jibx._Long;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;



/**
 * Class that contains logic for converting class entity types and/or merging entities to {@link RDF}
 */
public class EntityMergeEngine {

  private static PlaceType convertPlace(Place place) {

    PlaceType placeType = new PlaceType();

    // about
    ItemExtractorUtils.setAbout(place, placeType);

    // alt
    if (place.getAlt() != null) {
      Alt alt = new Alt();
      alt.setAlt(Float.valueOf(place.getAlt()));
      placeType.setAlt(alt);
    }

    // altlabels
    placeType
        .setAltLabelList(ItemExtractorUtils.extractLabels(place.getAltLabelList(), AltLabel::new));

    // hasPartList
    placeType.setHasPartList(
        ItemExtractorUtils.extractLabelResources(place.getHasPartsList(), HasPart::new));

    // isPartOf
    if (place.getIsPartOf() != null) {
      placeType.setIsPartOfList(
          ItemExtractorUtils.extractLabelResources(place.getIsPartOf(), IsPartOf::new));
    }

    // lat
    if (place.getLat() != null) {
      Lat lat = new Lat();
      lat.setLat(Float.valueOf(place.getLat()));
      placeType.setLat(lat);
    }

    // _long
    if (place.getLon() != null) {
      _Long longitude = new _Long();
      longitude.setLong(Float.valueOf(place.getLon()));
      placeType.setLong(longitude);
    }

    // noteList
    placeType.setNoteList(ItemExtractorUtils.extractLabels(place.getNotes(), Note::new));

    // prefLabelList
    placeType.setPrefLabelList(
        ItemExtractorUtils.extractLabels(place.getPrefLabelList(), PrefLabel::new));

    // sameAsList
    placeType.setSameAList(ItemExtractorUtils.extractResources(place.getSameAs(), SameAs::new));

    // isNextInSequence: not available

    // Done
    return placeType;
  }

  private static AgentType convertAgent(Agent agent) {

    AgentType agentType = new AgentType();

    // about
    ItemExtractorUtils.setAbout(agent, agentType);

    // altLabelList
    agentType
        .setAltLabelList(ItemExtractorUtils.extractLabels(agent.getAltLabelList(), AltLabel::new));

    // begin
    agentType.setBegin(ItemExtractorUtils.extractFirstLabel(agent.getBeginList(), Begin::new));

    // biographicalInformation
    agentType.setBiographicalInformationList(ItemExtractorUtils
        .extractLabelResources(agent.getBiographicalInformation(),
            BiographicalInformation::new));
    agentType.setProfessionOrOccupationList(ItemExtractorUtils
        .extractLabelResources(agent.getProfessionOrOccupation(), ProfessionOrOccupation::new));

    // dateList
    agentType.setDateList(ItemExtractorUtils.extractLabelResources(agent.getDate(), Date::new));

    // placeOfBirth
    agentType.setPlaceOfBirthList(
        ItemExtractorUtils.extractLabelResources(agent.getPlaceOfBirth(), PlaceOfBirth::new));

    // placeOfDeath
    agentType.setPlaceOfDeathList(
        ItemExtractorUtils.extractLabelResources(agent.getPlaceOfDeath(), PlaceOfDeath::new));

    // dateOfBirth
    agentType.setDateOfBirth(
        ItemExtractorUtils.extractFirstLabel(agent.getDateOfBirth(), DateOfBirth::new));

    // dateofDeath
    agentType.setDateOfDeath(
        ItemExtractorUtils.extractFirstLabel(agent.getDateOfDeath(), DateOfDeath::new));

    // dateOfEstablishment
    agentType.setDateOfEstablishment(ItemExtractorUtils
        .extractFirstLabel(agent.getDateOfEstablishment(), DateOfEstablishment::new));

    // dateofTermination
    agentType.setDateOfTermination(
        ItemExtractorUtils.extractFirstLabel(agent.getDateOfTermination(), DateOfTermination::new));

    // end
    agentType.setEnd(ItemExtractorUtils.extractFirstLabel(agent.getEndList(), End::new));

    // gender
    agentType.setGender(ItemExtractorUtils.extractFirstLabel(agent.getGender(), Gender::new));

    // hasMetList
    agentType.setHasMetList(ItemExtractorUtils.extractResources(agent.getHasMet(), HasMet::new));

    // hasPartList: not available

    // identifierList
    agentType.setIdentifierList(
        ItemExtractorUtils.extractLabels(agent.getIdentifier(), Identifier::new));

    // isPartOfList: not available

    // isRelatedToList
    agentType.setIsRelatedToList(
        ItemExtractorUtils.extractLabelResources(agent.getIsRelatedTo(), IsRelatedTo::new));

    // nameList: not available

    // noteList
    agentType.setNoteList(ItemExtractorUtils.extractLabels(agent.getNotes(), Note::new));

    // prefLabelList
    agentType.setPrefLabelList(
        ItemExtractorUtils.extractLabels(agent.getPrefLabelList(), PrefLabel::new));

    // professionOrOccupationList
    agentType.setProfessionOrOccupationList(ItemExtractorUtils
        .extractLabelResources(agent.getProfessionOrOccupation(), ProfessionOrOccupation::new));

    // sameAsList
    agentType.setSameAList(ItemExtractorUtils.extractResources(agent.getSameAs(), SameAs::new));

    return agentType;
  }

  private static eu.europeana.metis.schema.jibx.Concept convertConcept(Concept baseConcept) {

    eu.europeana.metis.schema.jibx.Concept concept = new eu.europeana.metis.schema.jibx.Concept();

    // about
    ItemExtractorUtils.setAbout(baseConcept, concept);

    // choiceList
    final List<Choice> choices = new ArrayList<>();

    final List<AltLabel> altLabels = ItemExtractorUtils
        .extractLabels(baseConcept.getAltLabelList(), AltLabel::new);
    ItemExtractorUtils.toChoices(altLabels, Choice::setAltLabel, choices);

    final List<BroadMatch> broadMatches = ItemExtractorUtils
        .extractResources(baseConcept.getBroadMatch(), BroadMatch::new);
    ItemExtractorUtils.toChoices(broadMatches, Choice::setBroadMatch, choices);

    final List<Broader> broaders = ItemExtractorUtils
        .extractResources(baseConcept.getBroader(), Broader::new);
    ItemExtractorUtils.toChoices(broaders, Choice::setBroader, choices);

    final List<CloseMatch> closeMatches = ItemExtractorUtils
        .extractResources(baseConcept.getCloseMatch(), CloseMatch::new);
    ItemExtractorUtils.toChoices(closeMatches, Choice::setCloseMatch, choices);

    final List<ExactMatch> exactMatches = ItemExtractorUtils
        .extractResources(baseConcept.getExactMatch(), ExactMatch::new);
    ItemExtractorUtils.toChoices(exactMatches, Choice::setExactMatch, choices);

    final List<InScheme> inSchemes = ItemExtractorUtils
        .extractResources(baseConcept.getInScheme(), InScheme::new);
    ItemExtractorUtils.toChoices(inSchemes, Choice::setInScheme, choices);

    final List<Narrower> narrowers = ItemExtractorUtils
        .extractResources(baseConcept.getNarrower(), Narrower::new);
    ItemExtractorUtils.toChoices(narrowers, Choice::setNarrower, choices);

    final List<NarrowMatch> narrowMatches = ItemExtractorUtils
        .extractResources(baseConcept.getNarrowMatch(), NarrowMatch::new);
    ItemExtractorUtils.toChoices(narrowMatches, Choice::setNarrowMatch, choices);

    final List<Notation> notations = ItemExtractorUtils
        .extractLabels(baseConcept.getNotation(), Notation::new);
    ItemExtractorUtils.toChoices(notations, Choice::setNotation, choices);

    final List<Note> notes = ItemExtractorUtils.extractLabels(baseConcept.getNotes(), Note::new);
    ItemExtractorUtils.toChoices(notes, Choice::setNote, choices);

    final List<PrefLabel> prefLabels = ItemExtractorUtils
        .extractLabels(baseConcept.getPrefLabelList(), PrefLabel::new);
    ItemExtractorUtils.toChoices(prefLabels, Choice::setPrefLabel, choices);

    final List<Related> relateds = ItemExtractorUtils
        .extractResources(baseConcept.getRelated(), Related::new);
    ItemExtractorUtils.toChoices(relateds, Choice::setRelated, choices);

    final List<RelatedMatch> relatedMatches = ItemExtractorUtils
        .extractResources(baseConcept.getRelatedMatch(), RelatedMatch::new);
    ItemExtractorUtils.toChoices(relatedMatches, Choice::setRelatedMatch, choices);

    concept.setChoiceList(choices);

    return concept;
  }

  private static TimeSpanType convertTimeSpan(TimeSpan timespan) {

    TimeSpanType timeSpanType = new TimeSpanType();

    // about
    ItemExtractorUtils.setAbout(timespan, timeSpanType);

    // altLabelList
    timeSpanType.setAltLabelList(
        ItemExtractorUtils.extractLabels(timespan.getAltLabelList(), AltLabel::new));

    // begin
    timeSpanType.setBegin(ItemExtractorUtils.extractLabel(timespan.getBegin(), Begin::new));

    // end
    timeSpanType.setEnd(ItemExtractorUtils.extractLabel(timespan.getEnd(), End::new));

    // hasPartList
    timeSpanType.setHasPartList(
        ItemExtractorUtils.extractLabelResources(timespan.getHasPartsList(), HasPart::new));

    // isNextInSequence
    if (timespan.getIsNextInSequence() != null) {
      timeSpanType.setIsNextInSequence(ItemExtractorUtils
          .extractAsResource(timespan.getIsNextInSequence(), IsNextInSequence::new,
              Part::getResource));
    }

    // isPartOf
    if (timespan.getIsPartOf() != null) {
      timeSpanType.setIsPartOfList(
          ItemExtractorUtils.extractLabelResources(timespan.getIsPartOf(), IsPartOf::new));
    }

    // noteList
    timeSpanType.setNoteList(ItemExtractorUtils.extractLabels(timespan.getNotes(), Note::new));

    // prefLabelList
    timeSpanType.setPrefLabelList(
        ItemExtractorUtils.extractLabels(timespan.getPrefLabelList(), PrefLabel::new));

    // sameAsList
    timeSpanType
        .setSameAList(ItemExtractorUtils.extractResources(timespan.getSameAs(), SameAs::new));

    // hiddenLabelList
    timeSpanType.setHiddenLabelList(
        ItemExtractorUtils.extractLabels(timespan.getHiddenLabel(), HiddenLabel::new));

    // done
    return timeSpanType;
  }

  private static eu.europeana.metis.schema.jibx.Organization convertOrganization(
      Organization organization) {
    final eu.europeana.metis.schema.jibx.Organization organizationType = new eu.europeana.metis.schema.jibx.Organization();
    organizationType.setAbout(organization.getAbout());
    organizationType.setPrefLabelList(
        ItemExtractorUtils.extractLabels(organization.getPrefLabelList(), PrefLabel::new));
    return organizationType;
  }

  private static <I extends EnrichmentBase, T extends AboutType> T convertAndAddEntity(
      I inputEntity, Function<I, T> converter, Supplier<List<T>> listGetter,
      Consumer<List<T>> listSetter) {

    // Check if Entity already exists in the list. If so, return it. We don't overwrite.
    final T existingEntity = Optional.ofNullable(listGetter.get()).stream()
                                     .flatMap(Collection::stream)
                                     .filter(candidate -> inputEntity.getAbout().equals(candidate.getAbout())).findAny()
                                     .orElse(null);
    if (existingEntity != null) {
      return existingEntity;
    }

    // Convert and add the new entity.
    final T convertedEntity = converter.apply(inputEntity);
    if (listGetter.get() == null) {
      listSetter.accept(new ArrayList<>());
    }
    listGetter.get().add(convertedEntity);
    return convertedEntity;
  }

  public static AboutType convertAndAddEntity(RDF rdf, EnrichmentBase enrichmentBase) {

    // Convert the entity and add it to the RDF.
    final AboutType entity;
    if (enrichmentBase instanceof Place) {
      entity = convertAndAddEntity((Place) enrichmentBase, EntityMergeEngine::convertPlace,
          rdf::getPlaceList, rdf::setPlaceList);
    } else if (enrichmentBase instanceof Agent) {
      entity = convertAndAddEntity((Agent) enrichmentBase, EntityMergeEngine::convertAgent,
          rdf::getAgentList, rdf::setAgentList);
    } else if (enrichmentBase instanceof Concept) {
      entity = convertAndAddEntity((Concept) enrichmentBase, EntityMergeEngine::convertConcept,
          rdf::getConceptList, rdf::setConceptList);
    } else if (enrichmentBase instanceof TimeSpan) {
      entity = convertAndAddEntity((TimeSpan) enrichmentBase, EntityMergeEngine::convertTimeSpan,
          rdf::getTimeSpanList, rdf::setTimeSpanList);
    } else if (enrichmentBase instanceof Organization) {
      entity = convertAndAddEntity((Organization) enrichmentBase,
          EntityMergeEngine::convertOrganization, rdf::getOrganizationList,
          rdf::setOrganizationList);
    } else {
      throw new IllegalArgumentException("Unknown entity type: " + enrichmentBase.getClass());
    }

    return entity;
  }

  /**
   * Merge entities in a record.
   *
   * @param rdf The RDF to enrich
   * @param enrichmentBaseList The information to append
   * @param searchTermContext the search term context
   */
  public void mergeSearchEntities(RDF rdf, List<EnrichmentBase> enrichmentBaseList,
      SearchTermContext searchTermContext) {
    for (EnrichmentBase base : enrichmentBaseList) {
      final AboutType aboutType = convertAndAddEntity(rdf, base);
      if (isProxyFieldType(searchTermContext.getFieldTypes())) {
        appendLinkToEuropeanaProxy(rdf, aboutType.getAbout(),
            searchTermContext.getFieldTypes().stream().map(ProxyFieldType.class::cast)
                             .collect(Collectors.toSet()));
      } else {
        //Replace matching values in Aggregation
        replaceValueWithLinkInAggregation(rdf, aboutType.getAbout(), searchTermContext);
      }
    }
  }

  /**
   * Merge entities in a record.
   * <p>This method is when enrichment is performed where we <b>do</b> want to add the generated
   * links to the europeana proxy</p>
   *
   * @param rdf The RDF to enrich
   * @param enrichmentBaseList The information to append
   * @param referenceTermContext the reference term context
   */
  public void mergeReferenceEntities(RDF rdf, List<EnrichmentBase> enrichmentBaseList,
      ReferenceTermContext referenceTermContext) {
    for (EnrichmentBase base : enrichmentBaseList) {
      final AboutType aboutType = convertAndAddEntity(rdf, base);
      if (referenceTermContext != null) {
        appendLinkToEuropeanaProxy(rdf, aboutType.getAbout(),
            referenceTermContext.getProxyFieldTypes().stream().map(ProxyFieldType.class::cast)
                                .collect(Collectors.toSet()));
      }
    }
  }

  /**
   * Merge entities in a record.
   * <p>This method is when enrichment is performed where we <b>do</b> want to add the generated
   * links to the europeana proxy</p>
   *
   * @param rdf The RDF to enrich
   * @param dereferencedEntitiesList The information to append
   */
  public void mergeReferenceEntitiesFromDereferencedEntities(RDF rdf, List<DereferencedEntities> dereferencedEntitiesList) {
    for (DereferencedEntities dereferencedEntities : dereferencedEntitiesList) {
      for (Map.Entry<ReferenceTerm, List<EnrichmentBase>> entry : dereferencedEntities.getReferenceTermListMap().entrySet()) {
        List<AboutType> aboutTypeList = entry.getValue()
                                             .stream()
                                             .map(base -> convertAndAddEntity(rdf, base))
                                             .collect(Collectors.toList());
        if (dereferencedEntities.getClassType().equals(Aggregation.class)) {
          replaceResourceWithLinkInAggregation(rdf, aboutTypeList, entry.getKey());
        }
      }
    }
  }

  private static <T extends FieldType<? extends AboutType>> boolean isProxyFieldType(Set<T> set) {
    //This shouldn't happen normally
    if (set == null || set.isEmpty()) {
      throw new IllegalArgumentException("Set cannot be empty");
    }
    final boolean allProxyFieldTypes = set.stream().allMatch(ProxyFieldType.class::isInstance);
    final boolean allAggregationFieldTypes = set.stream()
                                                .allMatch(AggregationFieldType.class::isInstance);

    if (!allProxyFieldTypes && !allAggregationFieldTypes) {
      throw new IllegalArgumentException("Invalid set");
    }

    return allProxyFieldTypes;
  }
}
