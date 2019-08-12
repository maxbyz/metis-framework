package eu.europeana.metis.core.execution;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import eu.europeana.metis.core.dao.WorkflowExecutionDao;
import eu.europeana.metis.core.exceptions.PluginExecutionNotAllowed;
import eu.europeana.metis.core.utils.TestObjectFactory;
import eu.europeana.metis.core.workflow.plugins.AbstractExecutablePlugin;
import eu.europeana.metis.core.workflow.plugins.EnrichmentPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ExecutablePluginFactory;
import eu.europeana.metis.core.workflow.plugins.ExecutablePluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ExecutablePluginType;
import eu.europeana.metis.core.workflow.plugins.ExecutionProgress;
import eu.europeana.metis.core.workflow.plugins.HTTPHarvestPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.IndexToPreviewPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.IndexToPublishPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.LinkCheckingPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.MediaProcessPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.NormalizationPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.OaipmhHarvestPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.TransformationPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ValidationExternalPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ValidationInternalPluginMetadata;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2018-02-01
 */
class TestExecutionRules {

  private static WorkflowExecutionDao workflowExecutionDao;

  @BeforeAll
  static void prepare() {
    workflowExecutionDao = Mockito.mock(WorkflowExecutionDao.class);
  }

  @AfterEach
  void cleanUp() {
    Mockito.reset(workflowExecutionDao);
  }

  @Test
  void testGetPredecessorPlugin_HarvestPlugin() throws PluginExecutionNotAllowed {
    assertNull(ExecutionRules.getPredecessorPlugin(ExecutablePluginType.OAIPMH_HARVEST, null,
        Integer.toString(TestObjectFactory.DATASETID), workflowExecutionDao));
    assertNull(ExecutionRules.getPredecessorPlugin(ExecutablePluginType.HTTP_HARVEST, null,
        Integer.toString(TestObjectFactory.DATASETID), workflowExecutionDao));
    Mockito.verify(workflowExecutionDao, Mockito.never())
        .getLatestSuccessfulPlugin(anyString(), any(), anyBoolean());
  }

  @Test
  void testGetPredecessorPlugin_EnforcedPluginType() throws PluginExecutionNotAllowed {

    // Create plugin object
    final AbstractExecutablePlugin plugin = ExecutablePluginFactory
        .createPlugin(new OaipmhHarvestPluginMetadata());
    when(workflowExecutionDao.getLatestSuccessfulPlugin(Integer.toString(TestObjectFactory.DATASETID),
            EnumSet.of(ExecutablePluginType.OAIPMH_HARVEST), true)).thenReturn(plugin);

    // Test without errors
    plugin.setExecutionProgress(new ExecutionProgress());
    plugin.getExecutionProgress().setProcessedRecords(1);
    plugin.getExecutionProgress().setErrors(0);
    assertSame(plugin, ExecutionRules.getPredecessorPlugin(ExecutablePluginType.TRANSFORMATION,
        ExecutablePluginType.OAIPMH_HARVEST, Integer.toString(TestObjectFactory.DATASETID),
        workflowExecutionDao));

    // Test with errors
    plugin.getExecutionProgress().setErrors(1);
    assertThrows(PluginExecutionNotAllowed.class, () -> ExecutionRules
        .getPredecessorPlugin(ExecutablePluginType.TRANSFORMATION,
            ExecutablePluginType.OAIPMH_HARVEST, Integer.toString(TestObjectFactory.DATASETID),
            workflowExecutionDao));
  }

  @Test
  void testGetPredecessorPlugin() throws PluginExecutionNotAllowed {
    final Set<ExecutablePluginType> pluginTypesSetThatPluginTypeCanBeBasedOn;
    pluginTypesSetThatPluginTypeCanBeBasedOn = new HashSet<>(
        ExecutionRules.getHarvestPluginGroup());
    testGetPredecessorPlugin(new ValidationExternalPluginMetadata(),
        pluginTypesSetThatPluginTypeCanBeBasedOn);
    testGetPredecessorPlugin(new TransformationPluginMetadata(),
        EnumSet.of(ExecutablePluginType.VALIDATION_EXTERNAL));
    testGetPredecessorPlugin(new ValidationInternalPluginMetadata(),
        EnumSet.of(ExecutablePluginType.TRANSFORMATION));
    testGetPredecessorPlugin(new NormalizationPluginMetadata(),
        EnumSet.of(ExecutablePluginType.VALIDATION_INTERNAL));
    testGetPredecessorPlugin(new EnrichmentPluginMetadata(),
        EnumSet.of(ExecutablePluginType.NORMALIZATION));
    testGetPredecessorPlugin(new MediaProcessPluginMetadata(),
        EnumSet.of(ExecutablePluginType.ENRICHMENT));
    testGetPredecessorPlugin(new IndexToPreviewPluginMetadata(),
        EnumSet.of(ExecutablePluginType.MEDIA_PROCESS));
    testGetPredecessorPlugin(new IndexToPublishPluginMetadata(),
        EnumSet.of(ExecutablePluginType.PREVIEW));
    testGetPredecessorPlugin(new LinkCheckingPluginMetadata(),
        ExecutionRules.getAllExceptLinkGroup());
    testGetPredecessorPlugin(new HTTPHarvestPluginMetadata(), Collections.emptySet());
    testGetPredecessorPlugin(new OaipmhHarvestPluginMetadata(), Collections.emptySet());
  }

  private void testGetPredecessorPlugin(ExecutablePluginMetadata metadata,
      Set<ExecutablePluginType> finishedPlugins) throws PluginExecutionNotAllowed {
    final AbstractExecutablePlugin plugin;
    if (!finishedPlugins.isEmpty()) {

      // Test without errors
      plugin = ExecutablePluginFactory.createPlugin(metadata);
      plugin.setExecutionProgress(new ExecutionProgress());
      plugin.getExecutionProgress().setProcessedRecords(1);
      plugin.getExecutionProgress().setErrors(0);
      when(workflowExecutionDao.getLatestSuccessfulPlugin(Integer.toString(
          TestObjectFactory.DATASETID), finishedPlugins, true)).thenReturn(plugin);
      assertSame(plugin,
          ExecutionRules.getPredecessorPlugin(metadata.getExecutablePluginType(), null,
              Integer.toString(TestObjectFactory.DATASETID), workflowExecutionDao));

      // Test with errors
      plugin.getExecutionProgress().setErrors(1);
      assertThrows(PluginExecutionNotAllowed.class, () -> ExecutionRules
          .getPredecessorPlugin(metadata.getExecutablePluginType(), null,
              Integer.toString(TestObjectFactory.DATASETID), workflowExecutionDao));
    } else {
      assertNull(ExecutionRules.getPredecessorPlugin(metadata.getExecutablePluginType(), null,
          Integer.toString(TestObjectFactory.DATASETID), workflowExecutionDao));
    }
  }
}
