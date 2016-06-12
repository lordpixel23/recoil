/*
 * Copyright © 2016 Andrew Thompson
 */
package org.recoil.pixel.threadlocalexecutorservice;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 */
public class ThreadLocalExecutorServiceTest {

    private static final String INITIAL = "Initial";
    private static final String PARENT = "Parent";

    private final StringBuilder errorMessage = new StringBuilder();
    private final AtomicInteger numTasksRun = new AtomicInteger(0);
    private int expectedTasksRun = Integer.MAX_VALUE;
    private ExecutorService fixture;

    private final Thread.UncaughtExceptionHandler failOnException = (Thread t, Throwable e) -> {
        errorMessage.append(e.getMessage());
        errorMessage.append(Arrays.asList(e.getStackTrace()));
    };

    private final ExecutorService delegate = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread result = Executors.defaultThreadFactory().newThread(r);
            result.setUncaughtExceptionHandler(failOnException);
            return result;
        }
    });

    private final ThreadLocal<String> local = new ThreadLocal<String>() {

        @Override
        protected String initialValue() {
            return INITIAL;
        }

    };

    public ThreadLocalExecutorServiceTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() throws InterruptedException, ExecutionException {
        errorMessage.setLength(0);
        fixture = new ThreadLocalExecutorService(delegate, local);
        local.set(INITIAL);
        numTasksRun.set(0);
        expectedTasksRun = Integer.MAX_VALUE;
        assertChildThreadHasInitialValue();

    }

    @After
    public void tearDown() throws InterruptedException, ExecutionException {
        assertChildThreadHasInitialValue();
        waitForShutdown();
        assertTrue(errorMessage.toString(), errorMessage.length() == 0);
        assertEquals("Wrong number of tasks run", expectedTasksRun, numTasksRun.get());
    }

    /**
     * Test of submit method, of class ThreadLocalExecutorService.
     *
     * @throws java.lang.InterruptedException
     * @throws java.util.concurrent.ExecutionException
     */
    @Test
    public void testSubmit_Callable() throws InterruptedException, ExecutionException {
        local.set(PARENT);
        expectedTasksRun = 1;
        assertEquals("Wrong value returned", PARENT, fixture.submit(createTaskToReturnChildThreadValue()).get());
    }

    /**
     * Test of submit method, of class ThreadLocalExecutorService.
     * @throws java.lang.InterruptedException
     */
    @Test
    public void testSubmit_Runnable_GenericType() throws InterruptedException, ExecutionException {
        local.set(PARENT);
        expectedTasksRun = 1;
        final Integer sentinel = 23;
        assertEquals(sentinel, fixture.submit(createTaskToCheckChildThreadHasParentValue(), sentinel).get());
    }

    /**
     * Test of submit method, of class ThreadLocalExecutorService.
     *
     * @throws java.lang.InterruptedException
     */
    @Test
    public void testSubmit_Runnable() throws InterruptedException {
        local.set(PARENT);
        expectedTasksRun = 1;
        fixture.submit(createTaskToCheckChildThreadHasParentValue());
    }

    /**
     * Test of invokeAll method, of class ThreadLocalExecutorService.
     * @throws java.lang.InterruptedException
     * @throws java.util.concurrent.ExecutionException
     */
     @Test
     public void testInvokeAll_Collection() throws InterruptedException, ExecutionException {
        expectedTasksRun = 5;
        List<Callable<String>> tasks = Stream.generate(this::createTaskToReturnChildThreadValue).limit(expectedTasksRun).collect(toList());
        local.set(PARENT);
        for(Future<String> result : fixture.invokeAll(tasks)) {
            assertEquals(PARENT, result.get());
        }
     }

    /**
     * Test of invokeAll method, of class ThreadLocalExecutorService.
     * @throws java.lang.InterruptedException
     * @throws java.util.concurrent.ExecutionException
     */
     @Test
     public void testInvokeAll_3args() throws InterruptedException, ExecutionException {
        expectedTasksRun = 3;
        List<Callable<String>> tasks = Stream.generate(this::createTaskToReturnChildThreadValue).limit(expectedTasksRun).collect(toList());
        local.set(PARENT);
        for(Future<String> result : fixture.invokeAll(tasks, 15, TimeUnit.SECONDS)) {
            assertEquals(PARENT, result.get());
        }
     }

    /**
     * Test of invokeAny method, of class ThreadLocalExecutorService.
     * @throws java.util.concurrent.ExecutionException
     * @throws java.lang.InterruptedException
     */
     @Test
     public void testInvokeAny_Collection() throws ExecutionException, InterruptedException {
        expectedTasksRun = 1;
        List<Callable<String>> tasks = Stream.generate(this::createTaskToReturnChildThreadValue).limit(expectedTasksRun).collect(toList());
        local.set(PARENT);
        assertEquals(PARENT, fixture.invokeAny(tasks));
     }

    /**
     * Test of invokeAny method, of class ThreadLocalExecutorService.
     * @throws java.util.concurrent.ExecutionException
     * @throws java.lang.InterruptedException
     * @throws java.util.concurrent.TimeoutException
     */
     @Test
     public void testInvokeAny_3args() throws ExecutionException, InterruptedException, TimeoutException {
        expectedTasksRun = 1;
        List<Callable<String>> tasks = Stream.generate(this::createTaskToReturnChildThreadValue).limit(expectedTasksRun).collect(toList());
        local.set(PARENT);
        assertEquals(PARENT, fixture.invokeAny(tasks, 15, TimeUnit.SECONDS));
     }

    /**
     * Test of execute method, of class ThreadLocalExecutorService.
     *
     * @throws java.lang.InterruptedException
     * @throws java.util.concurrent.ExecutionException
     */
    @Test
    public void testExecute() throws InterruptedException, ExecutionException {
        local.set(PARENT);
        expectedTasksRun = 1;
        fixture.execute(createTaskToCheckChildThreadHasParentValue());
    }

    private void assertChildThreadHasInitialValue() throws InterruptedException, ExecutionException {
        assertEquals(INITIAL, delegate.submit(() -> local.get()).get());
    }

    private Runnable createTaskToCheckChildThreadHasParentValue() {
        Thread testCaseThread = Thread.currentThread();
        return () -> {
            try {
                assertEquals(PARENT, createTaskReturnChildThreadValue(testCaseThread).call());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
    }

    private Callable<String> createTaskToReturnChildThreadValue() {
        return createTaskReturnChildThreadValue(Thread.currentThread());
    }

    private Callable<String> createTaskReturnChildThreadValue(Thread testCaseThread) {
        return () -> {
            assertNotSame("Should be on a different thread", Thread.currentThread(), testCaseThread);
            numTasksRun.incrementAndGet();
            return local.get();
        };
    }

    private void waitForShutdown() throws InterruptedException {
        fixture.shutdown();
        fixture.awaitTermination(30, TimeUnit.SECONDS);
    }

}
