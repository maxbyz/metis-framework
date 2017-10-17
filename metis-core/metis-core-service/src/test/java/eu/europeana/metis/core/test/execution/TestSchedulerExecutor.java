package eu.europeana.metis.core.test.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import eu.europeana.metis.core.exceptions.NoDatasetFoundException;
import eu.europeana.metis.core.execution.SchedulerExecutor;
import eu.europeana.metis.core.service.OrchestratorService;
import eu.europeana.metis.core.test.utils.TestObjectFactory;
import eu.europeana.metis.core.workflow.ScheduleFrequence;
import eu.europeana.metis.core.workflow.ScheduledUserWorkflow;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-10-17
 */
public class TestSchedulerExecutor {

  private static int periodicSchedulerCheckInSecs = 1;
  private static OrchestratorService orchestratorService;
  private static RedissonClient redissonClient;
  private static SchedulerExecutor schedulerExecutor;
  private static final String SCHEDULER_LOCK = "schedulerLock";

  @BeforeClass
  public static void prepare() {
    orchestratorService = Mockito.mock(OrchestratorService.class);
    redissonClient = Mockito.mock(RedissonClient.class);
    schedulerExecutor = new SchedulerExecutor(orchestratorService, redissonClient,
        periodicSchedulerCheckInSecs, false);
  }

  @After
  public void cleanUp() {
    Mockito.reset(orchestratorService);
    Mockito.reset(redissonClient);
  }

  @Test
  public void run() throws Exception {
    RLock rlock = mock(RLock.class);
    when(redissonClient.getFairLock(SCHEDULER_LOCK)).thenReturn(rlock);
    doNothing().when(rlock).lock();

    int userWorkflowExecutionsPerRequest = 3;
    int listSize = userWorkflowExecutionsPerRequest - 1; //To not trigger paging
    Date now = new Date();
    now.setTime(now.getTime() + periodicSchedulerCheckInSecs * 1000);
    List<ScheduledUserWorkflow> listOfScheduledUserWorkflowsWithDateONCE = TestObjectFactory
        .createListOfScheduledUserWorkflowsWithDateAndFrequence(listSize, now, ScheduleFrequence.ONCE);
    List<ScheduledUserWorkflow> listOfScheduledUserWorkflowsWithDateDAILY = TestObjectFactory
        .createListOfScheduledUserWorkflowsWithDateAndFrequence(listSize, now, ScheduleFrequence.DAILY);
    List<ScheduledUserWorkflow> listOfScheduledUserWorkflowsWithDateWEEKLY = TestObjectFactory
        .createListOfScheduledUserWorkflowsWithDateAndFrequence(listSize, now, ScheduleFrequence.WEEKLY);
    List<ScheduledUserWorkflow> listOfScheduledUserWorkflowsWithDateMONTHLY = TestObjectFactory
        .createListOfScheduledUserWorkflowsWithDateAndFrequence(listSize, now, ScheduleFrequence.MONTHLY);

    when(orchestratorService.getScheduledUserWorkflowsPerRequest())
        .thenReturn(userWorkflowExecutionsPerRequest);

    when(orchestratorService.getAllScheduledUserWorkflowsByDateRangeONCE(any(LocalDateTime.class),
        any(LocalDateTime.class), isNull()))
        .thenReturn(listOfScheduledUserWorkflowsWithDateONCE);
    when(
        orchestratorService.getAllScheduledUserWorkflows(any(ScheduleFrequence.class), isNull()))
        .thenReturn(listOfScheduledUserWorkflowsWithDateDAILY).thenReturn(
            listOfScheduledUserWorkflowsWithDateWEEKLY).thenReturn(
            listOfScheduledUserWorkflowsWithDateMONTHLY);
    doThrow(new NoDatasetFoundException("Some Error")).doNothing().when(orchestratorService)
        .addUserWorkflowInQueueOfUserWorkflowExecutions(anyString(), anyString(), anyString(),
            anyInt()); //Throw an exception as well, should continue execution after that
    doNothing().when(rlock).unlock();

    schedulerExecutor.run();

    verify(orchestratorService, times(4)).getScheduledUserWorkflowsPerRequest();
    verify(orchestratorService, times(1))
        .getAllScheduledUserWorkflowsByDateRangeONCE(any(LocalDateTime.class),
            any(LocalDateTime.class), isNull());
    verify(orchestratorService, times(3))
        .getAllScheduledUserWorkflows(any(ScheduleFrequence.class), isNull());
    verify(orchestratorService, times(listSize * 4))
        .addUserWorkflowInQueueOfUserWorkflowExecutions(anyString(), anyString(), anyString(),
            anyInt());
  }

