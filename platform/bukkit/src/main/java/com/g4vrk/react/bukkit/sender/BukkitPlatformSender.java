package com.g4vrk.react.bukkit.sender;

import com.g4vrk.react.api.platform.sender.PlatformSender;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Adapts a Bukkit {@link CommandSender} to React's platform-independent {@link PlatformSender}.
 */
public class BukkitPlatformSender implements PlatformSender, ForwardingAudience.Single {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final CommandSender handle;
    private final Audience audience;

    public BukkitPlatformSender(
            final @NotNull CommandSender handle
    ) {

        this.handle = handle;
        this.audience = handle instanceof final Audience nativeAudience
                ? nativeAudience
                : new SpigotAudience(handle);

    }

    /**
     * Wraps a Bukkit {@link CommandSender}, returning a {@link BukkitPlatformPlayer} for players.
     *
     * @param sender the native Bukkit sender
     * @return the wrapped platform sender
     */
    public static @NotNull PlatformSender wrap(
            final @NotNull CommandSender sender
    ) {

        return sender instanceof final Player player
                ? new BukkitPlatformPlayer(player)
                : new BukkitPlatformSender(sender);

    }

    @Override
    public @NotNull String name() {

        return handle.getName();

    }

    @Override
    public boolean hasPermission(
            final @NotNull String permission
    ) {

        return handle.hasPermission(permission);

    }

    @Override
    public @NotNull CommandSender handle() {

        return handle;

    }

    @Override
    public @NotNull Audience audience() {

        return audience;

    }

    @Override
    public boolean equals(
            final Object other
    ) {

        if (this == other) {
            return true;
        }
        if (!(other instanceof final BukkitPlatformSender that)) {
            return false;
        }
        return handle.equals(that.handle);

    }

    @Override
    public int hashCode() {

        return handle.hashCode();

    }

    private static final class SpigotAudience implements Audience {

        private final CommandSender sender;

        private SpigotAudience(final @NotNull CommandSender sender) {

            this.sender = sender;

        }

        @Override
        public void sendMessage(
                final @NotNull Identity source,
                final @NotNull Component message,
                final @NotNull MessageType type
        ) {

            sender.sendMessage(LEGACY_SERIALIZER.serialize(message));

        }

    }

}
