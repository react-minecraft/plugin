/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.api.architecture;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces module isolation, public API documentation/annotations, closed enums, and abstraction
 * boundaries for the {@code api} module.
 */
final class ApiArchitectureLockTest {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.*]+);", Pattern.MULTILINE);
    private static final Pattern PUBLIC_TYPE_PATTERN = Pattern.compile(
            "^\\s*public\\s+(?:abstract\\s+|final\\s+|sealed\\s+|non-sealed\\s+|static\\s+)*"
                    + "(class|interface|enum|record|@interface)\\s+(\\w+)",
            Pattern.MULTILINE
    );
    private static final Pattern PLURAL_SIMPLE_NAME = Pattern.compile("^[A-Z][A-Za-z0-9]*(?<!ss|us|is|Permissions)s$");

    private static final Set<String> ALLOWED_PUBLIC_ENUMS = Set.of(
            "com.g4vrk.react.api.event.EventPriority",
            "com.g4vrk.react.api.event.plugin.UpdateDownloadedEvent.Kind"
    );

    private static final List<String> FORBIDDEN_API_IMPORT_PREFIXES = List.of(
            "com.g4vrk.react.check",
            "com.g4vrk.react.config",
            "com.g4vrk.react.history",
            "com.g4vrk.react.player",
            "com.g4vrk.react.punish",
            "com.g4vrk.react.service",
            "com.g4vrk.react.statistic",
            "com.g4vrk.react.storage",
            "com.g4vrk.react.update",
            "org.bukkit",
            "io.papermc",
            "net.minecraft",
            "net.fabricmc",
            "net.minestom",
            "org.spongepowered.api",
            "com.github.retrooper.packetevents",
            "java.sql",
            "javax.sql",
            "com.zaxxer",
            "com.mongodb"
    );

    @Test
    void apiNeverImportsCommonOrPlatformImplementations() throws IOException {

        final List<String> violations = new ArrayList<>();
        for (final Path file : javaFiles(apiMainSourceRoot())) {
            final String content = Files.readString(file);
            final Matcher matcher = IMPORT_PATTERN.matcher(content);
            while (matcher.find()) {
                final String target = matcher.group(1);
                if (target.startsWith("com.g4vrk.react.api.")) {
                    continue;
                }
                for (final String forbidden : FORBIDDEN_API_IMPORT_PREFIXES) {
                    if (target.startsWith(forbidden)) {
                        violations.add(file.getFileName() + " imports forbidden " + target);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "API module leaked internal/platform deps: " + violations);

    }

    @Test
    void apiNeverPullsModelSpecificOrGatewayImplementations() throws IOException {

        final List<String> forbiddenTokens = List.of(
                "ReactInferenceRCP",
                "FlowerModel",
                "VectorModel",
                "inference-gateway",
                "127.0.0.1",
                "localhost:"
        );
        final List<String> violations = new ArrayList<>();
        for (final Path file : javaFiles(apiMainSourceRoot())) {
            final String withoutComments = stripBlockComments(Files.readString(file));
            for (final String token : forbiddenTokens) {
                if (withoutComments.contains(token)) {
                    violations.add(file.getFileName() + " contains " + token);
                }
            }
        }
        assertTrue(violations.isEmpty(), "API must stay abstract and model-agnostic: " + violations);

    }

    @Test
    void publicApiTypesDeclareMultilineJavadocAndApiStatusAndNoPluralNames() throws IOException {

        final List<String> violations = new ArrayList<>();
        for (final Path file : javaFiles(apiMainSourceRoot())) {
            final String content = Files.readString(file);
            final String packageName = packageName(content);
            final Matcher typeMatcher = PUBLIC_TYPE_PATTERN.matcher(content);
            while (typeMatcher.find()) {
                final String kind = typeMatcher.group(1);
                final String name = typeMatcher.group(2);
                if (typeMatcher.start() == content.indexOf(typeMatcher.group())) {
                    if (!content.contains("@ApiStatus.")) {
                        violations.add(file.getFileName() + " is missing @ApiStatus annotation");
                    }
                    if (!hasMultilineJavadocBefore(content, typeMatcher.start())) {
                        violations.add(file.getFileName() + " top-level type " + name + " lacks multiline Javadoc");
                    }
                    if (PLURAL_SIMPLE_NAME.matcher(name).matches()) {
                        violations.add(file.getFileName() + " has plural class name " + name);
                    }
                }
                if ("enum".equals(kind)) {
                    final String fqn = content.indexOf("public enum " + name) < content.indexOf('{')
                            ? packageName + "." + name
                            : packageName + ".UpdateDownloadedEvent." + name;
                    if (!ALLOWED_PUBLIC_ENUMS.contains(packageName + "." + name)
                            && !ALLOWED_PUBLIC_ENUMS.contains(fqn)) {
                        violations.add(file.getFileName() + " declares non-allowlisted API enum " + name);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "API contract violations: " + violations);

    }

    @Test
    void apiFollowsCommentAndImportHygiene() throws IOException {

        final List<String> violations = new ArrayList<>();
        for (final Path file : javaFiles(apiMainSourceRoot())) {
            final List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                final String line = lines.get(i);
                final String trimmed = line.trim();
                if (trimmed.startsWith("import ") && trimmed.endsWith("*;")) {
                    violations.add(file.getFileName() + ":" + (i + 1) + " wildcard import");
                }
                final String noStrings = line.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
                if (noStrings.contains("//")) {
                    violations.add(file.getFileName() + ":" + (i + 1) + " single-line // comment");
                }
                if (!trimmed.startsWith("*") && noStrings.contains("/*") && noStrings.contains("*/")) {
                    violations.add(file.getFileName() + ":" + (i + 1) + " single-line block/Javadoc comment");
                }
            }
        }
        assertTrue(violations.isEmpty(), "API style hygiene violations: " + violations);

    }

    private static boolean hasMultilineJavadocBefore(
            final @NotNull String content,
            final int typeStart
    ) {

        final int closing = content.lastIndexOf("*/", typeStart);
        if (closing < 0) {
            return false;
        }
        final int opening = content.lastIndexOf("/**", closing);
        if (opening < 0) {
            return false;
        }
        return content.substring(opening, closing).contains("\n");

    }

    private static @NotNull String packageName(
            final @NotNull String content
    ) {

        final Matcher matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : "";

    }

    private static @NotNull String stripBlockComments(
            final @NotNull String content
    ) {

        return content.replaceAll("/\\*.*?\\*/", "");

    }

    private static @NotNull Path apiMainSourceRoot() {

        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            final Path candidate = current.resolve("api/src/main/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            if (Files.isDirectory(current.resolve("src/main/java"))
                    && current.getFileName() != null
                    && "api".equals(current.getFileName().toString())) {
                return current.resolve("src/main/java");
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate api/src/main/java from " + Path.of("").toAbsolutePath());

    }

    private static @NotNull List<Path> javaFiles(
            final @NotNull Path root
    ) throws IOException {

        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }

    }

}
