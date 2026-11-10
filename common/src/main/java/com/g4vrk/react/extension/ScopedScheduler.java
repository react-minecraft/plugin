package com.g4vrk.react.extension;

import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class ScopedScheduler implements Scheduler, AutoCloseable {

    private final Scheduler delegate;
    private final Set<ScopedTask> tasks = new HashSet<>();
    private boolean closed;

    public ScopedScheduler(final @NotNull Scheduler delegate) {
        this.delegate = delegate;
    }

    @Override
    public @NotNull Task schedule(
            final @NotNull Runnable runnable,
            final @NotNull TickSchedule tickSchedule
    ) {
        return submit(task -> runnable.run(), tickSchedule, delegate::schedule);
    }

    @Override
    public @NotNull Task schedule(
            final @NotNull Consumer<Task> consumer,
            final @NotNull TickSchedule tickSchedule
    ) {
        return submit(consumer, tickSchedule, delegate::schedule);
    }

    @Override
    public @NotNull Task scheduleAsync(
            final @NotNull Runnable runnable,
            final @NotNull TickSchedule tickSchedule
    ) {
        return submit(task -> runnable.run(), tickSchedule, delegate::scheduleAsync);
    }

    @Override
    public @NotNull Task scheduleAsync(
            final @NotNull Consumer<Task> consumer,
            final @NotNull TickSchedule tickSchedule
    ) {
        return submit(consumer, tickSchedule, delegate::scheduleAsync);
    }

    @Override
    public @NotNull Task scheduleEntity(
            final @NotNull Entity entity,
            final @NotNull Runnable runnable,
            final @NotNull TickSchedule tickSchedule
    ) {
        return scheduleEntity(entity, task -> runnable.run(), tickSchedule);
    }

    @Override
    public @NotNull Task scheduleEntity(
            final @NotNull Entity entity,
            final @NotNull Consumer<Task> consumer,
            final @NotNull TickSchedule tickSchedule
    ) {
        return submit(consumer, tickSchedule, (task, schedule) -> delegate.scheduleEntity(entity, task, schedule));
    }

    @Override
    public @NotNull Task scheduleLocation(
            final @NotNull Location location,
            final @NotNull Runnable runnable,
            final @NotNull TickSchedule tickSchedule
    ) {
        return scheduleLocation(location, task -> runnable.run(), tickSchedule);
    }

    @Override
    public @NotNull Task scheduleLocation(
            final @NotNull Location location,
            final @NotNull Consumer<Task> consumer,
            final @NotNull TickSchedule tickSchedule
    ) {
        return submit(consumer, tickSchedule, (task, schedule) -> delegate.scheduleLocation(location, task, schedule));
    }

    private @NotNull Task submit(
            final @NotNull Consumer<Task> action,
            final @NotNull TickSchedule schedule,
            final @NotNull TaskScheduler scheduler
    ) {
        final ScopedTask task = new ScopedTask(action, schedule.isRepeating());
        synchronized (tasks) {
            if (closed) {
                throw new IllegalStateException("The extension scheduler is closed");
            }
            tasks.add(task);
        }
        try {
            task.attach(scheduler.schedule(task, schedule));
            return task;
        } catch (final RuntimeException | Error failure) {
            task.cancel();
            throw failure;
        }
    }

    @Override
    public void close() {
        final List<ScopedTask> running;
        synchronized (tasks) {
            closed = true;
            running = List.copyOf(tasks);
            tasks.clear();
        }
        running.forEach(ScopedTask::cancel);
    }

    @FunctionalInterface
    private interface TaskScheduler {

        @NotNull Task schedule(
                @NotNull Consumer<Task> action,
                @NotNull TickSchedule schedule
        );

    }

    private final class ScopedTask implements Task, Consumer<Task> {

        private final Consumer<Task> action;
        private final boolean repeating;
        private volatile Task task;
        private volatile boolean cancelled;

        private ScopedTask(
                final @NotNull Consumer<Task> action,
                final boolean repeating
        ) {
            this.action = action;
            this.repeating = repeating;
        }

        private void attach(final @NotNull Task task) {
            this.task = task;
            if (cancelled) {
                task.cancel();
            } else if (task.isCancelled()) {
                release();
            }
        }

        @Override
        public void accept(final @NotNull Task task) {
            attach(task);
            if (isCancelled()) {
                return;
            }
            try {
                action.accept(this);
            } catch (final RuntimeException | Error failure) {
                cancel();
                throw failure;
            } finally {
                if (!repeating) {
                    release();
                }
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            final Task task = this.task;
            try {
                if (task != null) {
                    task.cancel();
                }
            } finally {
                release();
            }
        }

        @Override
        public boolean isCancelled() {
            final Task task = this.task;
            return cancelled || task != null && task.isCancelled();
        }

        @Override
        public boolean sync() {
            final Task task = this.task;
            return task != null && task.sync();
        }

        private void release() {
            synchronized (tasks) {
                tasks.remove(this);
            }
        }

    }

}
