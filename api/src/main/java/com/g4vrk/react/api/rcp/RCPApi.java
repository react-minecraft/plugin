package com.g4vrk.react.api.rcp;

/**
 * Version of the contract between the plugin and its RCPs.
 *
 * <p>An RCP names the version it was built for in {@code rcp-metadata.yml} as
 * {@code api-version}. The plugin loads only RCPs with exactly its own version, and raises
 * it whenever a change would break RCPs built before.</p>
 */
public final class RCPApi {

    public static final int VERSION = 1;

    /**
     * Base address of the React backend, overridable with the {@code react.api.url}
     * system property for staging and local runs.
     */
    public static final String API_URL = System.getProperty("react.api.url", "https://api.react-ac.space");

    private RCPApi() {
    }

}
