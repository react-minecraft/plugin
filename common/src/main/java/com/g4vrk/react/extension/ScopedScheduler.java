package com.g4vrk.react.extension;

import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * The scheduler of one extension: {@link #close()} cancels every task it started.
 *
 * <p>Tasks are held weakly, so finished tasks do not pile up.</p>
 */
public final class ScopedScheduler implements Scheduler, AutoCloseable {

    private final Scheduler delegate;
    private final Set<Task> tasks = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public ScopedScheduler(final @NotNull Scheduler delegate) {
        this.delegate = delegate;
    }

    private @NotNull Task track(final @NotNull Task task) {
        tasks.add(task);
        return task;
    }

    @Override
    public @NotNull Task schedule(final @NotNull Runnable runnable, final @NotNull TickSchedule tickSchedule) {
        return track(delegate.schedule(runnable, tickSchedule));
    }

    @Override
    public @NotNull Task schedule(final @NotNull Consumer<Task> consumer, final @NotNull TickSchedule tickSchedule) {
        return track(delegate.schedule(consumer, tickSchedule));
    }

    @Override
    public @NotNull Task scheduleAsync(final @NotNull Runnable runnable, final @NotNull TickSchedule tickSchedule) {
        return track(delegate.scheduleAsync(runnable, tickSchedule));
    }

    @Override
    public @NotNull Task scheduleAsync(final @NotNull Consumer<Task> consumer, final @NotNull TickSchedule tickSchedule) {
        return track(delegate.scheduleAsync(consumer, tickSchedule));
    }

    @Override
    public @NotNull Task scheduleEntity(
            final @NotNull Entity entity,
            final @NotNull Runnable runnable,
            final @NotNull TickSchedule tickSchedule
    ) {
        return track(delegate.scheduleEntity(entity, runnable, tickSchedule));
    }

    @Override
    public @NotNull Task scheduleEntity(
            final @NotNull Entity entity,
            final @NotNull Consumer<Task> consumer,
            final @NotNull TickSchedule tickSchedule
    ) {
        return track(delegate.scheduleEntity(entity, consumer, tickSchedule));
    }

    @Override
    public @NotNull Task scheduleLocation(
            final @NotNull Location location,
            final @NotNull Runnable runnable,
            final @NotNull TickSchedule tickSchedule
    ) {
        return track(delegate.scheduleLocation(location, runnable, tickSchedule));
    }

    @Override
    public @NotNull Task scheduleLocation(
            final @NotNull Location location,
            final @NotNull Consumer<Task> consumer,
            final @NotNull TickSchedule tickSchedule
    ) {
        return track(delegate.scheduleLocation(location, consumer, tickSchedule));
    }

    @Override
    public void close() {
        final List<Task> running;
        synchronized (tasks) {
            running = List.copyOf(tasks);
            tasks.clear();
        }
        running.forEach(Task::cancel);
    }

}
