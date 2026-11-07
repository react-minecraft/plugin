package com.g4vrk.react.api.rcp.provider;

import com.g4vrk.react.api.rcp.RCPLifecycle;
import com.g4vrk.react.api.rcp.environment.RCPEnvironment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

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
        // pass by default
    }

    @Override
    public void onDisable() throws Exception {
        // pass by default
    }
}
