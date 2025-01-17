package eu.europeana.metis.transformation.service;

import eu.europeana.metis.transformation.service.CacheValueSupplier.CacheValueSupplierException;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.TransformerFactoryImpl;
import org.apache.commons.lang3.StringUtils;

/**
 * This class performs XSL transforms (XSLT). Instances of this class are <b>not thread-safe</b>. For each thread a new instance
 * needs to be created, but, due to the caching mechanism of the XSLT compilation, this operation is not very expensive.
 */
public class XsltTransformer {

  private static final CacheWithExpirationTime<String, Templates> TEMPLATES_CACHE =
      new CacheWithExpirationTime<>();

  private static final HttpClient httpClient = HttpClient.newBuilder().build();
  private final Transformer transformer;

  /**
   * Constructor in the case that no value of the datasetId field needs to be set.
   *
   * @param xsltUrl The URL of the XSLT file.
   * @throws TransformationException In case there was a problem setting up the transformation.
   */
  public XsltTransformer(String xsltUrl) throws TransformationException {
    this(xsltUrl, null, null, null);
  }

  /**
   * Constructor in the case that no value of the datasetId field needs to be set.
   *
   * @param xsltUrl The URL of the XSLT file.
   * @param xsltInputStream a inputStream of the XSLT file.
   * @throws TransformationException In case there was a problem setting up the transformation.
   */
  public XsltTransformer(String xsltUrl, InputStream xsltInputStream)
      throws TransformationException {
    this(xsltUrl, xsltInputStream, null, null, null);
  }


  /**
   * Constructor.
   *
   * @param xsltKey The URI of the XSLT file or a plain value usable as a cache key.
   * @param xsltInputStream a inputStream of the XSLT file.
   * @param datasetName the dataset name related to the dataset
   * @param edmCountry the Country related to the dataset
   * @param edmLanguage the language related to the dataset
   * @throws TransformationException In case there was a problem with setting up the transformation.
   */

  public XsltTransformer(String xsltKey, InputStream xsltInputStream, String datasetName,
      String edmCountry, String edmLanguage) throws TransformationException {
    try {
      transformer = getTemplatesFromUrlOrStream(xsltKey, xsltInputStream).newTransformer();
    } catch (TransformerConfigurationException | CacheValueSupplierException e) {
      throw new TransformationException(e);
    }
    setTransformerParameters(datasetName, edmCountry, edmLanguage);
  }

  /**
   * Constructor.
   *
   * @param xsltUrl The URL of the XSLT file.
   * @param datasetName the dataset name related to the dataset
   * @param edmCountry the Country related to the dataset
   * @param edmLanguage the language related to the dataset
   * @throws TransformationException In case there was a problem with setting up the transformation.
   */
  public XsltTransformer(String xsltUrl, String datasetName, String edmCountry, String edmLanguage)
      throws TransformationException {
    try {
      this.transformer = getTemplates(xsltUrl).newTransformer();
    } catch (TransformerConfigurationException | CacheValueSupplierException e) {
      throw new TransformationException(e);
    }
    setTransformerParameters(datasetName, edmCountry, edmLanguage);
  }

  private static Templates getTemplates(String xsltUrl) throws CacheValueSupplierException {
    return TEMPLATES_CACHE.getFromCache(xsltUrl, () -> createTemplatesFromUrl(xsltUrl));
  }

  /**
   *
   * @param xsltKey the xslt key which can be a URI or plain value string
   * @param xsltInputStream the xslt input stream in case xslt key fails to be retrieved as a URI
   * @return the templates
   * @throws CacheValueSupplierException if the input stream reading fails as well
   */
  private static Templates getTemplatesFromUrlOrStream(String xsltKey,
      InputStream xsltInputStream) throws CacheValueSupplierException {
    Templates templates;
    try {
      templates = TEMPLATES_CACHE.getFromCache(xsltKey, () -> createTemplatesFromUrl(xsltKey));
    } catch (CacheValueSupplierException | NullPointerException | IllegalArgumentException e) {
      templates = TEMPLATES_CACHE.getFromCache(xsltKey,
          () -> createTemplatesFromInputStream(xsltInputStream));
    }
    return templates;
  }

  private static Templates createTemplatesFromInputStream(InputStream xsltInputStream)
      throws CacheValueSupplierException {
    try {
      TransformerFactory transformerFactory = new TransformerFactoryImpl();
      return transformerFactory.newTemplates(new StreamSource(xsltInputStream));
    } catch (TransformerConfigurationException e) {
      throw new CacheValueSupplierException(e);
    }
  }

