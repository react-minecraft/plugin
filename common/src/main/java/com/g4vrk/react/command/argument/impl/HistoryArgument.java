package com.g4vrk.react.command.argument.impl;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.history.printer.InferenceHistoryPrinter;
import com.g4vrk.react.platform.command.PlatformCommandBridge;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.registry.PlayerRegistry;
import org.incendo.cloud.Command;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Shows a page of the selected player's analysis history.
 */
public final class HistoryArgument extends LocalArgument {

    private static final CloudKey<PlatformPlayer> PLAYER = CloudKey.of("player", PlatformPlayer.class);
    private static final CloudKey<Integer> PAGE = CloudKey.of("page", Integer.class);

    private final CommandBuilderFactory builderFactory;
    private final PlatformCommandBridge platformCommands;
    private final InferenceHistoryPrinter printer;
    private final PlayerRegistry playerRegistry;
    private final Consumer<PlatformSender> playerOnly;

    public HistoryArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull PlatformCommandBridge platformCommands,
            final @NotNull InferenceHistoryPrinter printer,
            final @NotNull PlayerRegistry playerRegistry,
            final @NotNull Consumer<PlatformSender> playerOnly
    ) {

        this.builderFactory = builderFactory;
        this.platformCommands = platformCommands;
        this.printer = printer;
        this.playerRegistry = playerRegistry;
        this.playerOnly = playerOnly;

    }

    @Override
    public @NotNull Command.Builder<PlatformSender> build() {

        return builderFactory.create()
                .literal("history")
                .permission("react.command.history")
                .argument(platformCommands.singlePlayerArgument(PLAYER, false))
                .optional(PAGE, IntegerParser.integerParser())
                .handler(context -> {
                    final PlatformSender sender = context.sender();

                    final PlatformPlayer target = context.getOrDefault(PLAYER, null);
                    final int page = context.getOrDefault(PAGE, 1);

                    final TrackedPlayerImpl reactPlayer;

                    if (target == null) {

                        final PlatformPlayer self = sender.asPlayer();
                        if (self == null) {
                            this.playerOnly.accept(sender);
                            return;
                        }

                        reactPlayer = this.playerRegistry.get(self.uniqueId());

                    } else {

                        reactPlayer = this.playerRegistry.get(target.uniqueId());

                    }

                    if (reactPlayer != null) {

                        this.printer.print(sender, reactPlayer, page);

                    }
                });

    }

}
