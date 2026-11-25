package com.g4vrk.react.api.rcp.provider;

import com.g4vrk.react.api.rcp.RCPLifecycle;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Convenience base class for RCP implementations holding a reference to their {@link RCPEnvironment}.
 */
@ApiStatus.AvailableSince("2.0")
public abstract class RCPProvider implements RCPLifecycle {

    private final RCPEnvironment singletonEnvironment;

    public RCPProvider(
            final @NotNull RCPEnvironment environment
    ) {

        this.singletonEnvironment = Objects.requireNonNull(
                environment,
                "environment can't be null"
        );

    }

    protected @NotNull RCPEnvironment singletonEnvironment() {

        return singletonEnvironment;

    }

    @Override
    public void onEnable() throws Exception {

        /*
         * Default no-op hook for providers without startup logic.
         */

    }

    @Override
    public void onDisable() throws Exception {

        /*
         * Default no-op hook for providers without shutdown logic.
         */

    }

}
