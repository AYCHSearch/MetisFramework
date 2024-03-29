package eu.europeana.metis.core.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import eu.europeana.cloud.client.dps.rest.DpsClient;
import eu.europeana.cloud.common.model.dps.TaskState;
import eu.europeana.metis.core.dao.WorkflowExecutionDao;
import eu.europeana.metis.core.utils.TestObjectFactory;
import eu.europeana.metis.core.workflow.CancelledSystemId;
import eu.europeana.metis.core.workflow.WorkflowExecution;
import eu.europeana.metis.core.workflow.WorkflowStatus;
import eu.europeana.metis.core.workflow.plugins.AbstractMetisPlugin;
import eu.europeana.metis.core.workflow.plugins.ExecutablePlugin.MonitorResult;
import eu.europeana.metis.core.workflow.plugins.EcloudBasePluginParameters;
import eu.europeana.metis.core.workflow.plugins.ExecutionProgress;
import eu.europeana.metis.core.workflow.plugins.OaipmhHarvestPlugin;
import eu.europeana.metis.core.workflow.plugins.OaipmhHarvestPluginMetadata;
import eu.europeana.metis.core.workflow.plugins.PluginStatus;
import eu.europeana.metis.exception.ExternalTaskException;
import java.util.ArrayList;
import java.util.Date;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-10-17
 */
class TestWorkflowExecutor {

  private static WorkflowExecutionDao workflowExecutionDao;
  private static DpsClient dpsClient;
  private static WorkflowExecutionMonitor workflowExecutionMonitor;
  private static PersistenceProvider persistenceProvider;
  private static WorkflowExecutionSettings workflowExecutionSettings;

  @BeforeAll
  static void prepare() {
    workflowExecutionDao = Mockito.mock(WorkflowExecutionDao.class);
    dpsClient = Mockito.mock(DpsClient.class);
    workflowExecutionMonitor = Mockito.mock(WorkflowExecutionMonitor.class);
    persistenceProvider =
        new PersistenceProvider(null, null, workflowExecutionDao, null, dpsClient);
    workflowExecutionSettings = Mockito.mock(WorkflowExecutionSettings.class);
    when(workflowExecutionSettings.getPeriodOfNoProcessedRecordsChangeInMinutes()).thenReturn(10);
  }

  @AfterEach
  void cleanUp() {
    Mockito.reset(workflowExecutionDao);
    Mockito.reset(workflowExecutionMonitor);
    Mockito.reset(dpsClient);
  }

