/*
 * Licensed to the jNode FTN Platform Development Team (jNode Team)
 * under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for 
 * additional information regarding copyright ownership.  
 * The jNode Team licenses this file to you under the 
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package jnode.main.threads;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jnode.main.MainHandler;

/**
 * Comprehensive unit tests for ThreadPool class
 * 
 * Tests constructor initialization, static methods, configuration,
 * concurrency behavior, and resource management.
 * 
 * @author jNode Team
 */
public class ThreadPoolTest {

    private MainHandler mockMainHandler;
    private ThreadPool threadPool;

    @BeforeEach
    void setUp() throws Exception {
        initializeTestEnvironment();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanupTestEnvironment();
    }

    private void initializeTestEnvironment() throws Exception {
        Properties props = new Properties();
        props.setProperty("threadpool.queue_size", "500");
        
        mockMainHandler = new MainHandler(props);
        setMainHandlerInstance(mockMainHandler);
    }

    private void cleanupTestEnvironment() throws Exception {
        if (threadPool != null) {
            shutdownThreadPool();
        }
        setMainHandlerInstance(null);
        setThreadPoolInstance(null);
    }

    private void setMainHandlerInstance(MainHandler handler) throws Exception {
        Field instanceField = MainHandler.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, handler);
    }

    private void setThreadPoolInstance(ThreadPool pool) throws Exception {
        Field selfField = ThreadPool.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(null, pool);
    }

    private void shutdownThreadPool() throws Exception {
        Field selfField = ThreadPool.class.getDeclaredField("self");
        selfField.setAccessible(true);
        ThreadPool pool = (ThreadPool) selfField.get(null);
        
        if (pool != null) {
            Field executorField = ThreadPool.class.getDeclaredField("executor");
            executorField.setAccessible(true);
            ThreadPoolExecutor executor = (ThreadPoolExecutor) executorField.get(pool);
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void testConstructorWithValidThreadCount() throws Exception {
        threadPool = new ThreadPool(4);
        
        assertNotNull(threadPool);
        
        Field executorField = ThreadPool.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) executorField.get(threadPool);
        
        assertEquals(4, executor.getCorePoolSize());
        assertEquals(6, executor.getMaximumPoolSize()); // 4 * 1.5
        assertEquals(30, executor.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(500, executor.getQueue().remainingCapacity() + executor.getQueue().size());
    }

    @Test
    void testConstructorWithSingleThread() throws Exception {
        threadPool = new ThreadPool(1);
        
        Field executorField = ThreadPool.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) executorField.get(threadPool);
        
        assertEquals(1, executor.getCorePoolSize());
        assertEquals(1, executor.getMaximumPoolSize()); // 1 * 1.5 = 1.5 -> 1
    }

    @Test
    void testConstructorWithLargeThreadCount() throws Exception {
        threadPool = new ThreadPool(20);
        
        Field executorField = ThreadPool.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) executorField.get(threadPool);
        
        assertEquals(20, executor.getCorePoolSize());
        assertEquals(30, executor.getMaximumPoolSize()); // 20 * 1.5
    }

    @Test
    void testConstructorWithCustomQueueSize() throws Exception {
        Properties props = new Properties();
        props.setProperty("threadpool.queue_size", "2000");
        
        MainHandler customHandler = new MainHandler(props);
        setMainHandlerInstance(customHandler);
        
        threadPool = new ThreadPool(4);
        
        Field executorField = ThreadPool.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) executorField.get(threadPool);
        
        assertEquals(2000, executor.getQueue().remainingCapacity() + executor.getQueue().size());
    }

    @Test
    void testConstructorWithDefaultQueueSizeWhenMainHandlerNull() throws Exception {
        setMainHandlerInstance(null);
        
        threadPool = new ThreadPool(4);
        
        Field executorField = ThreadPool.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) executorField.get(threadPool);
        
        assertEquals(1000, executor.getQueue().remainingCapacity() + executor.getQueue().size());
    }

    @Test
    void testConstructorWithInvalidQueueSizeProperty() throws Exception {
        Properties props = new Properties();
        props.setProperty("threadpool.queue_size", "invalid");
        
        MainHandler customHandler = new MainHandler(props);
        setMainHandlerInstance(customHandler);
        
        // Should throw NumberFormatException for invalid property value
        assertThrows(NumberFormatException.class, () -> {
            new ThreadPool(4);
        });
    }

    @Test
    void testStaticExecuteWithValidRunnable() throws Exception {
        threadPool = new ThreadPool(2);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);
        
        ThreadPool.execute(() -> {
            result.set(42);
            latch.countDown();
        });
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(42, result.get());
    }

    @Test
    void testStaticExecuteWithNullRunnable() throws Exception {
        threadPool = new ThreadPool(2);
        
        // ThreadPoolExecutor.execute(null) throws NullPointerException
        assertThrows(NullPointerException.class, () -> {
            ThreadPool.execute(null);
        });
    }

    @Test
    void testStaticExecuteWhenThreadPoolNotInitialized() throws Exception {
        // ThreadPool not initialized, should not execute
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);
        
        ThreadPool.execute(() -> {
            result.set(42);
            latch.countDown();
        });
        
        // Task should not execute since ThreadPool is not initialized
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS));
        assertEquals(0, result.get());
    }

    @Test
    void testStaticIsBusyWhenThreadPoolNotInitialized() {
        // Should return true when ThreadPool is not initialized
        assertTrue(ThreadPool.isBusy());
    }

    @Test
    void testStaticIsBusyWhenThreadPoolNotBusy() throws Exception {
        threadPool = new ThreadPool(4);
        
        // Should return false when queue size is less than max pool size
        assertFalse(ThreadPool.isBusy());
    }

    @Test
    void testStaticIsBusyWhenThreadPoolBusy() throws Exception {
        threadPool = new ThreadPool(2);
        
        // Fill up the queue to make it busy
        CountDownLatch blockingLatch = new CountDownLatch(1);
        CountDownLatch executionLatch = new CountDownLatch(10);
        
        // Submit tasks that will block
        for (int i = 0; i < 10; i++) {
            ThreadPool.execute(() -> {
                try {
                    blockingLatch.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                executionLatch.countDown();
            });
        }
        
        // Give tasks time to queue up
        Thread.sleep(100);
        
        // Should be busy now
        assertTrue(ThreadPool.isBusy());
        
        // Release the blocking tasks
        blockingLatch.countDown();
        executionLatch.await(1, TimeUnit.SECONDS);
    }

    @Test
    void testConcurrentTaskExecution() throws Exception {
        threadPool = new ThreadPool(4);
        
        int taskCount = 20;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger counter = new AtomicInteger(0);
        
        // Submit multiple tasks concurrently
        for (int i = 0; i < taskCount; i++) {
            ThreadPool.execute(() -> {
                counter.incrementAndGet();
                // Simulate some work
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                latch.countDown();
            });
        }
        
        // Wait for all tasks to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(taskCount, counter.get());
    }

    @Test
    void testSingletonBehavior() throws Exception {
        ThreadPool pool1 = new ThreadPool(2);
        ThreadPool pool2 = new ThreadPool(4);
        
        // The second constructor should update the singleton
        Field selfField = ThreadPool.class.getDeclaredField("self");
        selfField.setAccessible(true);
        ThreadPool currentInstance = (ThreadPool) selfField.get(null);
        
        assertSame(pool2, currentInstance);
        assertNotSame(pool1, currentInstance);
    }

    @Test
    void testThreadPoolExecutorConfiguration() throws Exception {
        threadPool = new ThreadPool(8);
        
        Field executorField = ThreadPool.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) executorField.get(threadPool);
        
        // Verify ThreadPoolExecutor configuration
        assertEquals(8, executor.getCorePoolSize());
        assertEquals(12, executor.getMaximumPoolSize());
        assertEquals(30, executor.getKeepAliveTime(TimeUnit.SECONDS));
        assertNotNull(executor.getQueue());
        assertTrue(executor.getQueue().remainingCapacity() > 0);
    }

    @Test
    void testQueueCapacityHandling() throws Exception {
        Properties props = new Properties();
        props.setProperty("threadpool.queue_size", "5");
        
        MainHandler customHandler = new MainHandler(props);
        setMainHandlerInstance(customHandler);
        
        threadPool = new ThreadPool(2); // 2 core threads, 3 max threads (2 * 1.5 = 3)
        
        CountDownLatch blockingLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(7);
        
        // Submit multiple blocking tasks to occupy all threads
        for (int i = 0; i < 3; i++) {
            ThreadPool.execute(() -> {
                try {
                    blockingLatch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                completionLatch.countDown();
            });
        }
        
        // Submit 4 more tasks to fill the queue beyond max pool size
        for (int i = 0; i < 4; i++) {
            ThreadPool.execute(() -> {
                completionLatch.countDown();
            });
        }
        
        // Give tasks time to queue up
        Thread.sleep(100);
        
        // isBusy() returns true when queue.size() > maxPoolSize
        // With 3 threads occupied and 4 queued tasks, queue.size() = 4, maxPoolSize = 3
        // So 4 > 3 = true, should be busy
        assertTrue(ThreadPool.isBusy());
        
        // Release the blocking tasks
        blockingLatch.countDown();
        
        // Wait for completion
        assertTrue(completionLatch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void testZeroThreadCount() throws Exception {
        // ThreadPoolExecutor requires at least 1 thread
        assertThrows(IllegalArgumentException.class, () -> {
            new ThreadPool(0);
        });
    }

    @Test
    void testNegativeThreadCount() throws Exception {
        // ThreadPoolExecutor should handle negative values appropriately
        assertThrows(IllegalArgumentException.class, () -> {
            new ThreadPool(-1);
        });
    }
}