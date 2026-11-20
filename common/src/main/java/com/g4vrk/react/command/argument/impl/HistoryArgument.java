package com.g4vrk.react.command.argument.impl;

import com.g4vrk.react.command.argument.LocalArgument;
import com.g4vrk.react.command.builder.CommandBuilderFactory;
import com.g4vrk.react.history.printer.InferenceHistoryPrinter;
import com.g4vrk.react.player.TrackedPlayerImpl;
import com.g4vrk.react.player.registry.PlayerRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.SinglePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.SinglePlayerSelectorParser;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

/**
 * Shows a page of the selected player's analysis history.
 */
public final class HistoryArgument extends LocalArgument {

    private static final CloudKey<SinglePlayerSelector> PLAYER = CloudKey.of("player", SinglePlayerSelector.class);
    private static final CloudKey<Integer> PAGE = CloudKey.of("page", Integer.class);

    private final CommandBuilderFactory builderFactory;

    private final InferenceHistoryPrinter printer;
    private final PlayerRegistry playerRegistry;

    public HistoryArgument(
            final @NotNull CommandBuilderFactory builderFactory,
            final @NotNull InferenceHistoryPrinter printer,
            final @NotNull PlayerRegistry playerRegistry
    ) {

        this.builderFactory = builderFactory;
        this.printer = printer;
        this.playerRegistry = playerRegistry;

    }

    @Override
    public @NotNull Command.Builder<CommandSender> build() {

        return builderFactory.create()
                .literal("history")
                .permission("react.command.history")
                .optional(PLAYER, SinglePlayerSelectorParser.singlePlayerSelectorParser())
                .optional(PAGE, IntegerParser.integerParser())
                .handler(context -> {
                    final CommandSender sender = context.sender();

                    final SinglePlayerSelector selector = context.getOrDefault(PLAYER, null);
                    final int page = context.getOrDefault(PAGE, 1);

                    final TrackedPlayerImpl reactPlayer;

                    if (selector == null) {

                        if (!(sender instanceof Player player)) {
                            return;
                        }

                        reactPlayer = this.playerRegistry.get(player.getUniqueId());

                    } else {

                        final Player target = selector.single();
                        reactPlayer = this.playerRegistry.get(target.getUniqueId());

                    }

                    if (reactPlayer != null) {

                        this.printer.print(sender, reactPlayer, page);

                    }
                });

    }
}
