package com.g4vrk.react.api.rcp;

import org.jetbrains.annotations.ApiStatus;

/**
 * Version of the contract between the plugin and its RCPs.
 *
 * <p>An RCP names the version it was built for in {@code rcp-metadata.yml}, an addon in
 * {@code react-addon.yml}, as {@code api-version}. The plugin loads only extensions with
 * exactly its own version, and raises it whenever a change would break extensions built before.</p>
 */
@ApiStatus.AvailableSince("2.0")
public final class RCPAPI {

    /**
     * The {@code api-version} that RCPs and addons must name.
     *
     * <p>Version 2 gave both kinds the shared {@code ExtensionEnvironment}. Version 3 split the
     * registries into their own packages, moved extension commands onto the Cloud command tree
     * ({@code environment.commands()}), replaced the {@code Rotation} record with the flat
     * {@code RotationSample} layout, and turned {@code Punishments} into a {@code Registry}.</p>
     */
    public static final int VERSION = 3;

    /**
     * Base address of the React backend, overridable with the {@code react.api.url}
     * system property for staging and local runs.
     */
    public static final String API_URL = System.getProperty("react.api.url", "https://api.react-ac.space");

    private RCPAPI() {
    }

}
