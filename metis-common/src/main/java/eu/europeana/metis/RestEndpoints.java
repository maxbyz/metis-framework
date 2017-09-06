package eu.europeana.metis;

import org.apache.commons.lang.StringUtils;

/**
 * REST Endpoints
 * Created by ymamakis on 7/29/16.
 */
public final class RestEndpoints {

    /* METIS-CORE Endopoints*/

  //ORGANIZATIONS
  public final static String ORGANIZATIONS = "/organizations";
  public final static String ORGANIZATIONS_ORGANIZATION_ID = "/organizations/{organizationId}";
  public final static String ORGANIZATIONS_COUNTRY_ISOCODE = "/organizations/country/{isoCode}";
  public final static String ORGANIZATIONS_ROLES = "/organizations/roles";
  public final static String ORGANIZATIONS_SUGGEST = "/organizations/suggest";
  public final static String ORGANIZATIONS_ORGANIZATION_ID_DATASETS = "/organizations/{organizationId}/datasets";
  public final static String ORGANIZATIONS_ORGANIZATION_ID_OPTINIIIF = "/organizations/{organizationId}/optInIIIF";
  public final static String ORGANIZATIONS_CRM_ORGANIZATION_ID = "/organizations/crm/{organizationId}";
  public final static String ORGANIZATIONS_CRM = "/organizations/crm";

  //DATASETS
  public final static String DATASETS = "/datasets";
  public final static String DATASETS_DATASETNAME = "/datasets/{datasetName}";
  public final static String DATASETS_DATASETNAME_UPDATENAME = "/datasets/{datasetName}/updateName";
  public final static String DATASETS_DATAPROVIDER = "/datasets/data_provider/{dataProvider}";

  //USERS
  public final static String USER = "/user";
  public final static String USERBYMAIL = "/user/{email}";

  //ORCHESTRATION
  public final static String ORCHESTRATOR_USERWORKFLOWS = "/orchestrator/user_workflows";
  public final static String ORCHESTRATOR_USERWORKFLOWS_OWNER = "/orchestrator/user_workflows/{owner}";
  public final static String ORCHESTRATOR_USERWORKFLOWS_DATASETNAME_EXECUTE = "/orchestrator/user_workflows/{datasetName}/execute";
  public final static String ORCHESTRATOR_USERWORKFLOWS_DATASETNAME_EXECUTE_DIRECT = "/orchestrator/user_workflows/{datasetName}/execute/direct";
  public final static String ORCHESTRATOR_USERWORKFLOWS_EXECUTION_DATASETNAME = "/orchestrator/user_workflows/execution/{datasetName}";
  public final static String ORCHESTRATOR_USERWORKFLOWS_EXECUTIONS_DATASETNAME = "/orchestrator/user_workflows/executions/{datasetName}";
  public final static String ORCHESTRATOR_USERWORKFLOWS_EXECUTIONS = "/orchestrator/user_workflows/executions";

  /* METIS-DEREFERENCE Endpoints*/
  public final static String DEREFERENCE = "/dereference";
  public final static String VOCABULARY = "/vocabulary";
  public final static String VOCABULARY_BYNAME = "/vocabulary/{name}";
  public final static String VOCABULARIES = "/vocabularies";
  public final static String ENTITY = "/entity";
  public final static String ENTITY_DELETE = "/entity/{uri}";
  public final static String CACHE_EMPTY = "/cache";

  /* METIS ENRICHMENT Endpoint */
  public final static String ENRICHMENT_DELETE = "/delete";
  public final static String ENRICHMENT_BYURI = "/getByUri";
  public final static String ENRICHMENT_ENRICH = "/enrich";

  /* METIS IDENTIFIER ITEMIZATION Endpoint */
  public final static String IDENTIFIER_GENERATE = "/identifier/generate/{collectionId}";
  public final static String IDENTIFIER_NORMALIZE_SINGLE = "/identifier/normalize/single";
  public final static String IDENTIFIER_NORMALIZE_BATCH = "/identifier/normalize/batch";
  public final static String ITEMIZE_URL = "/itemize/url";
  public final static String ITEMIZE_RECORDS = "/itemize/records";
  public final static String ITEMIZE_FILE = "/itemize/file";