  @Test
  void callNonMockedFieldValue() throws Exception {
    ExecutionProgress currentlyProcessingExecutionProgress = new ExecutionProgress();
    currentlyProcessingExecutionProgress.setStatus(TaskState.CURRENTLY_PROCESSING);
    ExecutionProgress processedExecutionProgress = new ExecutionProgress();
    processedExecutionProgress.setStatus(TaskState.PROCESSED);

    OaipmhHarvestPlugin oaipmhHarvestPlugin = Mockito.mock(OaipmhHarvestPlugin.class);
    OaipmhHarvestPluginMetadata oaipmhHarvestPluginMetadata = new OaipmhHarvestPluginMetadata();
    oaipmhHarvestPlugin.setPluginMetadata(oaipmhHarvestPluginMetadata);
    ArrayList<AbstractMetisPlugin> abstractMetisPlugins = new ArrayList<>();
    abstractMetisPlugins.add(oaipmhHarvestPlugin);

    WorkflowExecution workflowExecution = TestObjectFactory.createWorkflowExecutionObject();
    workflowExecution.setId(new ObjectId());
    workflowExecution.setWorkflowStatus(WorkflowStatus.INQUEUE);
    workflowExecution.setMetisPlugins(abstractMetisPlugins);

    when(oaipmhHarvestPlugin.getPluginMetadata()).thenReturn(oaipmhHarvestPluginMetadata);
    when(oaipmhHarvestPlugin.monitor(dpsClient))
        .thenReturn(new MonitorResult(currentlyProcessingExecutionProgress.getStatus(), null))
        .thenReturn(new MonitorResult(processedExecutionProgress.getStatus(), null));
    when(oaipmhHarvestPlugin.getExecutionProgress())
        .thenReturn(currentlyProcessingExecutionProgress)
        .thenReturn(processedExecutionProgress);

    when(workflowExecutionMonitor.claimExecution(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);
    doNothing().when(workflowExecutionDao).updateMonitorInformation(workflowExecution);
    when(workflowExecutionDao.isCancelling(workflowExecution.getId())).thenReturn(false);

    doNothing().when(workflowExecutionDao).updateWorkflowPlugins(workflowExecution);
    when(workflowExecutionDao.update(workflowExecution))
        .thenReturn(workflowExecution.getId().toString());

    WorkflowExecutor workflowExecutor = new WorkflowExecutor(workflowExecution.getId().toString(),
        persistenceProvider, workflowExecutionSettings, workflowExecutionMonitor);
    workflowExecutor.call();

    verify(workflowExecutionDao, times(2)).updateMonitorInformation(workflowExecution);
    verify(workflowExecutionDao, times(1)).update(workflowExecution);

    InOrder inOrderForPlugin = inOrder(oaipmhHarvestPlugin);
    inOrderForPlugin.verify(oaipmhHarvestPlugin, times(2))
        .setPluginStatusAndResetFailMessage(PluginStatus.RUNNING);
    inOrderForPlugin.verify(oaipmhHarvestPlugin)
        .setPluginStatusAndResetFailMessage(PluginStatus.FINISHED);
    verify(oaipmhHarvestPlugin, atMost(3)).setPluginStatusAndResetFailMessage(any());
    verify(oaipmhHarvestPlugin, never()).setFailMessage(anyString());
  }

  @Test
  void callNonMockedFieldValue_ExceptionWhenExecuteIsCalled() throws Exception {

    OaipmhHarvestPlugin oaipmhHarvestPlugin = Mockito.mock(OaipmhHarvestPlugin.class);
    OaipmhHarvestPluginMetadata oaipmhHarvestPluginMetadata = new OaipmhHarvestPluginMetadata();
    oaipmhHarvestPlugin.setPluginMetadata(oaipmhHarvestPluginMetadata);
    ArrayList<AbstractMetisPlugin> abstractMetisPlugins = new ArrayList<>();
    abstractMetisPlugins.add(oaipmhHarvestPlugin);

    WorkflowExecution workflowExecution = TestObjectFactory.createWorkflowExecutionObject();
    workflowExecution.setId(new ObjectId());
    workflowExecution.setWorkflowStatus(WorkflowStatus.INQUEUE);
    workflowExecution.setMetisPlugins(abstractMetisPlugins);

    doThrow(new ExternalTaskException("Some error")).when(oaipmhHarvestPlugin).execute(any(String.class),
        any(DpsClient.class), any(EcloudBasePluginParameters.class));

    when(oaipmhHarvestPlugin.getPluginMetadata()).thenReturn(oaipmhHarvestPluginMetadata);

    when(workflowExecutionMonitor.claimExecution(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);
    doNothing().when(workflowExecutionDao).updateMonitorInformation(workflowExecution);
    when(workflowExecutionDao.isCancelling(workflowExecution.getId())).thenReturn(false);

    doNothing().when(workflowExecutionDao).updateWorkflowPlugins(workflowExecution);
    when(workflowExecutionDao.update(workflowExecution))
        .thenReturn(workflowExecution.getId().toString());

    WorkflowExecutor workflowExecutor = new WorkflowExecutor(workflowExecution.getId().toString(),
        persistenceProvider, workflowExecutionSettings, workflowExecutionMonitor);
    workflowExecutor.call();

    verify(workflowExecutionDao, times(1)).update(workflowExecution);

    verify(oaipmhHarvestPlugin).setPluginStatusAndResetFailMessage(PluginStatus.FAILED);
    verify(oaipmhHarvestPlugin, atMost(1)).setPluginStatusAndResetFailMessage(any());
    verify(oaipmhHarvestPlugin).setFailMessage(notNull());
    verify(oaipmhHarvestPlugin, times(1)).setFailMessage(anyString());
  }

  @Test
  void callNonMockedFieldValue_DROPPEDExeternalTaskButNotCancelled() throws Exception {
    ExecutionProgress currentlyProcessingExecutionProgress = new ExecutionProgress();
    currentlyProcessingExecutionProgress.setStatus(TaskState.CURRENTLY_PROCESSING);
    ExecutionProgress droppedExecutionProgress = new ExecutionProgress();
    droppedExecutionProgress.setStatus(TaskState.DROPPED);

    OaipmhHarvestPlugin oaipmhHarvestPlugin = Mockito.mock(OaipmhHarvestPlugin.class);
    OaipmhHarvestPluginMetadata oaipmhHarvestPluginMetadata = new OaipmhHarvestPluginMetadata();
    oaipmhHarvestPlugin.setPluginMetadata(oaipmhHarvestPluginMetadata);
    ArrayList<AbstractMetisPlugin> abstractMetisPlugins = new ArrayList<>();
    abstractMetisPlugins.add(oaipmhHarvestPlugin);

    WorkflowExecution workflowExecution = TestObjectFactory.createWorkflowExecutionObject();
    workflowExecution.setId(new ObjectId());
    workflowExecution.setWorkflowStatus(WorkflowStatus.INQUEUE);
    workflowExecution.setMetisPlugins(abstractMetisPlugins);

    when(oaipmhHarvestPlugin.getPluginMetadata()).thenReturn(oaipmhHarvestPluginMetadata);
    when(oaipmhHarvestPlugin.monitor(dpsClient))
        .thenReturn(new MonitorResult(currentlyProcessingExecutionProgress.getStatus(), null))
        .thenReturn(new MonitorResult(droppedExecutionProgress.getStatus(), null));
    when(oaipmhHarvestPlugin.getExecutionProgress())
        .thenReturn(currentlyProcessingExecutionProgress)
        .thenReturn(droppedExecutionProgress);

    when(workflowExecutionMonitor.claimExecution(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);
    doNothing().when(workflowExecutionDao).updateMonitorInformation(workflowExecution);
    when(workflowExecutionDao.isCancelling(workflowExecution.getId())).thenReturn(false);

    doNothing().when(workflowExecutionDao).updateWorkflowPlugins(workflowExecution);
    when(workflowExecutionDao.update(workflowExecution))
        .thenReturn(workflowExecution.getId().toString());

    WorkflowExecutor workflowExecutor = new WorkflowExecutor(workflowExecution.getId().toString(),
        persistenceProvider, workflowExecutionSettings, workflowExecutionMonitor);
    workflowExecutor.call();

    verify(workflowExecutionDao, times(2)).updateMonitorInformation(workflowExecution);
    verify(workflowExecutionDao, times(1)).update(workflowExecution);

    InOrder inOrderForPlugin = inOrder(oaipmhHarvestPlugin);
    inOrderForPlugin.verify(oaipmhHarvestPlugin, times(2))
        .setPluginStatusAndResetFailMessage(PluginStatus.RUNNING);
    inOrderForPlugin.verify(oaipmhHarvestPlugin)
        .setPluginStatusAndResetFailMessage(PluginStatus.FAILED);
    verify(oaipmhHarvestPlugin, atMost(3)).setPluginStatusAndResetFailMessage(any());
    verify(oaipmhHarvestPlugin).setFailMessage(notNull());
    verify(oaipmhHarvestPlugin, times(1)).setFailMessage(anyString());
  }

  @Test
  void callNonMockedFieldValue_ConsecutiveMonitorFailures() throws Exception {
    ExecutionProgress currentlyProcessingExecutionProgress = new ExecutionProgress();
    currentlyProcessingExecutionProgress.setStatus(TaskState.CURRENTLY_PROCESSING);

    OaipmhHarvestPlugin oaipmhHarvestPlugin = Mockito.mock(OaipmhHarvestPlugin.class);
    OaipmhHarvestPluginMetadata oaipmhHarvestPluginMetadata = new OaipmhHarvestPluginMetadata();
    oaipmhHarvestPlugin.setPluginMetadata(oaipmhHarvestPluginMetadata);
    ArrayList<AbstractMetisPlugin> abstractMetisPlugins = new ArrayList<>();
    abstractMetisPlugins.add(oaipmhHarvestPlugin);

    WorkflowExecution workflowExecution = TestObjectFactory.createWorkflowExecutionObject();
    workflowExecution.setId(new ObjectId());
    workflowExecution.setWorkflowStatus(WorkflowStatus.INQUEUE);
    workflowExecution.setMetisPlugins(abstractMetisPlugins);

    when(oaipmhHarvestPlugin.getPluginMetadata()).thenReturn(oaipmhHarvestPluginMetadata);
    when(oaipmhHarvestPlugin.monitor(dpsClient)).thenThrow(new ExternalTaskException("Some error"))
        .thenThrow(new ExternalTaskException("Some error"));
    when(oaipmhHarvestPlugin.getExecutionProgress())
        .thenReturn(currentlyProcessingExecutionProgress);

    when(workflowExecutionMonitor.claimExecution(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);
    doNothing().when(workflowExecutionDao).updateMonitorInformation(workflowExecution);
    when(workflowExecutionDao.isCancelling(workflowExecution.getId())).thenReturn(false);

    doNothing().when(workflowExecutionDao).updateWorkflowPlugins(workflowExecution);
    when(workflowExecutionDao.update(workflowExecution))
        .thenReturn(workflowExecution.getId().toString());

    WorkflowExecutor workflowExecutor = new WorkflowExecutor(workflowExecution.getId().toString(),
        persistenceProvider, workflowExecutionSettings, workflowExecutionMonitor);
    workflowExecutor.call();

    verify(workflowExecutionDao, times(1)).update(workflowExecution);

    verify(oaipmhHarvestPlugin).setPluginStatusAndResetFailMessage(PluginStatus.FAILED);
    verify(oaipmhHarvestPlugin, atMost(1)).setPluginStatusAndResetFailMessage(any());
    verify(oaipmhHarvestPlugin).setFailMessage(notNull());
    verify(oaipmhHarvestPlugin, times(1)).setFailMessage(anyString());
  }

  @Test
  void callNonMockedFieldValue_ReachPendingState_and_then_finish() throws Exception {
    ExecutionProgress currentlyProcessingExecutionProgress = new ExecutionProgress();
    currentlyProcessingExecutionProgress.setStatus(TaskState.CURRENTLY_PROCESSING);
    ExecutionProgress processedExecutionProgress = new ExecutionProgress();
    processedExecutionProgress.setStatus(TaskState.PROCESSED);

    OaipmhHarvestPlugin oaipmhHarvestPlugin = Mockito.mock(OaipmhHarvestPlugin.class);
    OaipmhHarvestPluginMetadata oaipmhHarvestPluginMetadata = new OaipmhHarvestPluginMetadata();
    oaipmhHarvestPlugin.setPluginMetadata(oaipmhHarvestPluginMetadata);
    ArrayList<AbstractMetisPlugin> abstractMetisPlugins = new ArrayList<>();
    abstractMetisPlugins.add(oaipmhHarvestPlugin);

    WorkflowExecution workflowExecution = TestObjectFactory.createWorkflowExecutionObject();
    workflowExecution.setId(new ObjectId());
    workflowExecution.setWorkflowStatus(WorkflowStatus.INQUEUE);
    workflowExecution.setMetisPlugins(abstractMetisPlugins);

    when(oaipmhHarvestPlugin.getPluginMetadata()).thenReturn(oaipmhHarvestPluginMetadata);
    when(oaipmhHarvestPlugin.monitor(dpsClient))
        .thenThrow(new ExternalTaskException("Some error", new HttpServerErrorException(
            HttpStatus.BAD_GATEWAY)))
        .thenThrow(new ExternalTaskException("Some error", new HttpServerErrorException(
            HttpStatus.BAD_GATEWAY)))
        .thenThrow(new ExternalTaskException("Some error", new HttpServerErrorException(
            HttpStatus.BAD_GATEWAY)))
        .thenThrow(new ExternalTaskException("Some error", new HttpServerErrorException(
            HttpStatus.BAD_GATEWAY)))
        .thenReturn(new MonitorResult(currentlyProcessingExecutionProgress.getStatus(), null))
        .thenReturn(new MonitorResult(processedExecutionProgress.getStatus(), null));
    when(oaipmhHarvestPlugin.getExecutionProgress())
        .thenReturn(currentlyProcessingExecutionProgress)
        .thenReturn(processedExecutionProgress);

    when(workflowExecutionMonitor.claimExecution(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);
    doNothing().when(workflowExecutionDao).updateMonitorInformation(workflowExecution);
    when(workflowExecutionDao.isCancelling(workflowExecution.getId())).thenReturn(false);

    doNothing().when(workflowExecutionDao).updateWorkflowPlugins(workflowExecution);
    when(workflowExecutionDao.update(workflowExecution))
        .thenReturn(workflowExecution.getId().toString());

    WorkflowExecutor workflowExecutor = new WorkflowExecutor(workflowExecution.getId().toString(),
        persistenceProvider, workflowExecutionSettings, workflowExecutionMonitor);
    workflowExecutor.call();

    verify(workflowExecutionDao, times(1)).update(workflowExecution);

    InOrder inOrderForPlugin = inOrder(oaipmhHarvestPlugin);
    inOrderForPlugin.verify(oaipmhHarvestPlugin, times(1))
        .setPluginStatusAndResetFailMessage(PluginStatus.PENDING);
    inOrderForPlugin.verify(oaipmhHarvestPlugin, times(2))
        .setPluginStatusAndResetFailMessage(PluginStatus.RUNNING);
    inOrderForPlugin.verify(oaipmhHarvestPlugin, times(1))
        .setPluginStatusAndResetFailMessage(PluginStatus.FINISHED);
    verify(oaipmhHarvestPlugin, atMost(4)).setPluginStatusAndResetFailMessage(any());
    verify(oaipmhHarvestPlugin, never()).setFailMessage(anyString());
  }


  @Test
  void callNonMockedFieldValueCancellingState() throws Exception {
    ExecutionProgress currentlyProcessingExecutionProgress = new ExecutionProgress();
    currentlyProcessingExecutionProgress.setStatus(TaskState.CURRENTLY_PROCESSING);
    ExecutionProgress processedExecutionProgress = new ExecutionProgress();
    processedExecutionProgress.setStatus(TaskState.PROCESSED);

    OaipmhHarvestPlugin oaipmhHarvestPlugin = Mockito.mock(OaipmhHarvestPlugin.class);
    OaipmhHarvestPluginMetadata oaipmhHarvestPluginMetadata = new OaipmhHarvestPluginMetadata();
    oaipmhHarvestPlugin.setPluginMetadata(oaipmhHarvestPluginMetadata);
    ArrayList<AbstractMetisPlugin> abstractMetisPlugins = new ArrayList<>();
    abstractMetisPlugins.add(oaipmhHarvestPlugin);

    WorkflowExecution workflowExecution = TestObjectFactory.createWorkflowExecutionObject();
    workflowExecution.setId(new ObjectId());
    workflowExecution.setWorkflowStatus(WorkflowStatus.INQUEUE);
    workflowExecution.setMetisPlugins(abstractMetisPlugins);

    when(oaipmhHarvestPlugin.getPluginMetadata()).thenReturn(oaipmhHarvestPluginMetadata);
    when(oaipmhHarvestPlugin.monitor(dpsClient))
        .thenReturn(new MonitorResult(currentlyProcessingExecutionProgress.getStatus(), null))
        .thenReturn(new MonitorResult(processedExecutionProgress.getStatus(), null));
    when(oaipmhHarvestPlugin.getExecutionProgress())
        .thenReturn(currentlyProcessingExecutionProgress)
        .thenReturn(processedExecutionProgress);
    doNothing().when(oaipmhHarvestPlugin)
        .cancel(dpsClient, CancelledSystemId.SYSTEM_MINUTE_CAP_EXPIRE.name());

    when(workflowExecutionMonitor.claimExecution(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);
    doNothing().when(workflowExecutionDao).updateMonitorInformation(workflowExecution);
    when(workflowExecutionDao.isCancelling(workflowExecution.getId())).thenReturn(false)
        .thenReturn(true);
    when(workflowExecutionDao.getById(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);

    doNothing().when(workflowExecutionDao).updateWorkflowPlugins(workflowExecution);
    when(workflowExecutionDao.update(workflowExecution))
        .thenReturn(workflowExecution.getId().toString());

    WorkflowExecutor workflowExecutor = new WorkflowExecutor(workflowExecution.getId().toString(),
        persistenceProvider, workflowExecutionSettings, workflowExecutionMonitor);
    workflowExecutor.call();

    verify(workflowExecutionDao, times(2)).updateMonitorInformation(workflowExecution);
    verify(workflowExecutionDao, times(1)).update(workflowExecution);

    verify(oaipmhHarvestPlugin, never()).setFailMessage(anyString());
  }

  @Test
  void callExecutionInRUNNINGState() throws ExternalTaskException {
    ExecutionProgress currentlyProcessingExecutionProgress = new ExecutionProgress();
    currentlyProcessingExecutionProgress.setStatus(TaskState.CURRENTLY_PROCESSING);
    ExecutionProgress processedExecutionProgress = new ExecutionProgress();
    processedExecutionProgress.setStatus(TaskState.PROCESSED);

    OaipmhHarvestPlugin oaipmhHarvestPlugin = Mockito.spy(OaipmhHarvestPlugin.class);
    oaipmhHarvestPlugin.setPluginStatus(PluginStatus.FINISHED);
    OaipmhHarvestPluginMetadata oaipmhHarvestPluginMetadata = new OaipmhHarvestPluginMetadata();
    oaipmhHarvestPlugin.setPluginMetadata(oaipmhHarvestPluginMetadata);
    ArrayList<AbstractMetisPlugin> abstractMetisPlugins = new ArrayList<>();
    abstractMetisPlugins.add(oaipmhHarvestPlugin);

    WorkflowExecution workflowExecution = TestObjectFactory.createWorkflowExecutionObject();
    workflowExecution.setId(new ObjectId());
    workflowExecution.setWorkflowStatus(WorkflowStatus.RUNNING);
    workflowExecution.setMetisPlugins(abstractMetisPlugins);
    workflowExecution.setStartedDate(new Date());

    when(oaipmhHarvestPlugin.getPluginMetadata()).thenReturn(oaipmhHarvestPluginMetadata);
    doReturn(new MonitorResult(currentlyProcessingExecutionProgress.getStatus(), null))
        .when(oaipmhHarvestPlugin).monitor(dpsClient);
    when(oaipmhHarvestPlugin.getExecutionProgress())
        .thenReturn(currentlyProcessingExecutionProgress);

    when(workflowExecutionMonitor.claimExecution(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);
    doNothing().when(workflowExecutionDao).updateMonitorInformation(workflowExecution);
    when(workflowExecutionDao.isCancelling(workflowExecution.getId())).thenReturn(false);
    when(oaipmhHarvestPlugin.monitor(dpsClient))
        .thenReturn(new MonitorResult(currentlyProcessingExecutionProgress.getStatus(), null))
        .thenReturn(new MonitorResult(processedExecutionProgress.getStatus(), null));
    doNothing().when(workflowExecutionDao).updateWorkflowPlugins(workflowExecution);
    when(workflowExecutionDao.update(workflowExecution))
        .thenReturn(workflowExecution.getId().toString());

    WorkflowExecutor workflowExecutor = new WorkflowExecutor(workflowExecution.getId().toString(),
        persistenceProvider, workflowExecutionSettings, workflowExecutionMonitor);
    workflowExecutor.call();

    assertEquals(WorkflowStatus.FINISHED, workflowExecution.getWorkflowStatus());
    assertNotNull(workflowExecution.getStartedDate());
    assertNotNull(workflowExecution.getUpdatedDate());
    assertNotNull(workflowExecution.getFinishedDate());
    assertNotNull(workflowExecution.getMetisPlugins().get(0).getFinishedDate());
  }

  @Test
  void callExecutionThatMayNotBeClaimed() {
    when(workflowExecutionMonitor.claimExecution(any())).thenReturn(null);

    WorkflowExecutor workflowExecutor = new WorkflowExecutor("testId", persistenceProvider,
        workflowExecutionSettings, workflowExecutionMonitor);
    workflowExecutor.call();

    verify(workflowExecutionMonitor, times(1)).claimExecution(any());
    verifyNoMoreInteractions(workflowExecutionDao);
  }

  @Test
  void callCancellingStateINQUEUE() throws ExternalTaskException {
    ExecutionProgress currentlyProcessingExecutionProgress = new ExecutionProgress();
    currentlyProcessingExecutionProgress.setStatus(TaskState.DROPPED);

    OaipmhHarvestPlugin oaipmhHarvestPlugin = Mockito.spy(new OaipmhHarvestPlugin());
    OaipmhHarvestPluginMetadata oaipmhHarvestPluginMetadata = new OaipmhHarvestPluginMetadata();
    oaipmhHarvestPlugin.setPluginMetadata(oaipmhHarvestPluginMetadata);
    ArrayList<AbstractMetisPlugin> abstractMetisPlugins = new ArrayList<>();
    abstractMetisPlugins.add(oaipmhHarvestPlugin);
    final ObjectId objectId = new ObjectId();
    WorkflowExecution workflowExecution = TestObjectFactory.createWorkflowExecutionObject();
    workflowExecution.setId(objectId);
    workflowExecution.setMetisPlugins(abstractMetisPlugins);
    workflowExecution.setCancelledBy(CancelledSystemId.SYSTEM_MINUTE_CAP_EXPIRE.name());

    when(workflowExecutionMonitor.claimExecution(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);
    when(workflowExecutionDao.isCancelling(workflowExecution.getId())).thenReturn(true);
    doNothing().when(oaipmhHarvestPlugin)
        .cancel(dpsClient, CancelledSystemId.SYSTEM_MINUTE_CAP_EXPIRE.name());
    doReturn(new MonitorResult(currentlyProcessingExecutionProgress.getStatus(), null))
        .when(oaipmhHarvestPlugin).monitor(dpsClient);
    when(workflowExecutionDao.getById(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);

    WorkflowExecutor workflowExecutor = new WorkflowExecutor(workflowExecution.getId().toString(),
        persistenceProvider, workflowExecutionSettings, workflowExecutionMonitor);
    workflowExecutor.call();

    ArgumentCaptor<WorkflowExecution> workflowExecutionArgumentCaptor = ArgumentCaptor
        .forClass(WorkflowExecution.class);
    verify(workflowExecutionDao, times(1)).update(workflowExecutionArgumentCaptor.capture());
    assertEquals(WorkflowStatus.CANCELLED,
        workflowExecutionArgumentCaptor.getValue().getWorkflowStatus());
    assertEquals(CancelledSystemId.SYSTEM_MINUTE_CAP_EXPIRE.name(),
        workflowExecutionArgumentCaptor.getValue().getCancelledBy());
  }

  @Test
  void callCancellingStateRUNNING() throws ExternalTaskException {
    ExecutionProgress currentlyProcessingExecutionProgress = new ExecutionProgress();
    currentlyProcessingExecutionProgress.setStatus(TaskState.DROPPED);

    OaipmhHarvestPlugin oaipmhHarvestPlugin = Mockito.spy(new OaipmhHarvestPlugin());
    oaipmhHarvestPlugin.setPluginStatus(PluginStatus.RUNNING);
    OaipmhHarvestPluginMetadata oaipmhHarvestPluginMetadata = new OaipmhHarvestPluginMetadata();
    oaipmhHarvestPlugin.setPluginMetadata(oaipmhHarvestPluginMetadata);
    ArrayList<AbstractMetisPlugin> abstractMetisPlugins = new ArrayList<>();
    abstractMetisPlugins.add(oaipmhHarvestPlugin);
    final ObjectId objectId = new ObjectId();
    WorkflowExecution workflowExecution = TestObjectFactory.createWorkflowExecutionObject();
    workflowExecution.setId(objectId);
    workflowExecution.setMetisPlugins(abstractMetisPlugins);
    workflowExecution.setWorkflowStatus(WorkflowStatus.RUNNING);
    workflowExecution.setCancelledBy(CancelledSystemId.SYSTEM_MINUTE_CAP_EXPIRE.name());

    when(workflowExecutionMonitor.claimExecution(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);
    when(workflowExecutionDao.isCancelling(workflowExecution.getId())).thenReturn(true);
    doNothing().when(oaipmhHarvestPlugin)
        .cancel(dpsClient, CancelledSystemId.SYSTEM_MINUTE_CAP_EXPIRE.name());
    doReturn(new MonitorResult(currentlyProcessingExecutionProgress.getStatus(), null))
        .when(oaipmhHarvestPlugin).monitor(dpsClient);
    when(workflowExecutionDao.getById(workflowExecution.getId().toString()))
        .thenReturn(workflowExecution);

    WorkflowExecutor workflowExecutor = new WorkflowExecutor(workflowExecution.getId().toString(),
        persistenceProvider, workflowExecutionSettings, workflowExecutionMonitor);
    workflowExecutor.call();

    ArgumentCaptor<WorkflowExecution> workflowExecutionArgumentCaptor = ArgumentCaptor
        .forClass(WorkflowExecution.class);
    verify(workflowExecutionDao, times(1)).update(workflowExecutionArgumentCaptor.capture());
    assertEquals(WorkflowStatus.CANCELLED,
        workflowExecutionArgumentCaptor.getValue().getWorkflowStatus());
    assertEquals(CancelledSystemId.SYSTEM_MINUTE_CAP_EXPIRE.name(),
        workflowExecutionArgumentCaptor.getValue().getCancelledBy());
  }
}
