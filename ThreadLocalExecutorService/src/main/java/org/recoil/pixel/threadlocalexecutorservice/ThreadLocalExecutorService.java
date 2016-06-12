/*
 * Copyright © 2016 Andrew Thompson
 */
package org.recoil.pixel.threadlocalexecutorservice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static java.util.stream.Collectors.toList;

/**
 *  An {@code ExecutorService} which delegates to another {@code ExecutorService} while copying a defined set of
 *  thread locals from the threads that submit tasks to the threads within the service.
 * 
 *  For the given {@code ThreadLocal}s the values are copied from the threads which submit tasks to the threads which execute
 *  them in the delegated to {@code ExecutorService}. Previous values of the {@code ThreadLocal}s are restored when each task 
 *  completes. The net effect is the submitted tasks run with the {@code ThreadLocal} values of the {@code Thread} that submitted
 *  them.
 * 
 */
public class ThreadLocalExecutorService implements ExecutorService {

    /**
     * The service being delegated to
     */
    private final ExecutorService delegate;
    /**
     * The collection of thread locals whose values are copied from the task submitting threads to the 
     * worker threads in the delegate
     */
    private final Collection<ThreadLocal<?>> inheritedLocals = new ArrayList<>();

    /**
     * Construct a new {@code ExecutorService} which delegates to the given {@code ExecutorService} and copies the given locals
     * @param delegate the {@code ExecutorService} tasks are delegated to
     * @param inheritedLocals the {@code ThreadLocal}s whose values are copied from the submitting threads to the delegate's threads
     */
    public ThreadLocalExecutorService(final ExecutorService delegate, final ThreadLocal<?>... inheritedLocals) {
        this.delegate = delegate;
        this.inheritedLocals.addAll(Arrays.asList(inheritedLocals));
    }
    
    /**
     * Construct a new {@code ExecutorService} which delegates to the given {@code ExecutorService} and copies the given locals
     * @param delegate the {@code ExecutorService} tasks are delegated to
     * @param inheritedLocals the {@code ThreadLocal}s whose values are copied from the submitting threads to the delegate's threads
     */
    public ThreadLocalExecutorService(final ExecutorService delegate, final Collection<ThreadLocal<?>> inheritedLocals) {
        this(delegate, inheritedLocals.toArray(new ThreadLocal<?>[inheritedLocals.size()]));
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(taskWithSavedValues(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(taskWithSavedValues(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(taskWithSavedValues(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(tasks.stream().map(this::taskWithSavedValues).collect(toList()));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.invokeAll(tasks.stream().map(this::taskWithSavedValues).collect(toList()), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks.stream().map(this::taskWithSavedValues).collect(toList()));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks.stream().map(this::taskWithSavedValues).collect(toList()), timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(taskWithSavedValues(command));
    }

    /**
     * A class that can save and restore the value of a {@code ThreadLocal} variable, while temporarily replacing it with a value
     * from another thread.
     * @param <T> the type held by the associated {@code ThreadLocal}
     */
    private static class ThreadLocalMemento<T> {
        /** The associated variable*/
        final ThreadLocal<T> variable;
        /** The value to assign to the variable temporarily*/
        final T replacementValue;
        /** The original value of the variable, to restore*/
        T savedValue;

        /** 
         * Construct a new memento for a {@code ThreadLocal}, preparing to replace it's value with the value from the constructing
         * thread.
         * @param variable the variable whose value will be saved, replaced and restored
         */
        private ThreadLocalMemento(final ThreadLocal<T> variable) {
            this.variable = variable;
            replacementValue = variable.get();
        }

        /** 
         * Replace the value of the {@code ThreadLocal} with the value captured in the constructor.
         * The value from the current thread is saved for later restoration.
         * 
         */
        void apply() {
            savedValue = variable.get();
            variable.set(replacementValue);
        }

        /**
         * Restore the {@code ThreadLocal} to the value it had before the new value was applied
         */
        void restore() {
            variable.set(savedValue);
        }
    }

    /** 
     * Create a wrapper for the given task, which saves and restores all the {@code ThreadLocal}s specified by this
     * {@code ExecutorService}
     * @param task the task to execute with the values of the {@code ThreadLocal}s from the thread which submitted it
     * @return a wrapper which executes the original task with the appropriate {@code ThreadLocal} values
     */
    private Runnable taskWithSavedValues(Runnable task) {
        List<ThreadLocalMemento<?>> savedValues = inheritedLocals.stream().map(ThreadLocalMemento::new).collect(toList());
        return () -> {
            try {
                savedValues.forEach(ThreadLocalMemento::apply);
                task.run();
            } finally {
                savedValues.forEach(ThreadLocalMemento::restore);
            }
        };
    }
   
    /** 
     * Create a wrapper for the given task, which saves and restores all the {@code ThreadLocal}s specified by this
     * {@code ExecutorService}
     * @param task the task to execute with the values of the {@code ThreadLocal}s from the thread which submitted it
     * @param <T> the return type of the task's result
     * @return a wrapper which executes the original task with the appropriate {@code ThreadLocal} values
     */
    private <T> Callable<T> taskWithSavedValues(Callable<T> task) {
        List<ThreadLocalMemento<?>> savedValues = inheritedLocals.stream().map(ThreadLocalMemento::new).collect(toList());
        return () -> {
            try {
                savedValues.forEach(ThreadLocalMemento::apply);
                return task.call();
            } finally {
                savedValues.forEach(ThreadLocalMemento::restore);
            }
        };
    }
}