  /*METIS REDIRECTS Endpoint*/
  public final static String REDIRECT_SINGLE = "/redirect/single";
  public final static String REDIRECT_BATCH = "/redirect/batch";

  /* METIS PANDORA Endpoint */
  public final static String MAPPING = "/mapping";
  public final static String MAPPING_BYID = "/mapping/{mappingId}";
  public final static String MAPPING_DATASETNAME = "/mapping/dataset/{name}";
  public final static String MAPPINGS_BYORGANIZATIONID = "/mappings/organization/{orgId}";
  public final static String MAPPINGS_NAMES_BYORGANIZATIONID = "/mappings/names/organization/{orgId}";
  public final static String MAPPING_TEMPLATES = "/mapping/templates";
  public final static String MAPPING_STATISTICS_BYNAME = "/mapping/statistics/{name}";
  public final static String MAPPING_SCHEMATRON = "/mapping/schematron";
  public final static String MAPPING_NAMESPACES = "/mapping/namespaces";
  public final static String MAPPING_STATISTICS_ELEMENT = "/mapping/statistics/{datasetId}/element";
  public final static String MAPPING_STATISTICS_ATTRIBUTE = "/mapping/statistics/{datasetId}/attribute";
  public final static String STATISTICS_CALCULATE = "/statistics/calculate/{datasetId}";
  public final static String STATISTICS_APPEND = "/statistics/append/{datasetId}";
  public final static String VALIDATE_ATTRIBUTE = "/mapping/validation/{mappingId}/attribute";
  public final static String VALIDATE_ELEMENT = "/mapping/validation/{mappingId}/element";
  public final static String VALIDATE_CREATE_ATTTRIBUTE_FLAG = "/mapping/validation/{mappingId}/attribute/create/{value}/{flagType}";
  public final static String VALIDATE_CREATE_ELEMENT_FLAG = "/mapping/validation/{mappingId}/element/create/{value}/{flagType}";
  public final static String VALIDATE_MAPPING = "/mapping/validation/validate";
  public final static String XSD_UPLOAD = "/xsd/upload";
  public final static String XSD_URL = "/xsd/url";
  public final static String XSL_GENERATE = "/xsl/generate";
  public final static String XSL_MAPPINGID = "/xsl/{mappingId}";
  public final static String VALIDATE_DELETE_ATTRIBUTE_FLAG = "/mapping/validation/{mappingId}/attribute/{value}";
  public final static String VALIDATE_DELETE_ELEMENT_FLAG = "/mapping/validation/{mappingId}/element/{value}";
  public final static String NORMALIZATION = "/normalizeEdmInternal";

  /* METIS SCHEMA VALIDATION ENDPOINT */
  public final static String SCHEMA_VALIDATE = "/schema/validate/{schema}/{version}";
  public final static String SCHEMA_BATCH_VALIDATE = "/schema/validate/batch/{schema}/{version}";
  public final static String SCHEMA_RECORDS_BATCH_VALIDATE = "/schema/validate/batch/records/{schema}/{version}";
  public final static String SCHEMAS_DOWNLOAD_BY_NAME = "/schemas/download/schema/{name}/{version}";
  public final static String SCHEMAS_MANAGE_BY_NAME = "/schemas/schema/{name}/{version}";
  public final static String SCHEMAS_UPDATE_BY_NAME = "/schemas/schema/update/{name}/{version}";
  public final static String SCHEMAS_ALL = "/schemas/all";

  /* METIS PREVIEW SERVICE ENDPOINT*/
  public final static String PREVIEW_UPLOAD = "/upload";

  /* METIS LINKCHECK SERVICE ENDPOINT*/
  public final static String LINKCHECK = "/linkcheck";


  public static String resolve(String endpoint, String... params) {
    if (params == null || params.length == 0) {
      return endpoint;
    }
    String[] test = StringUtils.split(endpoint, "{");
    String fin = "";
    int i = 0;
    for (String en : test) {
      if (i == 0) {
        fin = en;
      } else {
        fin += StringUtils.replace(en, StringUtils.substringBefore(en, "}") + "}", params[i - 1]);
      }
      i++;
    }
    return fin;
  }
}

