package com.g4vrk.react.api.extension;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The {@code github} section of an extension metadata file.
 *
 * <p>When {@code updatable} is true the plugin installs new releases of
 * {@code repository} itself. A release must carry a {@code checksums.txt} signed with
 * the React key, so only React's own repositories can be updated.</p>
 *
 * @param repository the repository, {@code owner/name}
 * @param updatable  whether the plugin updates the extension
 */
@ApiStatus.AvailableSince("2.0")
public record GitHubSource(@NotNull String repository, boolean updatable) {

}
