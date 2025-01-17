package eu.europeana.metis.core.service;

import com.google.common.collect.Sets;
import eu.europeana.metis.authentication.user.AccountRole;
import eu.europeana.metis.authentication.user.MetisUserView;
import eu.europeana.metis.core.common.DaoFieldNames;
import eu.europeana.metis.core.dao.DataEvolutionUtils;
import eu.europeana.metis.core.dao.DatasetDao;
import eu.europeana.metis.core.dao.DepublishRecordIdDao;
import eu.europeana.metis.core.dao.PluginWithExecutionId;
import eu.europeana.metis.core.dao.WorkflowDao;
import eu.europeana.metis.core.dao.WorkflowExecutionDao;
import eu.europeana.metis.core.dao.WorkflowExecutionDao.ExecutionDatasetPair;
import eu.europeana.metis.core.dao.WorkflowExecutionDao.ResultList;
import eu.europeana.metis.core.dao.WorkflowValidationUtils;
import eu.europeana.metis.core.dataset.Dataset;
import eu.europeana.metis.core.dataset.DatasetExecutionInformation;
import eu.europeana.metis.core.dataset.DatasetExecutionInformation.PublicationStatus;
import eu.europeana.metis.core.exceptions.NoDatasetFoundException;
import eu.europeana.metis.core.exceptions.NoWorkflowExecutionFoundException;
import eu.europeana.metis.core.exceptions.NoWorkflowFoundException;
import eu.europeana.metis.core.exceptions.PluginExecutionNotAllowed;
import eu.europeana.metis.core.exceptions.WorkflowAlreadyExistsException;
import eu.europeana.metis.core.exceptions.WorkflowExecutionAlreadyExistsException;
import eu.europeana.metis.core.execution.WorkflowExecutorManager;
import eu.europeana.metis.core.rest.ExecutionHistory;
import eu.europeana.metis.core.rest.ExecutionHistory.Execution;
import eu.europeana.metis.core.rest.PluginsWithDataAvailability;
import eu.europeana.metis.core.rest.PluginsWithDataAvailability.PluginWithDataAvailability;
import eu.europeana.metis.core.rest.ResponseListWrapper;
import eu.europeana.metis.core.rest.VersionEvolution;
import eu.europeana.metis.core.rest.VersionEvolution.VersionEvolutionStep;
import eu.europeana.metis.core.rest.execution.details.WorkflowExecutionView;
import eu.europeana.metis.core.rest.execution.overview.ExecutionAndDatasetView;
import eu.europeana.metis.core.workflow.SystemId;
import eu.europeana.metis.core.workflow.Workflow;
import eu.europeana.metis.core.workflow.WorkflowExecution;
import eu.europeana.metis.core.workflow.WorkflowStatus;
import eu.europeana.metis.core.workflow.plugins.AbstractExecutablePlugin;
import eu.europeana.metis.core.workflow.plugins.AbstractHarvestPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.AbstractMetisPlugin;
import eu.europeana.metis.core.workflow.plugins.DataStatus;
import eu.europeana.metis.core.workflow.plugins.DepublishPlugin;
import eu.europeana.metis.core.workflow.plugins.DepublishPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ExecutablePlugin;
import eu.europeana.metis.core.workflow.plugins.ExecutablePluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ExecutablePluginType;
import eu.europeana.metis.core.workflow.plugins.ExecutionProgress;
import eu.europeana.metis.core.workflow.plugins.MetisPlugin;
import eu.europeana.metis.core.workflow.plugins.PluginStatus;
import eu.europeana.metis.core.workflow.plugins.PluginType;
import eu.europeana.metis.exception.BadContentException;
import eu.europeana.metis.exception.ExternalTaskException;
import eu.europeana.metis.exception.GenericMetisException;
import eu.europeana.metis.exception.UserUnauthorizedException;
import eu.europeana.metis.utils.DateUtils;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Service class that controls the communication between the different DAOs of the system.
 *
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-05-24
 */
