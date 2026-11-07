package com.g4vrk.react.extension;

import com.g4vrk.react.api.extension.ArgumentRegistry;
import com.g4vrk.react.api.rcp.argument.RCPArgument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Every {@code /react} subcommand that addons and RCPs added, by name.
 */
public final class ArgumentTable {

    private static final Set<String> RESERVED = Set.of(
            "alerts", "monitor", "verbose", "reload", "history", "reboot", "rcps", "help", "bypass", "debug", "update"
    );

    private final Map<String, RegisteredArgument> arguments = new ConcurrentHashMap<>();

    /**
     * The subcommand with a name.
     *
     * @param name the name, any case
     * @return the subcommand, or {@code null}
     */
    public @Nullable RegisteredArgument get(final @NotNull String name) {
        return arguments.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Every subcommand.
     *
     * @return a copy of the table
     */
    public @NotNull Collection<RegisteredArgument> all() {
        return List.copyOf(arguments.values());
    }

    /**
     * A registry that adds subcommands for one extension and can remove all of them.
     *
     * @param owner the extension name
     * @return the scoped registry
     */
    public @NotNull Scope scope(final @NotNull String owner) {
        return new Scope(owner);
    }

    /**
     * The subcommands of one extension.
     */
    public final class Scope implements ArgumentRegistry, AutoCloseable {

        private final String owner;

        private Scope(final @NotNull String owner) {
            this.owner = owner;
        }

        @Override
        public void register(final @NotNull String name, final @NotNull RCPArgument argument) {
            Objects.requireNonNull(argument, "argument");
            final String key = name.toLowerCase(Locale.ROOT);
            if (key.isBlank() || key.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("Invalid subcommand name '" + name + "'");
            }
            if (RESERVED.contains(key)) {
                throw new IllegalArgumentException("/react " + key + " belongs to the plugin");
            }
            if (arguments.putIfAbsent(key, new RegisteredArgument(key, owner, argument)) != null) {
                throw new IllegalArgumentException("/react " + key + " is already registered");
            }
        }

        @Override
        public boolean unregister(final @NotNull String name) {
            final RegisteredArgument argument = get(name);
            return argument != null && argument.owner().equals(owner) && arguments.remove(argument.name(), argument);
        }

        @Override
        public @NotNull Set<String> keys() {
            return arguments.values().stream()
                    .filter(argument -> argument.owner().equals(owner))
                    .map(RegisteredArgument::name)
                    .collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public void close() {
            arguments.values().removeIf(argument -> argument.owner().equals(owner));
        }

    }

}
