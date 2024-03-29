package eu.europeana.metis.core.service;

import eu.europeana.metis.core.dao.DatasetXsltDao;
import eu.europeana.metis.core.dataset.Dataset;
import eu.europeana.metis.core.dataset.DatasetXslt;
import eu.europeana.metis.core.workflow.ValidationProperties;
import eu.europeana.metis.core.workflow.Workflow;
import eu.europeana.metis.core.workflow.WorkflowExecution;
import eu.europeana.metis.core.workflow.plugins.AbstractExecutablePlugin;
import eu.europeana.metis.core.workflow.plugins.AbstractExecutablePluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ExecutablePlugin;
import eu.europeana.metis.core.workflow.plugins.ExecutablePluginFactory;
import eu.europeana.metis.core.workflow.plugins.IndexToPreviewPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.IndexToPublishPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.LinkCheckingPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.TransformationPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ValidationExternalPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.ValidationInternalPluginMetadata;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Class that contains various functionality for "helping" the {@link OrchestratorService}.
 *
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2018-10-11
 */
public class WorkflowExecutionFactory {

  private final DatasetXsltDao datasetXsltDao;

  private ValidationProperties validationExternalProperties; // Use getter and setter!
  private ValidationProperties validationInternalProperties; // Use getter and setter!
  private boolean metisUseAlternativeIndexingEnvironment; // Use getter and setter for this field!
  private int defaultSamplingSizeForLinkChecking; // Use getter and setter for this field!

  /**
   * Constructor with parameters required to support the {@link OrchestratorService}
   *
   * @param datasetXsltDao the Dao instance to access the dataset xslts
   */
  public WorkflowExecutionFactory(DatasetXsltDao datasetXsltDao) {
    this.datasetXsltDao = datasetXsltDao;
  }

  // Expect the dataset to be synced with eCloud.
  // Does not save the workflow execution.
  WorkflowExecution createWorkflowExecution(Workflow workflow, Dataset dataset,
      ExecutablePlugin predecessor, int priority) {

    // Create the plugins
    final List<AbstractExecutablePlugin> workflowPlugins = workflow.getMetisPluginsMetadata()
        .stream().filter(AbstractExecutablePluginMetadata::isEnabled)
        .map(metadata-> createWorkflowPlugin(metadata, dataset)).collect(Collectors.toList());

    // Set the predecessor
    if (predecessor != null) {
      workflowPlugins.get(0).getPluginMetadata().setPreviousRevisionInformation(predecessor);
    }

    // Done: create workflow with all the information.
    return new WorkflowExecution(dataset, workflowPlugins, priority);
  }

  private AbstractExecutablePlugin createWorkflowPlugin(
      AbstractExecutablePluginMetadata pluginMetadata, Dataset dataset) {

    // Add some extra configuration to the plugin metadata depending on the type.
    if (pluginMetadata instanceof TransformationPluginMetadata){
      setupXsltIdForPluginMetadata(dataset, ((TransformationPluginMetadata) pluginMetadata));
    } else if (pluginMetadata instanceof ValidationExternalPluginMetadata) {
      this.setupValidationExternalForPluginMetadata(
          (ValidationExternalPluginMetadata) pluginMetadata, getValidationExternalProperties());
    } else if (pluginMetadata instanceof ValidationInternalPluginMetadata) {
      this.setupValidationInternalForPluginMetadata(
          (ValidationInternalPluginMetadata) pluginMetadata, getValidationInternalProperties());
    } else if (pluginMetadata instanceof IndexToPreviewPluginMetadata) {
      ((IndexToPreviewPluginMetadata) pluginMetadata).setUseAlternativeIndexingEnvironment(
          isMetisUseAlternativeIndexingEnvironment());
    } else if (pluginMetadata instanceof IndexToPublishPluginMetadata) {
      ((IndexToPublishPluginMetadata) pluginMetadata).setUseAlternativeIndexingEnvironment(
          isMetisUseAlternativeIndexingEnvironment());
    } else if (pluginMetadata instanceof LinkCheckingPluginMetadata) {
      ((LinkCheckingPluginMetadata) pluginMetadata)
          .setSampleSize(getDefaultSamplingSizeForLinkChecking());
    }

    // Create the plugin
    return ExecutablePluginFactory.createPlugin(pluginMetadata);
  }

  private void setupValidationExternalForPluginMetadata(ValidationExternalPluginMetadata metadata,
      ValidationProperties validationProperties) {
    metadata.setUrlOfSchemasZip(validationProperties.getUrlOfSchemasZip());
    metadata.setSchemaRootPath(validationProperties.getSchemaRootPath());
    metadata.setSchematronRootPath(validationProperties.getSchematronRootPath());
  }

  private void setupValidationInternalForPluginMetadata(ValidationInternalPluginMetadata metadata,
      ValidationProperties validationProperties) {
    metadata.setUrlOfSchemasZip(validationProperties.getUrlOfSchemasZip());
    metadata.setSchemaRootPath(validationProperties.getSchemaRootPath());
    metadata.setSchematronRootPath(validationProperties.getSchematronRootPath());
  }

  private void setupXsltIdForPluginMetadata(Dataset dataset,
      TransformationPluginMetadata pluginMetadata) {
    DatasetXslt xsltObject;
    if (pluginMetadata.isCustomXslt()) {
      xsltObject = datasetXsltDao.getById(dataset.getXsltId().toString());
    } else {
      xsltObject = datasetXsltDao.getLatestDefaultXslt();
    }
    if (xsltObject != null && StringUtils.isNotEmpty(xsltObject.getXslt())) {
      pluginMetadata.setXsltId(xsltObject.getId().toString());
    }
    //DatasetName in Transformation should be a concatenation datasetId_datasetName
    pluginMetadata.setDatasetName(dataset.getDatasetId() + "_" + dataset.getDatasetName());
    pluginMetadata.setCountry(dataset.getCountry().getName());
    pluginMetadata.setLanguage(dataset.getLanguage().name().toLowerCase(Locale.US));
  }

  public ValidationProperties getValidationExternalProperties() {
    synchronized (this) {
      return validationExternalProperties;
    }
  }

  public void setValidationExternalProperties(ValidationProperties validationExternalProperties) {
    synchronized (this) {
      this.validationExternalProperties = validationExternalProperties;
    }
  }

  public ValidationProperties getValidationInternalProperties() {
    synchronized (this) {
      return validationInternalProperties;
    }
  }

  public void setValidationInternalProperties(ValidationProperties validationInternalProperties) {
    synchronized (this) {
      this.validationInternalProperties = validationInternalProperties;
    }
  }

  private boolean isMetisUseAlternativeIndexingEnvironment() {
    synchronized (this) {
      return metisUseAlternativeIndexingEnvironment;
    }
  }

  public void setMetisUseAlternativeIndexingEnvironment(
      boolean metisUseAlternativeIndexingEnvironment) {
    synchronized (this) {
      this.metisUseAlternativeIndexingEnvironment = metisUseAlternativeIndexingEnvironment;
    }
  }

  private int getDefaultSamplingSizeForLinkChecking() {
    synchronized (this) {
      return defaultSamplingSizeForLinkChecking;
    }
  }

  public void setDefaultSamplingSizeForLinkChecking(int defaultSamplingSizeForLinkChecking) {
    synchronized (this) {
      this.defaultSamplingSizeForLinkChecking = defaultSamplingSizeForLinkChecking;
    }
  }
}
