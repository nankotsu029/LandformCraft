package com.github.nankotsu029.landformcraft.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationExecutorsTest {
    @Test
    void separatesVirtualIoThreadsFromBoundedGenerationThreads() throws Exception {
        GenerationExecutors executors = GenerationExecutors.createDefault(1);
        try {
            String ioThread = executors.supplyIo(() -> Thread.currentThread().toString())
                    .get(2, TimeUnit.SECONDS);
            String generationThread = executors.supplyGeneration(token -> Thread.currentThread().getName())
                    .get(2, TimeUnit.SECONDS);

            assertTrue(ioThread.contains("VirtualThread"));
            assertTrue(generationThread.startsWith("landformcraft-generation-"));
        } finally {
            assertTrue(executors.shutdown(Duration.ofSeconds(2)));
            assertTrue(executors.isTerminated());
        }
    }

    @Test
    void rejectsGenerationWorkWhenTheBoundedQueueIsFull() throws Exception {
        GenerationExecutors executors = GenerationExecutors.create(1, 1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var running = executors.supplyGeneration(token -> await(started, release, 1));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var queued = executors.supplyGeneration(token -> 2);
            var rejected = executors.supplyGeneration(token -> 3);

            CompletionException failure = assertThrows(CompletionException.class, rejected::join);
            assertInstanceOf(RejectedExecutionException.class, failure.getCause());

            release.countDown();
            assertEquals(1, running.get(2, TimeUnit.SECONDS));
            assertEquals(2, queued.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            assertTrue(executors.shutdown(Duration.ofSeconds(2)));
        }
    }

    @Test
    void cancellingFutureInterruptsItsGenerationTask() throws Exception {
        GenerationExecutors executors = GenerationExecutors.create(1, 1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        try {
            var future = executors.supplyGeneration(token -> {
                started.countDown();
                try {
                    while (true) {
                        token.throwIfCancellationRequested();
                        if (new CountDownLatch(1).await(20, TimeUnit.MILLISECONDS)) {
                            break;
                        }
                    }
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.CancellationException exception) {
                    interrupted.countDown();
                }
                return 1;
            });

            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTrue(future.cancel(true));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        } finally {
            assertTrue(executors.shutdown(Duration.ofSeconds(2)));
        }
    }

    @Test
    void limitsConcurrentVirtualThreadIoTasks() throws Exception {
        GenerationExecutors executors = GenerationExecutors.create(1, 1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var running = executors.supplyIo(() -> await(started, release, 1));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var rejected = executors.supplyIo(() -> 2);

            CompletionException failure = assertThrows(CompletionException.class, rejected::join);
            assertInstanceOf(RejectedExecutionException.class, failure.getCause());

            release.countDown();
            assertEquals(1, running.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            assertTrue(executors.shutdown(Duration.ofSeconds(2)));
        }
    }

    @Test
    void keepsIoAdmissionUntilACancelledDelegateActuallyExits() throws Exception {
        GenerationExecutors executors = GenerationExecutors.create(1, 1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch allowExit = new CountDownLatch(1);
        try {
            var running = executors.supplyIo(() -> {
                started.countDown();
                boolean finished = false;
                while (!finished) {
                    try {
                        allowExit.await();
                        finished = true;
                    } catch (InterruptedException ignored) {
                        // Deliberately simulate a non-cooperative transport to verify admission remains held.
                    }
                }
                return 1;
            });

            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTrue(running.cancel(true));
            var rejected = executors.supplyIo(() -> 2);
            CompletionException failure = assertThrows(CompletionException.class, rejected::join);
            assertInstanceOf(RejectedExecutionException.class, failure.getCause());
        } finally {
            allowExit.countDown();
            assertTrue(executors.shutdown(Duration.ofSeconds(2)));
        }
    }

    @Test
    void concurrentSubmissionAndShutdownNeverLeavesAnIncompleteFuture() throws Exception {
        for (int iteration = 0; iteration < 50; iteration++) {
            GenerationExecutors executors = GenerationExecutors.create(1, 1, 1);
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<java.util.concurrent.CompletableFuture<Integer>> submitted = new AtomicReference<>();
            Thread submitter = Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                submitted.set(executors.supplyGeneration(token -> 1));
            });

            start.countDown();
            assertTrue(executors.shutdown(Duration.ofSeconds(2)));
            submitter.join(2_000L);

            assertTrue(!submitter.isAlive());
            assertNotNull(submitted.get());
            assertTrue(submitted.get().isDone());
        }
    }

    private static int await(CountDownLatch started, CountDownLatch release, int result) {
        started.countDown();
        try {
            release.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test task interrupted", exception);
        }
        return result;
    }
}
