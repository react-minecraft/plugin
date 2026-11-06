package com.g4vrk.react.api.channel.print.impl;

import com.g4vrk.react.React;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

public class VerbosePrinter extends AlertPrinter {

    public VerbosePrinter(
            final @NotNull Consumer<Component> consumer,
            final @NotNull Function<String, Component> serializer
    ) {

        super(consumer, serializer);

    }

    @Override
    protected @NotNull String rawFormat() {
        return React.INSTANCE.getConfigs().main().verbose().format();
    }

}