  private static Templates createTemplatesFromUrl(String xsltUrl)
      throws CacheValueSupplierException {

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .GET()
        .uri(URI.create(xsltUrl))
        .build();

    // We know where the xslt files are coming from, we consider them safe.
    try (final InputStream xsltStream = httpClient.send(httpRequest, BodyHandlers.ofInputStream())
        .body()) {
      TransformerFactory transformerFactory = new TransformerFactoryImpl();
      return transformerFactory.newTemplates(new StreamSource(xsltStream));
    } catch (IOException | TransformerConfigurationException e) {
      throw new CacheValueSupplierException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CacheValueSupplierException(e);
    }

  }

  /**
   * Set a new expiration time for the internal XSLT cache by calling {@link CacheWithExpirationTime#setExpirationTime(Duration)}.
   *
   * @param expirationTime The new expiration time.
   */
  public static void setExpirationTime(Duration expirationTime) {
    TEMPLATES_CACHE.setExpirationTime(expirationTime);
  }

  /**
   * Set a new leniency mode for the internal XSLT cache by calling {@link CacheWithExpirationTime#setLenientWithReloads(boolean)}.
   *
   * @param lenientWithReloads The new leniency mode.
   */
  public static void setLenientWithReloads(boolean lenientWithReloads) {
    TEMPLATES_CACHE.setLenientWithReloads(lenientWithReloads);
  }

  /**
   * Clean up the internal XSLT cache by calling {@link CacheWithExpirationTime#removeItemsNotAccessedSince(Duration)}.
   *
   * @param since The interval length of the period we want to check (which ends now). A negative duration cleans everything.
   */
  public static void removeItemsNotAccessedSince(Duration since) {
    TEMPLATES_CACHE.removeItemsNotAccessedSince(since);
  }

  private void setTransformerParameters(String datasetName, String edmCountry, String edmLanguage) {

    if (StringUtils.isNotBlank(datasetName)) {
      transformer.setParameter("datasetName", datasetName);
    }
    if (StringUtils.isNotBlank(edmLanguage)) {
      transformer.setParameter("edmLanguage", edmLanguage);
    }
    if (StringUtils.isNotBlank(edmCountry)) {
      transformer.setParameter("edmCountry", edmCountry);
    }
  }

  /**
   * Transforms a file using this instance's XSL transformation.
   *
   * @param fileContent The file to be transformed.
   * @param europeanaGeneratedIdsMap all the identifiers related to europeana RDF elements
   * @return The transformed file.
   * @throws TransformationException In case there was a problem with the transformation.
   */
  public StringWriter transform(byte[] fileContent,
      EuropeanaGeneratedIdsMap europeanaGeneratedIdsMap) throws TransformationException {
    final StringWriter result = new StringWriter();
    transform(fileContent, result, europeanaGeneratedIdsMap);
    return result;
  }

  /**
   * Transforms a file using this instance's XSL transformation.
   *
   * @param fileContent The file to be transformed.
   * @param europeanaGeneratedIdsMap all the identifiers related to europeana RDF elements
   * @return The transformed file.
   * @throws TransformationException In case there was a problem with the transformation.
   */
  public byte[] transformToBytes(byte[] fileContent,
      EuropeanaGeneratedIdsMap europeanaGeneratedIdsMap) throws TransformationException {
    try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      try (final Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
        transform(fileContent, writer, europeanaGeneratedIdsMap);
      }
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new TransformationException(e);
    }
  }

  public void transform(byte[] fileContent, Writer writer,
      EuropeanaGeneratedIdsMap europeanaGeneratedIdsMap) throws TransformationException {
    if (europeanaGeneratedIdsMap != null) {
      transformer.setParameter("providedCHOAboutId",
          europeanaGeneratedIdsMap.getEuropeanaGeneratedId());
      transformer.setParameter("aggregationAboutId",
          europeanaGeneratedIdsMap.getAggregationAboutPrefixed());
      transformer.setParameter("europeanaAggregationAboutId",
          europeanaGeneratedIdsMap.getEuropeanaAggregationAboutPrefixed());
      transformer.setParameter("proxyAboutId", europeanaGeneratedIdsMap.getProxyAboutPrefixed());
      transformer.setParameter("europeanaProxyAboutId",
          europeanaGeneratedIdsMap.getEuropeanaProxyAboutPrefixed());
      transformer.setParameter("dcIdentifier",
          europeanaGeneratedIdsMap.getSourceProvidedChoAbout());
    }
    try (final InputStream contentStream = new ByteArrayInputStream(fileContent)) {
      transformer.transform(new StreamSource(contentStream), new StreamResult(writer));
    } catch (TransformerException | IOException e) {
      throw new TransformationException(e);
    }
  }
}