  @Test
  public void runAllSchedulesOutOfRange() throws Exception {
    RLock rlock = mock(RLock.class);
    when(redissonClient.getFairLock(SCHEDULER_LOCK)).thenReturn(rlock);
    doNothing().when(rlock).lock();

    int userWorkflowExecutionsPerRequest = 3;
    int listSize = userWorkflowExecutionsPerRequest - 1; //To not trigger paging
    Date past = new Date();
    past.setTime(past.getTime() - periodicSchedulerCheckInSecs * 1000);
    List<ScheduledUserWorkflow> listOfScheduledUserWorkflowsWithDateDAILY = TestObjectFactory
        .createListOfScheduledUserWorkflowsWithDateAndFrequence(listSize, past, ScheduleFrequence.DAILY);
    List<ScheduledUserWorkflow> listOfScheduledUserWorkflowsWithDateWEEKLY = TestObjectFactory
        .createListOfScheduledUserWorkflowsWithDateAndFrequence(listSize, past, ScheduleFrequence.WEEKLY);
    List<ScheduledUserWorkflow> listOfScheduledUserWorkflowsWithDateMONTHLY = TestObjectFactory
        .createListOfScheduledUserWorkflowsWithDateAndFrequence(listSize, past, ScheduleFrequence.MONTHLY);

    when(orchestratorService.getScheduledUserWorkflowsPerRequest())
        .thenReturn(userWorkflowExecutionsPerRequest);

    when(orchestratorService.getAllScheduledUserWorkflowsByDateRangeONCE(any(LocalDateTime.class),
        any(LocalDateTime.class), isNull()))
        .thenReturn(new ArrayList<>());
    when(
        orchestratorService.getAllScheduledUserWorkflows(any(ScheduleFrequence.class), isNull()))
        .thenReturn(listOfScheduledUserWorkflowsWithDateDAILY).thenReturn(
        listOfScheduledUserWorkflowsWithDateWEEKLY).thenReturn(
        listOfScheduledUserWorkflowsWithDateMONTHLY);
    doThrow(new NoDatasetFoundException("Some Error")).doNothing().when(orchestratorService)
        .addUserWorkflowInQueueOfUserWorkflowExecutions(anyString(), anyString(), anyString(),
            anyInt()); //Throw an exception as well, should continue execution after that
    doNothing().when(rlock).unlock();

    schedulerExecutor.run();

    verify(orchestratorService, times(4)).getScheduledUserWorkflowsPerRequest();
    verify(orchestratorService, times(1))
        .getAllScheduledUserWorkflowsByDateRangeONCE(any(LocalDateTime.class),
            any(LocalDateTime.class), isNull());
    verify(orchestratorService, times(3))
        .getAllScheduledUserWorkflows(any(ScheduleFrequence.class), isNull());
    verify(orchestratorService, times(0))
        .addUserWorkflowInQueueOfUserWorkflowExecutions(anyString(), anyString(), anyString(),
            anyInt());
  }

  @Test
  public void runThatThrowsExceptionAndContinues() throws Exception {
    SchedulerExecutor schedulerExecutor = new SchedulerExecutor(orchestratorService, redissonClient,
        10, false);
    RLock rlock = mock(RLock.class);
    when(redissonClient.getFairLock(SCHEDULER_LOCK)).thenReturn(rlock);
    doNothing().when(rlock).lock();
    doNothing().when(rlock).unlock();

    Thread t = new Thread(schedulerExecutor::run);
    t.start();
    Awaitility.await().atMost(Duration.TWO_SECONDS).until(() -> untilThreadIsSleeping(t));
    t.interrupt();
    verifyNoMoreInteractions(orchestratorService);
  }

  @Test
  public void close() {
    when(redissonClient.isShutdown()).thenReturn(false);
    schedulerExecutor.close();
    verify(redissonClient, times(1)).shutdown();
  }

  private void untilThreadIsSleeping(Thread t) {
    Assert.assertEquals("java.lang.Thread", t.getStackTrace()[0].getClassName());
    Assert.assertEquals("sleep", t.getStackTrace()[0].getMethodName());
  }

}
