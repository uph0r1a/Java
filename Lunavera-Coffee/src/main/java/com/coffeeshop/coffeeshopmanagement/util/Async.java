package com.coffeeshop.coffeeshopmanagement.util;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs a blocking operation (a DB call, password hashing, etc.) on a background thread so it
 * never freezes the JavaFX Application Thread, then hands the result (or failure) back on the
 * FX thread automatically - callers never need to think about which thread they're on.
 *
 * This is a thin wrapper around {@link Task} rather than a new concurrency model: JavaFX
 * already guarantees onSucceeded/onFailed run on the FX thread, this just removes the
 * boilerplate of writing that Task every time.
 */
public final class Async {

    private Async() {
    }

    /** One background thread shared by every {@link #runOrdered} call, so those tasks run strictly in the order submitted. */
    private static final ExecutorService ORDERED = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "lunavera-ordered-task");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Like {@link #run(Callable, Consumer, Consumer)}, and the given nodes (a button, a table) stay disabled until the work has
     * ended, success or failure, so a quick second click cannot start the same action again (TODO 2.75). They are switched back
     * on before the callback runs, so the callback may disable what it likes.
     */
    public static <T> void run(Callable<T> backgroundWork, Consumer<T> onSuccess, Consumer<Throwable> onFailure,
                               javafx.scene.Node... busy) {
        Runnable release = Busy.hold(switchesFor(busy));
        Runnable pointer = waitingPointer(busy); // the window's pointer shows the wait too (TODO 5.16)
        run(backgroundWork,
                value -> { release.run(); pointer.run(); onSuccess.accept(value); },
                error -> { release.run(); pointer.run(); onFailure.accept(error); });
    }

    private static Runnable waitingPointer(javafx.scene.Node... nodes) {
        if (nodes != null) {
            for (javafx.scene.Node node : nodes) {
                if (node != null && node.getScene() != null) {
                    return BusyIndicator.waitCursor(node);
                }
            }
        }
        return () -> { };
    }

    private static java.util.List<Busy.Switch> switchesFor(javafx.scene.Node... nodes) {
        java.util.List<Busy.Switch> list = new java.util.ArrayList<>();
        if (nodes != null) {
            for (javafx.scene.Node node : nodes) {
                if (node == null) continue;
                list.add(new Busy.Switch() {
                    @Override public boolean isDisabled() { return node.isDisable(); }
                    @Override public void setDisabled(boolean disabled) { node.setDisable(disabled); }
                });
            }
        }
        return list;
    }

    /** Runs {@code backgroundWork} on its own new thread; tasks may run in any order relative to each other. */
    public static <T> void run(Callable<T> backgroundWork, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = newTask(backgroundWork, onSuccess, onFailure);
        Thread thread = new Thread(task, "lunavera-async-task");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Like {@link #run}, but tasks submitted through this method execute one at a time in the order
     * they were submitted. Use it for writes that must not overtake each other (an autosave of the
     * cart must never land after the payment that follows it).
     */
    public static <T> void runOrdered(Callable<T> backgroundWork, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        ORDERED.execute(newTask(backgroundWork, onSuccess, onFailure));
    }

    /**
     * Waits until every {@link #runOrdered} job submitted so far has finished, but at most {@code maxMillis}: used when the
     * window closes, so the last autosave of a cart is not lost with the daemon thread (TODO 3.24). Returns false on a timeout.
     * Must not be called from a job that runs on that queue.
     */
    public static boolean flushOrdered(long maxMillis) {
        try {
            ORDERED.submit(() -> { }).get(maxMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
            return true;
        } catch (java.util.concurrent.TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.RejectedExecutionException e) {
            return true;
        }
    }

    /** For tests: queue a plain job on the ordered thread (a JavaFX Task needs the toolkit). */
    static void runOrderedRaw(Runnable job) {
        ORDERED.execute(job);
    }

    private static <T> Task<T> newTask(Callable<T> backgroundWork, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return backgroundWork.call();
            }
        };
        task.setOnSucceeded(e -> deliver(onSuccess, task.getValue(), Async::tellScreenNotRefreshed));
        task.setOnFailed(e -> onFailure.accept(task.getException()));
        return task;
    }

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(Async.class.getName());

    /**
     * Hands the result of a finished job to its success callback. The job has already done its work (the order is saved), so a
     * callback that throws (a missing label, a window that fails to open) must not look like a failed save and must not vanish
     * into the global handler: the exception is logged and the person is told once that the screen could not be refreshed
     * (TODO 6.46). {@code tell} is what shows that message; it is a parameter so a test can run without JavaFX.
     */
    static <T> void deliver(Consumer<T> onSuccess, T value, Runnable tell) {
        try {
            onSuccess.accept(value);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "A success callback of a background job failed", e);
            try {
                tell.run();
            } catch (RuntimeException | LinkageError second) {
                LOGGER.log(java.util.logging.Level.WARNING, "The notice about it could not be shown either", second);
            }
        }
    }

    private static void tellScreenNotRefreshed() {
        AlertUtil.warning(I18n.t("async.refresh.title"), I18n.t("async.refresh.message"));
    }
}
