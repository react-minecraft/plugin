package com.g4vrk.react.check.manager;

import com.g4vrk.react.config.ReactConfigs;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.check.Check;
import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.player.ReactPlayer;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// realization like in GrimAC
public final class CheckManager {

    private final ClassToInstanceMap<Check> checks;

    private final List<Check> checksValues;

    public CheckManager(
            @NotNull ReactPlayer player
    ) {

        this.checks = new ImmutableClassToInstanceMap.Builder<Check>()
                .put(AimAI.class, new AimAI(player))
                .build();

        this.checksValues = new ObjectArrayList<>(this.checks.values());

        this.reload();

    }

    public <T extends Check> @Nullable T getCheck(
            final @NotNull Class<T> checkClass
    ) {
        return this.checks.getInstance(checkClass);
    }

    public @NotNull List<Check> checks() {
        return this.checksValues;
    }

    public @Nullable Check getCheck(final @NotNull String configId) {
        for (final Check check : checksValues) {
            if (check.getConfigId().equalsIgnoreCase(configId)) {
                return check;
            }
        }
        return null;
    }

    public void reload() {
        final ReactConfigs configs = React.INSTANCE.configs();
        for (final Check check : checksValues) {
            if (check instanceof final ReloadObserver<?> observer) {
                reload(observer, configs.check(check.getConfigId()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <C> void reload(final @NotNull ReloadObserver<C> observer, final @NotNull Object config) {
        observer.onReload((C) config);
    }

}
