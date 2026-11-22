package com.g4vrk.react;

import com.g4vrk.react.api.platform.sender.PlatformSender;
import org.jetbrains.annotations.NotNull;

/**
 * Permission names used by React's commands, channels, and checks.
 */
public final class PermissionKey {

    public static final @NotNull String PERMISSION_PREFIX = "react.";

    public static final @NotNull String COMMAND_USAGE = PERMISSION_PREFIX + "command.usage";

    public static final @NotNull String ALERTS = PERMISSION_PREFIX + "alerts";
    public static final @NotNull String ALERTS_ENABLE_ON_JOIN = ALERTS + ".enable_on_join";

    public static final @NotNull String MONITOR = PERMISSION_PREFIX + "monitor";
    public static final @NotNull String MONITOR_ENABLE_ON_JOIN = MONITOR + ".enable_on_join";

    public static final @NotNull String DEBUG = PERMISSION_PREFIX + "debug";

    public static final @NotNull String BYPASS_COMMAND = PERMISSION_PREFIX + "command.bypass";

    public static final @NotNull String NOTIFY_UPDATE = PERMISSION_PREFIX + "notify.update";

    public static final @NotNull String BASE_BYPASS = PERMISSION_PREFIX + "bypass";

    private PermissionKey() {

    }

    public static boolean hasBypassForCheck(
            final @NotNull PlatformSender sender,
            final @NotNull String checkName
    ) {

        return sender.hasPermission(BASE_BYPASS + "." + checkName);

    }

}
