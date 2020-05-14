package eu.europeana.metis.dereference.vocimport.model;

import eu.europeana.metis.dereference.vocimport.model.VocabularyMetadata.Type;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * This is an immutable class that represents one vocabulary.
 */
public class Vocabulary {

  private final String name;
  private final Type type;
  private final List<String> paths;
  private final int parentIterations;
  private final String suffix;
  private final List<String> examples;
  private final List<String> counterExamples;
  private final String transformation;
  private final String readableMetadataLocation;
  private final String readableMappingLocation;

  private Vocabulary(Builder builder) {
    this.name = builder.name;
    this.type = builder.type;
    this.paths = builder.paths;
    this.parentIterations = builder.parentIterations;
    this.suffix = builder.suffix;
    this.examples = builder.examples;
    this.counterExamples = builder.counterExamples;
    this.transformation = builder.transformation;
    this.readableMetadataLocation = builder.readableMetadataLocation;
    this.readableMappingLocation = builder.readableMappingLocation;
  }

  public String getName() {
    return name;
  }

  public Type getType() {
    return type;
  }

  public List<String> getPaths() {
    return Collections.unmodifiableList(paths);
  }

  public int getParentIterations() {
    return parentIterations;
  }

  public String getSuffix() {
    return suffix;
  }

  public List<String> getExamples() {
    return Collections.unmodifiableList(examples);
  }

  public List<String> getCounterExamples() {
    return Collections.unmodifiableList(counterExamples);
  }

  public String getTransformation() {
    return transformation;
  }

  public String getReadableMetadataLocation() {
    return readableMetadataLocation;
  }

  public String getReadableMappingLocation() {
    return readableMappingLocation;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * This class is a builder for the vocabulary.
   */
  public static class Builder {

    private String name;
    private Type type;
    private List<String> paths;
    private int parentIterations;
    private String suffix;
    private List<String> examples;
    private List<String> counterExamples;
    private String transformation;
    private String readableMetadataLocation;
    private String readableMappingLocation;

    private Builder() {
    }

    public Builder setName(String name) {
      this.name = normalizeString(name);
      return this;
    }

    public Builder setType(Type type) {
      this.type = type;
      return this;
    }

    public Builder setPaths(List<String> paths) {
      this.paths = normalizeStringList(paths);
      return this;
    }

    public Builder setParentIterations(Integer parentIterations) {
      this.parentIterations = Optional.ofNullable(parentIterations).orElse(0);
      return this;
    }

    public Builder setSuffix(String suffix) {
      this.suffix = normalizeString(suffix);
      return this;
    }

    public Builder setExamples(List<String> examples) {
      this.examples = normalizeStringList(examples);
      return this;
    }

    public Builder setCounterExamples(List<String> counterExamples) {
      this.counterExamples = normalizeStringList(counterExamples);
      return this;
    }

    public Builder setTransformation(String transformation) {
      this.transformation = normalizeString(transformation);
      return this;
    }

    public Builder setReadableMetadataLocation(String readableMetadataLocation) {
      this.readableMetadataLocation = readableMetadataLocation;
      return this;
    }

    public Builder setReadableMappingLocation(String readableMappingLocation) {
      this.readableMappingLocation = readableMappingLocation;
      return this;
    }

    private static String normalizeString(String input) {
      return Optional.ofNullable(input).filter(StringUtils::isNotBlank).map(String::trim)
              .orElse(null);
    }

    private static List<String> normalizeStringList(List<String> input) {
      return input.stream().filter(StringUtils::isNotBlank).map(String::trim)
              .collect(Collectors.toList());
    }

    public Vocabulary build() {
      return new Vocabulary(this);
    }
  }
}