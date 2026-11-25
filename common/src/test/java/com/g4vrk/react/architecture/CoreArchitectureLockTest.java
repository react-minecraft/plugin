/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.architecture;

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
 * Guards module isolation, abstract plugin boundaries, privacy rules, god-class budgets, and
 * strict Java conventions across the entire multi-module repository.
 */
final class CoreArchitectureLockTest {

    private static final int MAX_MAIN_SOURCE_LINES = 615;
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.*]+);", Pattern.MULTILINE);
    private static final Pattern TOP_TYPE_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+)?(?:abstract\\s+|final\\s+|sealed\\s+|non-sealed\\s+)*"
                    + "(class|interface|enum|record|@interface)\\s+(\\w+)",
            Pattern.MULTILINE
    );
    private static final Pattern PLURAL_SIMPLE_NAME = Pattern.compile("^[A-Z][A-Za-z0-9]*(?<!ss|us|is|Permissions)s$");

    private static final List<String> PLATFORM_IMPORTS = List.of(
            "org.bukkit",
            "io.papermc",
            "com.destroystokyo.paper",
            "net.minecraft",
            "net.fabricmc",
            "net.minestom",
            "org.spongepowered.api",
            "com.github.retrooper.packetevents"
    );

    private static final Set<String> HOT_PATH_PACKAGES = Set.of(
            "com.g4vrk.react.check",
            "com.g4vrk.react.check.impl.aim",
            "com.g4vrk.react.check.processor.rotation",
            "com.g4vrk.react.history",
            "com.g4vrk.react.history.entry",
            "com.g4vrk.react.placeholder",
            "com.g4vrk.react.placeholder.registry",
            "com.g4vrk.react.placeholder.text",
            "com.g4vrk.react.player",
            "com.g4vrk.react.player.rotation",
            "com.g4vrk.react.statistic",
            "com.g4vrk.react.event"
    );

    private static final Set<String> FQN_COLLISION_ALLOWED_FILES = Set.of(
            "MinestomCommandBridge.java",
            "SpongeCommandBridge.java",
            "MiniTextFormatter.java",
            "Check.java",
            "ListenerRegistry.java",
            "PlatformServer.java"
    );

    @Test
    void commonNeverImportsPlatformOrDriverSpecificApisOutsideBackends() throws IOException {

        final Path commonRoot = repoRoot().resolve("common/src/main/java");
        final List<String> violations = new ArrayList<>();
        for (final Path file : javaFiles(commonRoot)) {
            final String content = Files.readString(file);
            final String pkg = packageName(content);
            final Matcher matcher = IMPORT_PATTERN.matcher(content);
            while (matcher.find()) {
                final String imp = matcher.group(1);
                for (final String platform : PLATFORM_IMPORTS) {
                    if (imp.startsWith(platform)) {
                        violations.add(pkg + "/" + file.getFileName() + " imports platform type " + imp);
                    }
                }
                if ((imp.startsWith("java.sql.") || imp.startsWith("javax.sql.") || imp.startsWith("com.zaxxer."))
                        && !pkg.startsWith("com.g4vrk.react.storage.backend.jdbc")) {
                    violations.add(pkg + "/" + file.getFileName() + " leaks JDBC type " + imp);
                }
                if ((imp.startsWith("com.mongodb.") || imp.startsWith("org.bson."))
                        && !pkg.startsWith("com.g4vrk.react.storage.backend.mongo")) {
                    violations.add(pkg + "/" + file.getFileName() + " leaks MongoDB type " + imp);
                }
                if (imp.startsWith("java.net.http.")
                        && !pkg.startsWith("com.g4vrk.react.update.client")
                        && !pkg.startsWith("com.g4vrk.react.extension.library")) {
                    violations.add(pkg + "/" + file.getFileName() + " leaks HttpClient outside update.client and extension.library");
                }
            }
        }
        assertTrue(violations.isEmpty(), "Common module architecture boundary broken: " + violations);

    }

    @Test
    void platformModulesStayIsolatedFromSiblingPlatforms() throws IOException {

        final Path repo = repoRoot();
        final List<String> violations = new ArrayList<>();
        checkPlatformIsolation(repo.resolve("platform/bukkit/src/main/java"), List.of(
                "net.fabricmc", "net.minestom", "org.spongepowered.api", "net.minecraft", "com.g4vrk.react.fabric", "com.g4vrk.react.minestom", "com.g4vrk.react.sponge"
        ), violations);
        checkPlatformIsolation(repo.resolve("platform/fabric/src/main/java"), List.of(
                "org.bukkit", "io.papermc", "net.minestom", "org.spongepowered.api", "com.g4vrk.react.bukkit", "com.g4vrk.react.minestom", "com.g4vrk.react.sponge"
        ), violations);
        checkPlatformIsolation(repo.resolve("platform/minestom/src/main/java"), List.of(
                "org.bukkit", "io.papermc", "net.fabricmc", "org.spongepowered.api", "net.minecraft", "com.g4vrk.react.bukkit", "com.g4vrk.react.fabric", "com.g4vrk.react.sponge"
        ), violations);
        checkPlatformIsolation(repo.resolve("platform/sponge/src/main/java"), List.of(
                "org.bukkit", "io.papermc", "net.fabricmc", "net.minestom", "net.minecraft", "com.g4vrk.react.bukkit", "com.g4vrk.react.fabric", "com.g4vrk.react.minestom"
        ), violations);
        assertTrue(violations.isEmpty(), "Sibling platform cross-imports detected: " + violations);

    }

    @Test
    void coreSubsystemsPreserveStrictLayeringHierarchy() throws IOException {

        final Path commonRoot = repoRoot().resolve("common/src/main/java");
        final List<String> violations = new ArrayList<>();
        for (final Path file : javaFiles(commonRoot)) {
            final String content = Files.readString(file);
            final String pkg = packageName(content);
            final List<String> forbiddenPrefixes;
            if (pkg.startsWith("com.g4vrk.react.history") || pkg.startsWith("com.g4vrk.react.statistic")) {
                forbiddenPrefixes = List.of("com.g4vrk.react.storage", "com.g4vrk.react.punish", "com.g4vrk.react.update", "com.g4vrk.react.extension", "com.g4vrk.react.command", "com.g4vrk.react.rcp");
            } else if (pkg.startsWith("com.g4vrk.react.event") || pkg.startsWith("com.g4vrk.react.registry") || pkg.startsWith("com.g4vrk.react.color") || pkg.startsWith("com.g4vrk.react.util")) {
                forbiddenPrefixes = List.of("com.g4vrk.react.check", "com.g4vrk.react.player", "com.g4vrk.react.storage", "com.g4vrk.react.punish", "com.g4vrk.react.update", "com.g4vrk.react.extension", "com.g4vrk.react.command", "com.g4vrk.react.rcp");
            } else if (pkg.startsWith("com.g4vrk.react.punish")) {
                forbiddenPrefixes = List.of("com.g4vrk.react.storage", "com.g4vrk.react.update", "com.g4vrk.react.extension", "com.g4vrk.react.command");
            } else if (pkg.startsWith("com.g4vrk.react.update")) {
                forbiddenPrefixes = List.of("com.g4vrk.react.check", "com.g4vrk.react.player", "com.g4vrk.react.punish", "com.g4vrk.react.storage", "com.g4vrk.react.command");
            } else if (pkg.startsWith("com.g4vrk.react.config")) {
                forbiddenPrefixes = List.of("com.g4vrk.react.check", "com.g4vrk.react.player", "com.g4vrk.react.punish", "com.g4vrk.react.update", "com.g4vrk.react.extension", "com.g4vrk.react.command", "com.g4vrk.react.rcp");
            } else {
                forbiddenPrefixes = List.of();
            }
            if (forbiddenPrefixes.isEmpty()) {
                continue;
            }
            final Matcher matcher = IMPORT_PATTERN.matcher(content);
            while (matcher.find()) {
                final String imp = matcher.group(1);
                for (final String forbidden : forbiddenPrefixes) {
                    if (imp.startsWith(forbidden)) {
                        violations.add(pkg + "/" + file.getFileName() + " violates layering by importing " + imp);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Subsystem layering violation: " + violations);

    }

    @Test
    void pluginRemainsAbstractAndCollectAddonIsExcludedFromBundledConfigs() throws IOException {

        final Path repo = repoRoot();
        final List<String> violations = new ArrayList<>();
        final List<String> forbiddenModelTokens = List.of(
                "FlowerModel",
                "VectorModel",
                "inference-gateway",
                "net.minecraft.server.v1_"
        );
        for (final Path file : javaFiles(repo.resolve("common/src/main/java"))) {
            final String withoutComments = stripBlockComments(Files.readString(file));
            for (final String token : forbiddenModelTokens) {
                if (withoutComments.contains(token)) {
                    violations.add(file.getFileName() + " leaks non-abstract reference " + token);
                }
            }
        }
        for (final String language : List.of("en", "ru")) {
            final Path mainConfig = repo.resolve("common/src/main/resources/" + language + "/main-config.yml");
            final String yaml = Files.readString(mainConfig);
            if (yaml.contains("collect-addon") || yaml.contains("collect:")) {
                violations.add(mainConfig.getFileName() + " (" + language + ") must never advertise private collect-addon");
            }
        }
        assertTrue(violations.isEmpty(), "Plugin abstraction or privacy boundary broken: " + violations);

    }

    @Test
    void mainSourcesEnforceSizeBudgetStyleAndNoEmptyDirectories() throws IOException {

        final Path repo = repoRoot();
        final List<Path> moduleMainRoots = List.of(
                repo.resolve("api/src/main/java"),
                repo.resolve("common/src/main/java"),
                repo.resolve("platform/bukkit/src/main/java"),
                repo.resolve("platform/paper/src/main/java"),
                repo.resolve("platform/folia/src/main/java"),
                repo.resolve("platform/fabric/src/main/java"),
                repo.resolve("platform/minestom/src/main/java"),
                repo.resolve("platform/sponge/src/main/java")
        );
        final List<String> violations = new ArrayList<>();
        for (final Path root : moduleMainRoots) {
            for (final Path file : javaFiles(root)) {
                final String content = Files.readString(file);
                final String pkg = packageName(content);
                final List<String> lines = content.lines().toList();
                final String fileName = file.getFileName().toString();
                if (lines.size() > MAX_MAIN_SOURCE_LINES) {
                    violations.add(fileName + " exceeds max line budget (" + lines.size() + " > " + MAX_MAIN_SOURCE_LINES + ")");
                }
                final Matcher typeMatcher = TOP_TYPE_PATTERN.matcher(content);
                if (typeMatcher.find() && PLURAL_SIMPLE_NAME.matcher(typeMatcher.group(2)).matches()) {
                    violations.add(fileName + " uses plural top-level class name " + typeMatcher.group(2));
                }
                for (int i = 0; i < lines.size(); i++) {
                    final String line = lines.get(i);
                    final String trimmed = line.trim();
                    if (trimmed.startsWith("import ") && trimmed.endsWith("*;")) {
                        violations.add(fileName + ":" + (i + 1) + " wildcard import");
                    }
                    final String noStrings = line.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
                    if (noStrings.contains("//")) {
                        violations.add(fileName + ":" + (i + 1) + " single-line // comment");
                    }
                    if (!trimmed.startsWith("*") && noStrings.contains("/*") && noStrings.contains("*/")) {
                        violations.add(fileName + ":" + (i + 1) + " single-line block comment");
                    }
                    if (noStrings.contains("System.out.print") || noStrings.contains("System.err.print") || noStrings.contains(".printStackTrace(")) {
                        violations.add(fileName + ":" + (i + 1) + " console stream or printStackTrace");
                    }
                    if (HOT_PATH_PACKAGES.contains(pkg) && noStrings.contains("String.format(")) {
                        violations.add(fileName + ":" + (i + 1) + " String.format in hot path");
                    }
                    if (!trimmed.startsWith("package ") && !trimmed.startsWith("import ") && !trimmed.startsWith("*")
                            && !FQN_COLLISION_ALLOWED_FILES.contains(fileName)) {
                        final Matcher fqn = Pattern.compile("\\b(?:java|javax|org|net|com|io|it)\\.[a-z][A-Za-z0-9_.]*\\.[A-Z][A-Za-z0-9_]*").matcher(noStrings);
                        if (fqn.find()) {
                            violations.add(fileName + ":" + (i + 1) + " FQN in code: " + fqn.group());
                        }
                    }
                }
            }
            try (Stream<Path> dirs = Files.walk(root)) {
                for (final Path dir : dirs.filter(Files::isDirectory).toList()) {
                    try (Stream<Path> children = Files.list(dir)) {
                        if (children.findAny().isEmpty()) {
                            violations.add("Empty source directory left behind: " + repo.relativize(dir));
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Architectural/style lock failed:\n - " + String.join("\n - ", violations));

    }

    private static void checkPlatformIsolation(
            final @NotNull Path root,
            final @NotNull List<String> forbiddenPrefixes,
            final @NotNull List<String> violations
    ) throws IOException {

        if (!Files.isDirectory(root)) {
            return;
        }
        for (final Path file : javaFiles(root)) {
            final Matcher matcher = IMPORT_PATTERN.matcher(Files.readString(file));
            while (matcher.find()) {
                final String imp = matcher.group(1);
                for (final String forbidden : forbiddenPrefixes) {
                    if (imp.startsWith(forbidden)) {
                        violations.add(root.getParent().getParent().getFileName() + "/" + file.getFileName() + " imports " + imp);
                    }
                }
            }
        }

    }

    @Test
    void mainSourcesNeverCollapseMethodBodiesOrOmitOpeningBlankLines() throws IOException {

        final Path repo = repoRoot();
        final List<Path> sourceRoots = new ArrayList<>();
        sourceRoots.add(repo.resolve("api/src/main/java"));
        sourceRoots.add(repo.resolve("common/src/main/java"));
        try (Stream<Path> platforms = Files.list(repo.resolve("platform"))) {
            platforms.map(platform -> platform.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .sorted()
                    .forEach(sourceRoots::add);
        }

        final List<String> violations = new ArrayList<>();
        for (final Path root : sourceRoots) {
            for (final Path file : javaFiles(root)) {
                final List<String> lines = Files.readAllLines(file);
                final String rel = repo.relativize(file).toString();
                for (int i = 0; i < lines.size(); i++) {
                    final String line = lines.get(i);
                    final int indent = leadingSpaces(line);
                    final String trimmed = line.strip();
                    if ((indent == 4 || indent == 8)
                            && trimmed.contains("{")
                            && trimmed.contains("}")
                            && trimmed.contains("(")
                            && !startsWithControlOrComment(trimmed)
                            && !trimmed.contains("->")
                            && !trimmed.contains("=")
                            && !trimmed.endsWith(";")
                            && !trimmed.matches(".*\\b(record|class|interface|enum)\\b.*")) {
                        violations.add(rel + ":" + (i + 1) + " has a one-line method or constructor");
                    } else if (isMethodOrConstructorOpen(indent, trimmed)
                            && i + 1 < lines.size()) {
                        final String next = lines.get(i + 1).strip();
                        if (!next.isEmpty() && !next.equals("}")) {
                            violations.add(rel + ":" + (i + 1) + " misses blank line after opening '{'");
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Method formatting violation: " + violations);

    }

    private static int leadingSpaces(
            final @NotNull String line
    ) {

        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;

    }

    private static boolean startsWithControlOrComment(
            final @NotNull String trimmed
    ) {

        final List<String> prefixes = List.of(
                "if ", "if(", "for ", "for(", "while ", "while(", "switch ", "switch(",
                "try ", "try(", "} catch", "catch ", "} else", "else ", "else{",
                "synchronized", "do ", "//", "*", "case ", "default:", "default ", "},"
        );
        for (final String prefix : prefixes) {
            if (trimmed.startsWith(prefix)) {
                return true;
            }
        }
        return false;

    }

    private static boolean isMethodOrConstructorOpen(
            final int indent,
            final @NotNull String trimmed
    ) {

        if (indent != 4 && indent != 8) {
            return false;
        }
        if (startsWithControlOrComment(trimmed) || trimmed.contains("->")) {
            return false;
        }
        if (trimmed.matches("^(?:public|protected|private|static|final|abstract|synchronized|native|default|\\s|<[^>]+>)*\\b(class|interface|enum|record|@interface)\\b.*")) {
            return false;
        }
        if (trimmed.equals(") {") || trimmed.matches("^\\) throws [A-Za-z0-9_.,\\s<>]+\\{$")) {
            return true;
        }
        if (trimmed.matches("^(?:public|protected|private)\\b.*")
                && trimmed.contains("(")
                && (trimmed.endsWith(") {") || trimmed.matches(".*\\)\\s*throws\\s+[^{]+\\{$"))) {
            return true;
        }
        return !trimmed.contains("=")
                && trimmed.matches("^(?:@Override\\s+)?(?:static|final|synchronized|\\s)*[\\w<>\\[\\]?@.]+\\s+\\w+\\([^;]*\\)\\s*(?:throws\\s+[^{]+)?\\{$");

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

    private static @NotNull Path repoRoot() {

        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))
                    && Files.isDirectory(current.resolve("common/src/main/java"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate plugin repository root");

    }

    private static @NotNull List<Path> javaFiles(
            final @NotNull Path root
    ) throws IOException {

        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }

    }

}