@Service
public class OrchestratorService {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrchestratorService.class);
  //Use with String.format to suffix the datasetId
  private static final String EXECUTION_FOR_DATASETID_SUBMITION_LOCK = "EXECUTION_FOR_DATASETID_SUBMITION_LOCK_%s";

  public static final Set<ExecutablePluginType> HARVEST_TYPES = Sets
      .immutableEnumSet(ExecutablePluginType.HTTP_HARVEST, ExecutablePluginType.OAIPMH_HARVEST);
  public static final Set<ExecutablePluginType> EXECUTABLE_PREVIEW_TYPES = Sets
      .immutableEnumSet(ExecutablePluginType.PREVIEW);
  public static final Set<ExecutablePluginType> EXECUTABLE_PUBLISH_TYPES = Sets
      .immutableEnumSet(ExecutablePluginType.PUBLISH);
  public static final Set<ExecutablePluginType> EXECUTABLE_DEPUBLISH_TYPES = Sets
      .immutableEnumSet(ExecutablePluginType.DEPUBLISH);
  public static final Set<PluginType> PREVIEW_TYPES = Sets
      .immutableEnumSet(PluginType.PREVIEW, PluginType.REINDEX_TO_PREVIEW);
  public static final Set<PluginType> PUBLISH_TYPES = Sets
      .immutableEnumSet(PluginType.PUBLISH, PluginType.REINDEX_TO_PUBLISH);
  public static final Set<ExecutablePluginType> NO_XML_PREVIEW_TYPES = Sets
      .immutableEnumSet(ExecutablePluginType.LINK_CHECKING, ExecutablePluginType.DEPUBLISH);

  private final WorkflowExecutionDao workflowExecutionDao;
  private final WorkflowValidationUtils workflowValidationUtils;
  private final DataEvolutionUtils dataEvolutionUtils;
  private final WorkflowDao workflowDao;
  private final DatasetDao datasetDao;
  private final WorkflowExecutorManager workflowExecutorManager;
  private final RedissonClient redissonClient;
  private final Authorizer authorizer;
  private final WorkflowExecutionFactory workflowExecutionFactory;
  private final DepublishRecordIdDao depublishRecordIdDao;
  private int solrCommitPeriodInMins; // Use getter and setter for this field!

  /**
   * Constructor with all the required parameters
   *
   * @param workflowExecutionFactory the orchestratorHelper instance
   * @param workflowDao the Dao instance to access the Workflow database
   * @param workflowExecutionDao the Dao instance to access the WorkflowExecution database
   * @param workflowValidationUtils A utilities class providing more functionality on top of DAOs.
   * @param dataEvolutionUtils A utilities class providing more functionality on top of DAOs.
   * @param datasetDao the Dao instance to access the Dataset database
   * @param workflowExecutorManager the instance that handles the production and consumption of workflowExecutions
   * @param redissonClient the instance of Redisson library that handles distributed locks
   * @param authorizer the authorizer
   * @param depublishRecordIdDao the Dao instance to access the DepublishRecordId database
   */
  @Autowired
  public OrchestratorService(WorkflowExecutionFactory workflowExecutionFactory,
      WorkflowDao workflowDao, WorkflowExecutionDao workflowExecutionDao,
      WorkflowValidationUtils workflowValidationUtils, DataEvolutionUtils dataEvolutionUtils,
      DatasetDao datasetDao, WorkflowExecutorManager workflowExecutorManager,
      RedissonClient redissonClient, Authorizer authorizer,
      DepublishRecordIdDao depublishRecordIdDao) {
    this.workflowExecutionFactory = workflowExecutionFactory;
    this.workflowDao = workflowDao;
    this.workflowExecutionDao = workflowExecutionDao;
    this.workflowValidationUtils = workflowValidationUtils;
    this.dataEvolutionUtils = dataEvolutionUtils;
    this.datasetDao = datasetDao;
    this.workflowExecutorManager = workflowExecutorManager;
    this.redissonClient = redissonClient;
    this.authorizer = authorizer;
    this.depublishRecordIdDao = depublishRecordIdDao;
  }

  /**
   * Create a workflow using a datasetId and the {@link Workflow} that contains the requested plugins. If plugins are disabled,
   * they (their settings) are still saved.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param datasetId the identifier of the dataset for which the workflow should be created
   * @param workflow the workflow with the plugins requested
   * @param enforcedPredecessorType optional, the plugin type to be used as source data
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link WorkflowAlreadyExistsException} if a workflow for the dataset identifier provided
   * already exists</li>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * <li>{@link BadContentException} if the workflow parameters have unexpected values</li>
   * </ul>
   */
  public void createWorkflow(MetisUserView metisUserView, String datasetId, Workflow workflow,
      ExecutablePluginType enforcedPredecessorType) throws GenericMetisException {

    // Authorize (check dataset existence) and set dataset ID to avoid discrepancy.
    authorizer.authorizeWriteExistingDatasetById(metisUserView, datasetId);
    workflow.setDatasetId(datasetId);

    // Check that the workflow does not yet exist.
    if (workflowDao.workflowExistsForDataset(workflow.getDatasetId())) {
      throw new WorkflowAlreadyExistsException(
          String.format("Workflow with datasetId: %s, already exists", workflow.getDatasetId()));
    }

    // Validate the new workflow.
    workflowValidationUtils.validateWorkflowPlugins(workflow, enforcedPredecessorType);

    // Save the workflow.
    workflowDao.create(workflow);
  }

  /**
   * Update an already existent workflow using a datasetId and the {@link Workflow} that contains the requested plugins. If
   * plugins are disabled, they (their settings) are still saved. Any settings in plugins that are not sent in the request are
   * removed.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param datasetId the identifier of the dataset for which the workflow should be updated
   * @param workflow the workflow with the plugins requested
   * @param enforcedPredecessorType optional, the plugin type to be used as source data
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link NoWorkflowFoundException} if a workflow for the dataset identifier provided does
   * not exist</li>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * <li>{@link BadContentException} if the workflow parameters have unexpected values</li>
   * </ul>
   */
  public void updateWorkflow(MetisUserView metisUserView, String datasetId, Workflow workflow,
      ExecutablePluginType enforcedPredecessorType) throws GenericMetisException {

    // Authorize (check dataset existence) and set dataset ID to avoid discrepancy.
    authorizer.authorizeWriteExistingDatasetById(metisUserView, datasetId);
    workflow.setDatasetId(datasetId);

    // Get the current workflow in the database. If it doesn't exist, throw exception.
    final Workflow storedWorkflow = workflowDao.getWorkflow(workflow.getDatasetId());
    if (storedWorkflow == null) {
      throw new NoWorkflowFoundException(
          String.format("Workflow with datasetId: %s, not found", workflow.getDatasetId()));
    }

    // Validate the new workflow.
    workflowValidationUtils.validateWorkflowPlugins(workflow, enforcedPredecessorType);

    // Overwrite the workflow.
    workflow.setId(storedWorkflow.getId());
    workflowDao.update(workflow);
  }

  /**
   * Deletes a workflow.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param datasetId the dataset identifier that corresponds to the workflow to be deleted
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * </ul>
   */
  public void deleteWorkflow(MetisUserView metisUserView, String datasetId) throws GenericMetisException {
    authorizer.authorizeWriteExistingDatasetById(metisUserView, datasetId);
    workflowDao.deleteWorkflow(datasetId);
  }

  /**
   * Get a workflow for a dataset identifier.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param datasetId the dataset identifier
   * @return the Workflow object
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * </ul>
   */
  public Workflow getWorkflow(MetisUserView metisUserView, String datasetId) throws GenericMetisException {
    authorizer.authorizeReadExistingDatasetById(metisUserView, datasetId);
    return workflowDao.getWorkflow(datasetId);
  }

  /**
   * Get a WorkflowExecution using an execution identifier.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param executionId the execution identifier
   * @return the WorkflowExecution object
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * </ul>
   */
  public WorkflowExecution getWorkflowExecutionByExecutionId(MetisUserView metisUserView,
      String executionId) throws GenericMetisException {
    final WorkflowExecution result = workflowExecutionDao.getById(executionId);
    if (result != null) {
      authorizer.authorizeReadExistingDatasetById(metisUserView, result.getDatasetId());
    }
    return result;
  }

  /**
   * <p> Does checking, prepares and adds a WorkflowExecution in the queue. That means it updates
   * the status of the WorkflowExecution to {@link WorkflowStatus#INQUEUE}, adds it to the database and also it's identifier goes
   * into the distributed queue of WorkflowExecutions. The source data for the first plugin in the workflow can be controlled, if
   * required, from the {@code enforcedPredecessorType}, which means that the last valid plugin that is provided with that
   * parameter, will be used as the source data. </p>
   * <p> <b>Please note:</b> this method is not checked for authorization: it is only meant to be
   * called from a scheduled task. </p>
   *
   * @param datasetId the dataset identifier for which the execution will take place
   * @param workflowProvided optional, the workflow to use instead of retrieving the saved one from the db
   * @param enforcedPredecessorType optional, the plugin type to be used as source data
   * @param priority the priority of the execution in case the system gets overloaded, 0 lowest, 10 highest
   * @return the WorkflowExecution object that was generated
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link NoWorkflowFoundException} if a workflow for the dataset identifier provided does
   * not exist</li>
   * <li>{@link BadContentException} if the workflow is empty or no plugin enabled</li>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link ExternalTaskException} if there was an exception when contacting the external
   * resource(ECloud)</li>
   * <li>{@link PluginExecutionNotAllowed} if the execution of the first plugin was not allowed,
   * because a valid source plugin could not be found</li>
   * <li>{@link WorkflowExecutionAlreadyExistsException} if a workflow execution for the generated
   * execution identifier already exists, almost impossible to happen since ids are UUIDs</li>
   * </ul>
   */
  public WorkflowExecution addWorkflowInQueueOfWorkflowExecutionsWithoutAuthorization(
      String datasetId, @Nullable Workflow workflowProvided,
      @Nullable ExecutablePluginType enforcedPredecessorType, int priority)
      throws GenericMetisException {
    final Dataset dataset = datasetDao.getDatasetByDatasetId(datasetId);
    if (dataset == null) {
      throw new NoDatasetFoundException(
          String.format("No dataset found with datasetId: %s, in METIS", datasetId));
    }
    return addWorkflowInQueueOfWorkflowExecutions(dataset, workflowProvided,
        enforcedPredecessorType, priority, null);
  }

  /**
   * Does checking, prepares and adds a WorkflowExecution in the queue. That means it updates the status of the WorkflowExecution
   * to {@link WorkflowStatus#INQUEUE}, adds it to the database and also it's identifier goes into the distributed queue of
   * WorkflowExecutions. The source data for the first plugin in the workflow can be controlled, if required, from the {@code
   * enforcedPredecessorType}, which means that the last valid plugin that is provided with that parameter, will be used as the
   * source data.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param datasetId the dataset identifier for which the execution will take place
   * @param workflowProvided optional, the workflow to use instead of retrieving the saved one from the db
   * @param enforcedPredecessorType optional, the plugin type to be used as source data
   * @param priority the priority of the execution in case the system gets overloaded, 0 lowest, 10 highest
   * @return the WorkflowExecution object that was generated
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link NoWorkflowFoundException} if a workflow for the dataset identifier provided does
   * not exist</li>
   * <li>{@link BadContentException} if the workflow is empty or no plugin enabled</li>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * <li>{@link ExternalTaskException} if there was an exception when contacting the external
   * resource(ECloud)</li>
   * <li>{@link PluginExecutionNotAllowed} if the execution of the first plugin was not allowed,
   * because a valid source plugin could not be found</li>
   * <li>{@link WorkflowExecutionAlreadyExistsException} if a workflow execution for the generated
   * execution identifier already exists, almost impossible to happen since ids are UUIDs</li>
   * </ul>
   */
  public WorkflowExecution addWorkflowInQueueOfWorkflowExecutions(MetisUserView metisUserView,
      String datasetId, @Nullable Workflow workflowProvided,
      @Nullable ExecutablePluginType enforcedPredecessorType,
      int priority)
      throws GenericMetisException {
    final Dataset dataset = authorizer.authorizeWriteExistingDatasetById(metisUserView, datasetId);
    return addWorkflowInQueueOfWorkflowExecutions(dataset, workflowProvided,
        enforcedPredecessorType, priority, metisUserView);
  }

  private WorkflowExecution addWorkflowInQueueOfWorkflowExecutions(Dataset dataset,
      @Nullable Workflow workflowProvided,
      @Nullable ExecutablePluginType enforcedPredecessorType,
      int priority, MetisUserView metisUserView)
      throws GenericMetisException {

    // Get the workflow or use the one provided.
    final Workflow workflow;
    if (Objects.isNull(workflowProvided)) {
      workflow = workflowDao.getWorkflow(dataset.getDatasetId());
    } else {
      workflow = workflowProvided;
    }
    if (workflow == null) {
      throw new NoWorkflowFoundException(
          String.format("No workflow found with datasetId: %s, in METIS", dataset.getDatasetId()));
    }

    // Validate the workflow and obtain the predecessor.
    final PluginWithExecutionId<ExecutablePlugin> predecessor = workflowValidationUtils
        .validateWorkflowPlugins(workflow, enforcedPredecessorType);

    // Make sure that eCloud knows tmetisUserhis dataset (needs to happen before we create the workflow).
    datasetDao.checkAndCreateDatasetInEcloud(dataset);

    // Create the workflow execution (without adding it to the database).
    final WorkflowExecution workflowExecution = workflowExecutionFactory
        .createWorkflowExecution(workflow, dataset, predecessor, priority);

    // Obtain the lock.
    RLock executionDatasetIdLock = redissonClient
        .getFairLock(String.format(EXECUTION_FOR_DATASETID_SUBMITION_LOCK, dataset.getDatasetId()));
    executionDatasetIdLock.lock();

    // Add the workflow execution to the database. Then release the lock.
    final String objectId;
    try {
      String storedWorkflowExecutionId = workflowExecutionDao
          .existsAndNotCompleted(dataset.getDatasetId());
      if (storedWorkflowExecutionId != null) {
        throw new WorkflowExecutionAlreadyExistsException(String
            .format("Workflow execution already exists with id %s and is not completed",
                storedWorkflowExecutionId));
      }
      workflowExecution.setWorkflowStatus(WorkflowStatus.INQUEUE);
      if (metisUserView == null || metisUserView.getUserId() == null) {
        workflowExecution.setStartedBy(SystemId.STARTED_BY_SYSTEM.name());
      } else {
        workflowExecution.setStartedBy(metisUserView.getUserId());
      }
      workflowExecution.setCreatedDate(new Date());
      objectId = workflowExecutionDao.create(workflowExecution).getId().toString();
    } finally {
      executionDatasetIdLock.unlock();
    }

    // Add the workflow execution to the queue.
    workflowExecutorManager.addWorkflowExecutionToQueue(objectId, priority);
    LOGGER.info("WorkflowExecution with id: {}, added to execution queue", objectId);

    // Done. Get a fresh copy of the workflow execution to return.
    return workflowExecutionDao.getById(objectId);
  }

  /**
   * Request to cancel a workflow execution. The execution will go into a cancelling state until it's properly {@link
   * WorkflowStatus#CANCELLED} from the system
   *
   * @param metisUserView the user wishing to perform this operation
   * @param executionId the execution identifier of the execution to cancel
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link NoWorkflowExecutionFoundException} if no worklfowExecution could be found</li>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * </ul>
   */
  public void cancelWorkflowExecution(MetisUserView metisUserView, String executionId)
      throws GenericMetisException {

    WorkflowExecution workflowExecution = workflowExecutionDao.getById(executionId);
    if (workflowExecution != null) {
      authorizer.authorizeWriteExistingDatasetById(metisUserView, workflowExecution.getDatasetId());
    }
    if (workflowExecution != null && (
        workflowExecution.getWorkflowStatus() == WorkflowStatus.RUNNING
            || workflowExecution.getWorkflowStatus() == WorkflowStatus.INQUEUE)) {
      workflowExecutionDao.setCancellingState(workflowExecution, metisUserView);
      LOGGER.info("Cancelling user workflow execution with id: {}", workflowExecution.getId());
    } else {
      throw new NoWorkflowExecutionFoundException(String
          .format("Running workflowExecution with executionId: %s, does not exist or not active",
              executionId));
    }
  }

  /**
   * The number of WorkflowExecutions that would be returned if a get all request would be performed.
   *
   * @return the number representing the size during a get all request
   */
  public int getWorkflowExecutionsPerRequest() {
    return workflowExecutionDao.getWorkflowExecutionsPerRequest();
  }

  /**
   * Check if a specified {@code pluginType} is allowed for execution. This is checked based on, if there was a previous
   * successful finished plugin that follows a specific order (unless the {@code enforcedPredecessorType} is used) and that has
   * the latest successful harvest plugin as an ancestor.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param datasetId the dataset identifier of which the executions are based on
   * @param pluginType the pluginType to be checked for allowance of execution
   * @param enforcedPredecessorType optional, the plugin type to be used as source data
   * @return the abstractMetisPlugin that the execution on {@code pluginType} will be based on. Can be null if the {@code
   * pluginType} is the first one in the total order of executions e.g. One of the harvesting plugins.
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link PluginExecutionNotAllowed} if the no plugin was found so the {@code pluginType}
   * will be based upon</li>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * </ul>
   */
  public ExecutablePlugin getLatestFinishedPluginByDatasetIdIfPluginTypeAllowedForExecution(
      MetisUserView metisUserView, String datasetId, ExecutablePluginType pluginType,
      ExecutablePluginType enforcedPredecessorType) throws GenericMetisException {
    authorizer.authorizeReadExistingDatasetById(metisUserView, datasetId);
    return Optional.ofNullable(
            dataEvolutionUtils.computePredecessorPlugin(pluginType, enforcedPredecessorType, datasetId))
        .map(PluginWithExecutionId::getPlugin).orElse(null);
  }

  /**
   * Get all WorkflowExecutions paged.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param datasetId the dataset identifier filter, can be null to get all datasets
   * @param workflowStatuses a set of workflow statuses to filter, can be empty or null
   * @param orderField the field to be used to sort the results
   * @param ascending a boolean value to request the ordering to ascending or descending
   * @param nextPage the nextPage token
   * @return A list of all the WorkflowExecutions found. If the user is not admin, the list is filtered to only show those
   * executions that are in the user's organization.
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * </ul>
   */
  public ResponseListWrapper<WorkflowExecutionView> getAllWorkflowExecutions(
      MetisUserView metisUserView,
      String datasetId, Set<WorkflowStatus> workflowStatuses, DaoFieldNames orderField,
      boolean ascending, int nextPage) throws GenericMetisException {

    // Authorize
    if (datasetId == null) {
      authorizer.authorizeReadAllDatasets(metisUserView);
    } else {
      authorizer.authorizeReadExistingDatasetById(metisUserView, datasetId);
    }

    // Determine the dataset IDs to filter on.
    final Set<String> datasetIds;
    if (datasetId == null) {
      datasetIds = getDatasetIdsToFilterOn(metisUserView);
    } else {
      datasetIds = Collections.singleton(datasetId);
    }

    // Find the executions.
    final ResultList<WorkflowExecution> data = workflowExecutionDao
        .getAllWorkflowExecutions(datasetIds, workflowStatuses, orderField, ascending, nextPage, 1,
            false);

    // Compile and return the result.
    final List<WorkflowExecutionView> convertedData = data.getResults().stream().map(
        execution -> new WorkflowExecutionView(execution, isIncremental(execution),
            OrchestratorService::canDisplayRawXml)).collect(Collectors.toList());
    final ResponseListWrapper<WorkflowExecutionView> result = new ResponseListWrapper<>();
    result.setResultsAndLastPage(convertedData, getWorkflowExecutionsPerRequest(), nextPage,
        data.isMaxResultCountReached());
    return result;
  }

  /**
   * Checks if a workflow execution is an incremental one based on root ancestor information
   *
   * @param workflowExecution the workflow execution to check
   * @return true if incremental, false otherwise
   */
  private boolean isIncremental(WorkflowExecution workflowExecution) {
    final AbstractMetisPlugin<?> firstPluginInList = workflowExecution.getMetisPlugins().get(0);
    // Non-executable plugins are not to be checked
    if (!(firstPluginInList instanceof AbstractExecutablePlugin)) {
      return false;
    }

    final ExecutablePlugin harvestPlugin = new DataEvolutionUtils(workflowExecutionDao)
        .getRootAncestor(new PluginWithExecutionId<>(workflowExecution,
            ((AbstractExecutablePlugin<?>) firstPluginInList)))
        .getPlugin();

    // depublication can also be a root ancestor.
    if (harvestPlugin.getPluginMetadata().getExecutablePluginType()
        == ExecutablePluginType.DEPUBLISH) {
      return false;
    }

    // Check the harvesting types
    if (!DataEvolutionUtils.getHarvestPluginGroup()
        .contains(harvestPlugin.getPluginMetadata().getExecutablePluginType())) {
      throw new IllegalStateException(String.format(
          "workflowExecutionId: %s, pluginId: %s - Found plugin root that is not a harvesting plugin.",
          workflowExecution.getId(), harvestPlugin.getId()));
    }
    return (harvestPlugin.getPluginMetadata() instanceof AbstractHarvestPluginMetadata)
        && ((AbstractHarvestPluginMetadata) harvestPlugin.getPluginMetadata())
        .isIncrementalHarvest();
  }

  /**
   * Get the overview of WorkflowExecutions. This returns a list of executions ordered to display an overview. First the ones in
   * queue, then those in progress and then those that are finalized. They will be sorted by creation date. This method does
   * support pagination.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param pluginStatuses the plugin statuses to filter. Can be null.
   * @param pluginTypes the plugin types to filter. Can be null.
   * @param fromDate the date from where the results should start. Can be null.
   * @param toDate the date to where the results should end. Can be null.
   * @param nextPage the nextPage token, the end of the list is marked with -1 on the response
   * @param pageCount the number of pages that are requested
   * @return a list of all the WorkflowExecutions together with the datasets that they belong to.
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link eu.europeana.metis.exception.UserUnauthorizedException} if the user is not
   * authenticated or authorized to perform this operation</li>
   * </ul>
   */
  public ResponseListWrapper<ExecutionAndDatasetView> getWorkflowExecutionsOverview(
      MetisUserView metisUserView, Set<PluginStatus> pluginStatuses, Set<PluginType> pluginTypes,
      Date fromDate, Date toDate, int nextPage, int pageCount) throws GenericMetisException {
    authorizer.authorizeReadAllDatasets(metisUserView);
    final Set<String> datasetIds = getDatasetIdsToFilterOn(metisUserView);
    final ResultList<ExecutionDatasetPair> resultList;
    if (datasetIds == null || !datasetIds.isEmpty()) {
      //Match results filtering using specified dataset ids or without dataset id filter if it's null
      resultList = workflowExecutionDao
          .getWorkflowExecutionsOverview(datasetIds, pluginStatuses, pluginTypes, fromDate, toDate,
              nextPage, pageCount);
    } else {
      //Result should be empty if dataset set is empty
      resultList = new ResultList<>(Collections.emptyList(), false);
    }
    final List<ExecutionAndDatasetView> views = resultList.getResults().stream()
        .map(result -> new ExecutionAndDatasetView(result.getExecution(), result.getDataset()))
        .collect(Collectors.toList());
    final ResponseListWrapper<ExecutionAndDatasetView> result = new ResponseListWrapper<>();
    result.setResultsAndLastPage(views, getWorkflowExecutionsPerRequest(), nextPage, pageCount,
        resultList.isMaxResultCountReached());
    return result;
  }

  /**
   * Get the list of dataset ids that the provided user owns.
   * <p>The return value can be one of the following:
   * <ul>
   *  <li>null when a user has role {@link AccountRole#METIS_ADMIN}, which means the user owns everything</li>
   *  <li>Empty set if the user owns nothing</li>
   *  <li>Non-Empty set with the dataset ids that the user owns, for users that have a role other than {@link AccountRole#METIS_ADMIN}</li>
   * </ul>
   * </p>
   *
   * @param metisUserView the user to use for getting the owned dataset ids
   * @return a set of dataset ids
   */
  private Set<String> getDatasetIdsToFilterOn(MetisUserView metisUserView) {
    final Set<String> datasetIds;
    if (metisUserView.getAccountRole() == AccountRole.METIS_ADMIN) {
      datasetIds = null;
    } else {
      datasetIds = datasetDao.getAllDatasetsByOrganizationId(metisUserView.getOrganizationId()).stream()
          .map(Dataset::getDatasetId).collect(Collectors.toSet());
    }
    return datasetIds;
  }

  /**
   * Retrieve dataset level information of past executions {@link DatasetExecutionInformation}
   *
   * @param metisUserView the user wishing to perform this operation
   * @param datasetId the dataset identifier to generate the information for
   * @return the structured class containing all the execution information
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * </ul>
   */
  public DatasetExecutionInformation getDatasetExecutionInformation(MetisUserView metisUserView,
      String datasetId) throws GenericMetisException {
    authorizer.authorizeReadExistingDatasetById(metisUserView, datasetId);
    return getDatasetExecutionInformation(datasetId);
  }

  DatasetExecutionInformation getDatasetExecutionInformation(String datasetId) {

    // Obtain the relevant parts of the execution history
    final ExecutablePlugin lastHarvestPlugin = Optional.ofNullable(
            workflowExecutionDao.getLatestSuccessfulExecutablePlugin(datasetId, HARVEST_TYPES, false))
        .map(PluginWithExecutionId::getPlugin).orElse(null);
    final PluginWithExecutionId<MetisPlugin> firstPublishPluginWithExecutionId = workflowExecutionDao
        .getFirstSuccessfulPlugin(datasetId, PUBLISH_TYPES);
    final MetisPlugin firstPublishPlugin = firstPublishPluginWithExecutionId == null ? null
        : firstPublishPluginWithExecutionId.getPlugin();
    final ExecutablePlugin lastExecutablePreviewPlugin = Optional.ofNullable(workflowExecutionDao
            .getLatestSuccessfulExecutablePlugin(datasetId, EXECUTABLE_PREVIEW_TYPES, false))
        .map(PluginWithExecutionId::getPlugin).orElse(null);
    final ExecutablePlugin lastExecutablePublishPlugin = Optional.ofNullable(workflowExecutionDao
            .getLatestSuccessfulExecutablePlugin(datasetId, EXECUTABLE_PUBLISH_TYPES, false))
        .map(PluginWithExecutionId::getPlugin).orElse(null);
    final PluginWithExecutionId<MetisPlugin> latestPreviewPluginWithExecutionId = workflowExecutionDao
        .getLatestSuccessfulPlugin(datasetId, PREVIEW_TYPES);
    final PluginWithExecutionId<MetisPlugin> latestPublishPluginWithExecutionId = workflowExecutionDao
        .getLatestSuccessfulPlugin(datasetId, PUBLISH_TYPES);
    final MetisPlugin lastPreviewPlugin = latestPreviewPluginWithExecutionId == null ? null
        : latestPreviewPluginWithExecutionId.getPlugin();
    final MetisPlugin lastPublishPlugin = latestPublishPluginWithExecutionId == null ? null
        : latestPublishPluginWithExecutionId.getPlugin();
    final ExecutablePlugin lastExecutableDepublishPlugin = Optional.ofNullable(workflowExecutionDao
            .getLatestSuccessfulExecutablePlugin(datasetId, EXECUTABLE_DEPUBLISH_TYPES, false))
        .map(PluginWithExecutionId::getPlugin).orElse(null);

    // Obtain the relevant current executions
    final WorkflowExecution runningOrInQueueExecution = getRunningOrInQueueExecution(datasetId);
    final boolean isPreviewCleaningOrRunning = isPluginInWorkflowCleaningOrRunning(
        runningOrInQueueExecution, PREVIEW_TYPES);
    final boolean isPublishCleaningOrRunning = isPluginInWorkflowCleaningOrRunning(
        runningOrInQueueExecution, PUBLISH_TYPES);

    final DatasetExecutionInformation executionInfo = new DatasetExecutionInformation();
    // Set the last harvest information
    if (Objects.nonNull(lastHarvestPlugin)) {
      executionInfo.setLastHarvestedDate(lastHarvestPlugin.getFinishedDate());
      executionInfo.setLastHarvestedRecords(
          lastHarvestPlugin.getExecutionProgress().getProcessedRecords() - lastHarvestPlugin
              .getExecutionProgress().getErrors());
    }
    final Date now = new Date();
    setPreviewInformation(executionInfo, lastExecutablePreviewPlugin, lastPreviewPlugin,
        isPreviewCleaningOrRunning, now);
    setPublishInformation(executionInfo, firstPublishPlugin, lastExecutablePublishPlugin,
        lastPublishPlugin, lastExecutableDepublishPlugin, isPublishCleaningOrRunning, now,
        datasetId);

    return executionInfo;
  }

  WorkflowExecution getRunningOrInQueueExecution(String datasetId) {
    return workflowExecutionDao.getRunningOrInQueueExecution(datasetId);
  }

  private void setPreviewInformation(DatasetExecutionInformation executionInfo,
      ExecutablePlugin lastExecutablePreviewPlugin, MetisPlugin lastPreviewPlugin,
      boolean isPreviewCleaningOrRunning, Date date) {

    boolean lastPreviewHasDeletedRecords = computeRecordCountsAndCheckDeletedRecords(lastExecutablePreviewPlugin,
        executionInfo::setLastPreviewRecords, executionInfo::setTotalPreviewRecords);

    //Compute more general information of the plugin
    if (Objects.nonNull(lastPreviewPlugin)) {
      executionInfo.setLastPreviewDate(lastPreviewPlugin.getFinishedDate());
      //Check if we have information about the total records
      final boolean recordsAvailable;
      if (executionInfo.getTotalPreviewRecords() > 0) {
        recordsAvailable = true;
      } else if (executionInfo.getTotalPreviewRecords() == 0) {
        recordsAvailable = false;
      } else {
        recordsAvailable = executionInfo.getLastPreviewRecords() > 0 || lastPreviewHasDeletedRecords;
      }

      executionInfo.setLastPreviewRecordsReadyForViewing(recordsAvailable &&
          !isPreviewCleaningOrRunning && isPreviewOrPublishReadyForViewing(lastPreviewPlugin,
          date));
    }
  }

  private void setPublishInformation(DatasetExecutionInformation executionInfo,
      MetisPlugin firstPublishPlugin, ExecutablePlugin lastExecutablePublishPlugin,
      MetisPlugin lastPublishPlugin, ExecutablePlugin lastExecutableDepublishPlugin,
      boolean isPublishCleaningOrRunning, Date date, String datasetId) {

    // Set the first publication information
    executionInfo.setFirstPublishedDate(
        firstPublishPlugin == null ? null : firstPublishPlugin.getFinishedDate());

    // Determine the depublication situation of the dataset
    final boolean depublishHappenedAfterLatestExecutablePublish =
        lastExecutableDepublishPlugin != null && lastExecutablePublishPlugin != null &&
            lastExecutablePublishPlugin.getFinishedDate()
                .compareTo(lastExecutableDepublishPlugin.getFinishedDate()) < 0;
    /* TODO JV below we use the fact that a record depublish cannot follow a dataset depublish (so
        we don't have to look further into the past for all depublish actions after the last
        publish). We should make this code more robust by not assuming that here. */
    final boolean datasetCurrentlyDepublished = depublishHappenedAfterLatestExecutablePublish
        && (lastExecutableDepublishPlugin instanceof DepublishPlugin)
        && ((DepublishPluginMetadata) lastExecutableDepublishPlugin.getPluginMetadata())
        .isDatasetDepublish();

    boolean lastPublishHasDeletedRecords = computeRecordCountsAndCheckDeletedRecords(lastExecutablePublishPlugin,
        executionInfo::setLastPublishedRecords, executionInfo::setTotalPublishedRecords);

    //Compute depublish count
    final int depublishedRecordCount;
    if (datasetCurrentlyDepublished) {
      depublishedRecordCount = executionInfo.getLastPublishedRecords();
    } else {
      depublishedRecordCount = (int) depublishRecordIdDao
          .countSuccessfullyDepublishedRecordIdsForDataset(datasetId);
    }

    //Compute more general information of the plugin
    if (Objects.nonNull(lastPublishPlugin)) {
      executionInfo.setLastPublishedDate(lastPublishPlugin.getFinishedDate());

      //Check if we have information about the total records
      final boolean recordsAvailable;
      if (executionInfo.getTotalPublishedRecords() > 0) {
        recordsAvailable = true;
      } else if (executionInfo.getTotalPublishedRecords() == 0) {
        recordsAvailable = false;
      } else {
        recordsAvailable =
            !datasetCurrentlyDepublished && (executionInfo.getLastPublishedRecords() > depublishedRecordCount
                || lastPublishHasDeletedRecords);
      }
      executionInfo.setLastPublishedRecordsReadyForViewing(
          recordsAvailable && !isPublishCleaningOrRunning && isPreviewOrPublishReadyForViewing(
              lastPublishPlugin, date));
    }

    // Set the last depublished information.
    executionInfo.setLastDepublishedRecords(depublishedRecordCount);
    if (Objects.nonNull(lastExecutableDepublishPlugin)) {
      executionInfo.setLastDepublishedDate(lastExecutableDepublishPlugin.getFinishedDate());
    }

    // Set the publication status.
    final PublicationStatus status;
    if (datasetCurrentlyDepublished) {
      status = PublicationStatus.DEPUBLISHED;
    } else if (lastExecutablePublishPlugin != null) {
      status = PublicationStatus.PUBLISHED;
    } else {
      status = null;
    }
    executionInfo.setPublicationStatus(status);
  }

  private boolean computeRecordCountsAndCheckDeletedRecords(ExecutablePlugin executablePlugin, IntConsumer lastRecordsSetter,
      IntConsumer totalRecordsSetter) {
    int recordCount = 0;
    int totalRecordCount = -1;
    boolean hasDeletedRecords = false;
    if (Objects.nonNull(executablePlugin)) {
      recordCount = executablePlugin.getExecutionProgress().getProcessedRecords()
          - executablePlugin.getExecutionProgress().getErrors();
      totalRecordCount = executablePlugin.getExecutionProgress().getTotalDatabaseRecords();
      hasDeletedRecords = executablePlugin.getExecutionProgress().getDeletedRecords() > 0;
    }
    lastRecordsSetter.accept(recordCount);
    totalRecordsSetter.accept(totalRecordCount);
    return hasDeletedRecords;
  }

  private boolean isPreviewOrPublishReadyForViewing(MetisPlugin plugin, Date now) {
    final boolean dataIsValid = !(plugin instanceof ExecutablePlugin)
        || MetisPlugin.getDataStatus((ExecutablePlugin) plugin) == DataStatus.VALID;
    final boolean enoughTimeHasPassed = getSolrCommitPeriodInMins() < DateUtils
        .calculateDateDifference(plugin.getFinishedDate(), now, TimeUnit.MINUTES);
    return dataIsValid && enoughTimeHasPassed;
  }

  private boolean isPluginInWorkflowCleaningOrRunning(WorkflowExecution runningOrInQueueExecution,
      Set<PluginType> pluginTypes) {
    return runningOrInQueueExecution != null && runningOrInQueueExecution.getMetisPlugins().stream()
        .filter(metisPlugin -> pluginTypes.contains(metisPlugin.getPluginType()))
        .map(AbstractMetisPlugin::getPluginStatus).anyMatch(
            pluginStatus -> pluginStatus == PluginStatus.CLEANING
                || pluginStatus == PluginStatus.RUNNING);
  }

  /**
   * Retrieve dataset level history of past executions {@link DatasetExecutionInformation}
   *
   * @param metisUserView the user wishing to perform this operation
   * @param datasetId the dataset identifier to generate the history for
   * @return the structured class containing all the execution history, ordered by date descending.
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * </ul>
   */
  public ExecutionHistory getDatasetExecutionHistory(MetisUserView metisUserView, String datasetId)
      throws GenericMetisException {

    // Check that the user is authorized
    authorizer.authorizeReadExistingDatasetById(metisUserView, datasetId);

    // Get the executions from the database
    final ResultList<WorkflowExecution> allExecutions = workflowExecutionDao
        .getAllWorkflowExecutions(Set.of(datasetId), null, DaoFieldNames.STARTED_DATE, false, 0,
            null, false);

    // Filter the executions.
    final List<Execution> executions = allExecutions.getResults().stream().filter(
            entry -> entry.getMetisPlugins().stream().anyMatch(OrchestratorService::canDisplayRawXml))
        .map(OrchestratorService::convert).collect(Collectors.toList());

    // Done
    final ExecutionHistory result = new ExecutionHistory();
    result.setExecutions(executions);
    return result;
  }

  private static Execution convert(WorkflowExecution execution) {
    final Execution result = new Execution();
    result.setWorkflowExecutionId(execution.getId().toString());
    result.setStartedDate(execution.getStartedDate());
    return result;
  }

  /**
   * Retrieve a list of plugins with data availability {@link PluginsWithDataAvailability} for a given workflow execution.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param executionId the identifier of the execution for which to get the plugins
   * @return the structured class containing all the execution history, ordered by date descending.
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link eu.europeana.metis.core.exceptions.NoWorkflowExecutionFoundException} if an
   * non-existing execution ID or version is provided.</li>
   * <li>{@link eu.europeana.metis.exception.UserUnauthorizedException} if the user is not
   * authenticated or authorized to perform this operation</li>
   * </ul>
   */
  public PluginsWithDataAvailability getExecutablePluginsWithDataAvailability(
      MetisUserView metisUserView,
      String executionId) throws GenericMetisException {

    // Get the execution and do the authorization check.
    final WorkflowExecution execution = getWorkflowExecutionByExecutionId(metisUserView, executionId);
    if (execution == null) {
      throw new NoWorkflowExecutionFoundException(
          String.format("No workflow execution found for workflowExecutionId: %s", executionId));
    }

    // Compile the result.
    final List<PluginWithDataAvailability> plugins = execution.getMetisPlugins().stream()
        .filter(OrchestratorService::canDisplayRawXml).map(OrchestratorService::convert)
        .collect(Collectors.toList());
    final PluginsWithDataAvailability result = new PluginsWithDataAvailability();
    result.setPlugins(plugins);

    // Done.
    return result;
  }

  private static PluginWithDataAvailability convert(MetisPlugin plugin) {
    final PluginWithDataAvailability result = new PluginWithDataAvailability();
    result.setCanDisplayRawXml(true); // If this method is called, it is known that it can display.
    result.setPluginType(plugin.getPluginType());
    return result;
  }

  private static boolean canDisplayRawXml(MetisPlugin plugin) {
    final boolean result;
    if (plugin instanceof ExecutablePlugin) {
      final boolean dataIsValid =
          MetisPlugin.getDataStatus(((ExecutablePlugin) plugin)) == DataStatus.VALID;
      final ExecutionProgress progress = ((ExecutablePlugin) plugin).getExecutionProgress();
      final boolean pluginHasBlacklistedType = Optional.of(((ExecutablePlugin) plugin))
          .map(ExecutablePlugin::getPluginMetadata)
          .map(ExecutablePluginMetadata::getExecutablePluginType)
          .map(NO_XML_PREVIEW_TYPES::contains).orElse(Boolean.TRUE);
      result = dataIsValid && !pluginHasBlacklistedType && progress != null
          && progress.getProcessedRecords() > progress.getErrors();
    } else {
      result = false;
    }
    return result;
  }

  /**
   * Get the evolution of the records from when they were first imported until (and excluding) the specified version.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param executionId The ID of the workflow exection in which the version is created.
   * @param pluginType The step within the workflow execution that created the version.
   * @return The record evolution.
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link eu.europeana.metis.core.exceptions.NoWorkflowExecutionFoundException} if an
   * non-existing execution ID or version is provided.</li>
   * <li>{@link eu.europeana.metis.exception.UserUnauthorizedException} if the user is not
   * authenticated or authorized to perform this operation</li>
   * </ul>
   */
  public VersionEvolution getRecordEvolutionForVersion(MetisUserView metisUserView, String executionId,
      PluginType pluginType) throws GenericMetisException {

    // Get the execution and do the authorization check.
    final WorkflowExecution execution = getWorkflowExecutionByExecutionId(metisUserView, executionId);
    if (execution == null) {
      throw new NoWorkflowExecutionFoundException(
          String.format("No workflow execution found for workflowExecutionId: %s", executionId));
    }

    // Find the plugin (workflow step) in question.
    final AbstractMetisPlugin<?> targetPlugin = execution.getMetisPluginWithType(pluginType)
        .orElseThrow(() -> new NoWorkflowExecutionFoundException(String
            .format("No plugin of type %s found for workflowExecution with id: %s",
                pluginType.name(), execution)));

    // Compile the version evolution.
    final Collection<Pair<ExecutablePlugin, WorkflowExecution>> evolutionSteps = dataEvolutionUtils
        .compileVersionEvolution(targetPlugin, execution);
    final VersionEvolution versionEvolution = new VersionEvolution();
    versionEvolution.setEvolutionSteps(evolutionSteps.stream().map(step -> {
      final VersionEvolutionStep evolutionStep = new VersionEvolutionStep();
      final ExecutablePlugin plugin = step.getLeft();
      evolutionStep.setWorkflowExecutionId(step.getRight().getId().toString());
      evolutionStep.setPluginType(plugin.getPluginMetadata().getExecutablePluginType());
      evolutionStep.setFinishedTime(plugin.getFinishedDate());
      return evolutionStep;
    }).collect(Collectors.toList()));
    return versionEvolution;
  }

  /**
   * This method returns whether currently it is permitted/possible to perform incremental harvesting for the given dataset.
   *
   * @param metisUserView the user wishing to perform this operation
   * @param datasetId The ID of the dataset for which to check.
   * @return Whether we can perform incremental harvesting for the dataset.
   * @throws GenericMetisException which can be one of:
   * <ul>
   * <li>{@link NoDatasetFoundException} if the dataset identifier provided does not exist</li>
   * <li>{@link UserUnauthorizedException} if the user is not authorized to perform this task</li>
   * </ul>
   */
  public boolean isIncrementalHarvestingAllowed(MetisUserView metisUserView, String datasetId)
      throws GenericMetisException {

    // Check that the user is authorized
    authorizer.authorizeReadExistingDatasetById(metisUserView, datasetId);

    // Do the check.
    return workflowValidationUtils.isIncrementalHarvestingAllowed(datasetId);
  }

  public int getSolrCommitPeriodInMins() {
    synchronized (this) {
      return solrCommitPeriodInMins;
    }
  }

  public void setSolrCommitPeriodInMins(int solrCommitPeriodInMins) {
    synchronized (this) {
      this.solrCommitPeriodInMins = solrCommitPeriodInMins;
    }
  }
}
