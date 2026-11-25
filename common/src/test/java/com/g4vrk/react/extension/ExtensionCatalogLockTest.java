/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.react.extension;

import com.g4vrk.react.api.extension.meta.ExtensionId;
import com.g4vrk.react.extension.descriptor.ExtensionDescriptor;
import com.g4vrk.react.extension.descriptor.ExtensionKind;
import com.g4vrk.react.extension.descriptor.ExtensionPlan;
import com.g4vrk.react.extension.descriptor.InvalidExtensionException;
import com.g4vrk.react.extension.loader.ExtensionCatalog;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Locks extension descriptor validation, isolated bootstrap plan reading, and topological dependency selection.
 */
public final class ExtensionCatalogLockTest {

    @Test
    void rejectsMalformedOrUnsafeExtensionDescriptors(@TempDir final Path dir) throws Exception {

        final Path badNameJar = addonJar(
                dir,
                "bad-name.jar",
                "name: '../escape'\nversion: '1.0'\nmainClass: com.example.NoOp\napi-version: 2\n",
                "BadNameBootstrap",
                "context.identify(ExtensionId.addon(\"bad\"), 1);"
        );
        assertThrows(InvalidExtensionException.class, () -> ExtensionDescriptor.read(ExtensionKind.ADDON, badNameJar));

        final Path badApiJar = addonJar(
                dir,
                "bad-api.jar",
                "name: 'bad-api'\nversion: '1.0'\nmainClass: com.example.NoOp\napi-version: 999\n",
                "BadApiBootstrap",
                "context.identify(ExtensionId.addon(\"bad-api\"), 1);"
        );
        assertThrows(InvalidExtensionException.class, () -> ExtensionDescriptor.read(ExtensionKind.ADDON, badApiJar));

    }

    @Test
    void nextReadyOrdersDependenciesAndDetectsCycles(@TempDir final Path dir) throws Exception {

        final ExtensionDescriptor alphaDesc = ExtensionDescriptor.read(
                ExtensionKind.ADDON,
                addonJar(dir, "alpha.jar", yaml("alpha"), "AlphaBootstrap", "context.identify(ExtensionId.addon(\"alpha\"), 1);")
        );
        final ExtensionDescriptor betaDesc = ExtensionDescriptor.read(
                ExtensionKind.ADDON,
                addonJar(
                        dir,
                        "beta.jar",
                        yaml("beta"),
                        "BetaBootstrap",
                        "context.identify(ExtensionId.addon(\"beta\"), 1); context.require(ExtensionId.addon(\"alpha\"), 1);"
                )
        );
        final Map<ExtensionId, ExtensionCatalog.Candidate> pending = new LinkedHashMap<>();
        final ExtensionPlan betaPlan = ExtensionPlan.read(betaDesc);
        final ExtensionPlan alphaPlan = ExtensionPlan.read(alphaDesc);
        pending.put(betaPlan.id(), new ExtensionCatalog.Candidate(betaDesc, betaPlan));
        pending.put(alphaPlan.id(), new ExtensionCatalog.Candidate(alphaDesc, alphaPlan));

        final ExtensionCatalog.Candidate first = ExtensionCatalog.nextReady(pending);
        assertNotNull(first);
        assertEquals(ExtensionId.addon("alpha"), first.plan().id());
        pending.remove(first.plan().id());

        final ExtensionCatalog.Candidate second = ExtensionCatalog.nextReady(pending);
        assertNotNull(second);
        assertEquals(ExtensionId.addon("beta"), second.plan().id());

        final ExtensionDescriptor cycleADesc = ExtensionDescriptor.read(
                ExtensionKind.ADDON,
                addonJar(
                        dir,
                        "cycle-a.jar",
                        yaml("cycle-a"),
                        "CycleABootstrap",
                        "context.identify(ExtensionId.addon(\"cycle-a\"), 1); context.require(ExtensionId.addon(\"cycle-b\"), 1);"
                )
        );
        final ExtensionDescriptor cycleBDesc = ExtensionDescriptor.read(
                ExtensionKind.ADDON,
                addonJar(
                        dir,
                        "cycle-b.jar",
                        yaml("cycle-b"),
                        "CycleBBootstrap",
                        "context.identify(ExtensionId.addon(\"cycle-b\"), 1); context.require(ExtensionId.addon(\"cycle-a\"), 1);"
                )
        );
        final ExtensionPlan cycleAPlan = ExtensionPlan.read(cycleADesc);
        final ExtensionPlan cycleBPlan = ExtensionPlan.read(cycleBDesc);
        final Map<ExtensionId, ExtensionCatalog.Candidate> cyclic = new LinkedHashMap<>();
        cyclic.put(cycleAPlan.id(), new ExtensionCatalog.Candidate(cycleADesc, cycleAPlan));
        cyclic.put(cycleBPlan.id(), new ExtensionCatalog.Candidate(cycleBDesc, cycleBPlan));

        assertNull(ExtensionCatalog.nextReady(cyclic));

    }

    private static String yaml(
            final @NotNull String name
    ) {

        return "name: '" + name + "'\nversion: '1.0.0'\nmainClass: com.example." + name + "\napi-version: 2\n";

    }

    private static Path addonJar(
            final @NotNull Path workDir,
            final @NotNull String jarName,
            final @NotNull String addonYaml,
            final @NotNull String simpleClassName,
            final @NotNull String body
    ) throws Exception {

        final String pkg = "test.isolated." + simpleClassName.toLowerCase();
        final Path compileRoot = Files.createTempDirectory(workDir, "compile-");
        final Path sourceFile = compileRoot.resolve(simpleClassName + ".java");
        final String source = "package " + pkg + ";\n"
                + "import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrap;\n"
                + "import com.g4vrk.react.api.extension.bootstrap.ExtensionBootstrapContext;\n"
                + "import com.g4vrk.react.api.extension.meta.ExtensionId;\n"
                + "public final class " + simpleClassName + " implements ExtensionBootstrap {\n"
                + "    @Override\n"
                + "    public void bootstrap(final ExtensionBootstrapContext context) {\n"
                + "        " + body + "\n"
                + "    }\n"
                + "}\n";
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required for isolated extension bootstrap lock tests");
        final ByteArrayOutputStream errors = new ByteArrayOutputStream();
        final int compiled = compiler.run(
                null,
                null,
                errors,
                "-d", compileRoot.toString(),
                "-cp", System.getProperty("java.class.path"),
                sourceFile.toString()
        );
        if (compiled != 0) {
            throw new IOException("Failed to compile test bootstrap: " + errors.toString(StandardCharsets.UTF_8));
        }

        final String binaryName = pkg + "." + simpleClassName;
        final String classEntry = binaryName.replace('.', '/') + ".class";
        final byte[] classBytes = Files.readAllBytes(compileRoot.resolve(classEntry));
        final Path target = workDir.resolve(jarName);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target))) {
            out.putNextEntry(new JarEntry("react-addon.yml"));
            out.write(addonYaml.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry(ExtensionPlan.BOOTSTRAP_SERVICE));
            out.write((binaryName + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry(classEntry));
            out.write(classBytes);
            out.closeEntry();
        }
        return target;

    }

}
