package com.g4vrk.react.player.factory;

import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.react.config.model.MainConfig;
import com.g4vrk.react.player.TrackedPlayerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Creates {@link TrackedPlayerImpl} instances while applying Bedrock filtering rules.
 */
public final class PlayerFactory implements ReloadObserver<MainConfig> {

    private final Predicate<UUID> bedrockDetector;
    private volatile boolean checkBedrockPlayers;

    public PlayerFactory(
            final @NotNull Predicate<UUID> bedrockDetector
    ) {

        this.bedrockDetector = bedrockDetector;
        this.reload();

    }

    public @Nullable TrackedPlayerImpl create(
            final @NotNull PlatformPlayer player
    ) {

        final UUID uniqueId = player.uniqueId();
        if (!checkBedrockPlayers && bedrockDetector.test(uniqueId)) {
            return null;
        }

        return new TrackedPlayerImpl(uniqueId, player.name(), player);

    }

    public void reload() {

        this.onReload(React.INSTANCE.configs().main());

    }

    @Override
    public void onReload(
            final @NotNull MainConfig config
    ) {

        this.checkBedrockPlayers = config.player().checkBedrockPlayers();

    }

}
