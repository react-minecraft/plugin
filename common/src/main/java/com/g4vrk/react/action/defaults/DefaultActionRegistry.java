package com.g4vrk.react.action.defaults;

import com.g4vrk.functionalActions.Action;
import com.g4vrk.functionalActions.registry.ActionRegistry;
import com.g4vrk.react.action.PlaySoundAction;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.platform.ReactPlatform;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Registers React's platform-independent built-in actions.
 */
public final class DefaultActionRegistry {

    private DefaultActionRegistry() {

    }

    /**
     * Registers the default actions without linking shared runtime code to Bukkit classes.
     *
     * @param registry the action registry
     * @param platform the active server platform
     * @param scheduler the platform scheduler
     * @param logger the runtime logger
     * @param textMapper converts configured text to Adventure components
     * @param splitter the configured title and sound separator
     */
    public static void register(
            final @NotNull ActionRegistry<Audience> registry,
            final @NotNull ReactPlatform platform,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull Logger logger,
            final @NotNull Function<String, Component> textMapper,
            final @NotNull String splitter
    ) {

        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(textMapper, "textMapper");
        Objects.requireNonNull(splitter, "splitter");

        registry.register((audience, arguments) -> {
            final Component message = arguments == null || arguments.isBlank()
                    ? Component.empty()
                    : textMapper.apply(arguments);
            executeAudience(audience, scheduler, () -> audience.sendMessage(message));
        }, "message", "msg");

        registry.register((audience, arguments) -> {
            if (arguments == null || arguments.isBlank()) {
                return;
            }
            final Component message = textMapper.apply(arguments);
            executeAudience(audience, scheduler, () -> audience.sendActionBar(message));
        }, "actionbar", "bar");

        registry.register((audience, arguments) -> {
            if (arguments == null || arguments.isBlank()) {
                return;
            }
            final String[] parts = arguments.split(splitter);
            final Component title = textMapper.apply(parts[0]);
            final Component subtitle = parts.length > 1 ? textMapper.apply(parts[1]) : Component.empty();
            executeAudience(audience, scheduler, () -> audience.showTitle(Title.title(title, subtitle)));
        }, "title", "show-title");

        final Action<Audience> sound = new PlaySoundAction(
                logger,
                platform::resolveSoundKey
        );
        registry.register(
                (audience, arguments) -> executeAudience(audience, scheduler, () -> sound.execute(audience, arguments)),
                "sound",
                "play-sound"
        );

        registry.register((audience, arguments) -> {
            final Component message = arguments == null || arguments.isBlank()
                    ? Component.empty()
                    : textMapper.apply(arguments);
            broadcast(platform, scheduler, player -> player.sendMessage(message));
        }, "broadcast", "bc");

        registry.register((audience, arguments) -> {
            final Component message = arguments == null || arguments.isBlank()
                    ? Component.empty()
                    : textMapper.apply(arguments);
            broadcast(platform, scheduler, player -> player.sendActionBar(message));
        }, "broadcast-actionbar", "broadcast-bar", "bcbar");

        registry.register((audience, arguments) -> {
            if (arguments == null || arguments.isBlank()) {
                return;
            }
            final String[] parts = arguments.split(splitter);
            final Component title = textMapper.apply(parts[0]);
            final Component subtitle = parts.length > 1 ? textMapper.apply(parts[1]) : Component.empty();
            final Title configuredTitle = Title.title(title, subtitle);
            broadcast(platform, scheduler, player -> player.showTitle(configuredTitle));
        }, "broadcast-title", "bctitle");

        registry.register((audience, arguments) -> {
            if (arguments == null || arguments.isBlank()) {
                return;
            }
            scheduler.schedule(() -> platform.dispatchConsoleCommand(arguments), TickSchedule.instant());
        }, "console-command", "console", "cmd");

    }

    private static void executeAudience(
            final @NotNull Audience audience,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull Runnable action
    ) {

        if (audience instanceof final PlatformPlayer player) {
            if (player.online()) {
                scheduler.scheduleEntity(player, action, TickSchedule.instant());
            }
            return;
        }
        action.run();

    }

    private static void broadcast(
            final @NotNull ReactPlatform platform,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull Consumer<PlatformPlayer> action
    ) {

        scheduler.schedule(() -> {
            for (final PlatformPlayer player : platform.onlinePlayers()) {
                if (player.online()) {
                    scheduler.scheduleEntity(player, () -> action.accept(player), TickSchedule.instant());
                }
            }
        }, TickSchedule.instant());

    }

}
