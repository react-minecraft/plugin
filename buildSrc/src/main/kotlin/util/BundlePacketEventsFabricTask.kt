package util

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.TreeMap
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry

/**
 * Merges PacketEvents' Adventure libraries into its own mod class loader.
 */
abstract class BundlePacketEventsFabricTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJars: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun bundle() {

        val sources = inputJars.files.sortedBy { it.name }
        val packetEvents = sources.singleOrNull { hasFabricMetadata(it) }
            ?: throw IllegalStateException("Expected one PacketEvents Fabric JAR with fabric.mod.json")
        val entries = TreeMap<String, ByteArray>()
        val services = TreeMap<String, LinkedHashSet<String>>()

        packetEvents.inputStream().use { mergeArchive(it, packetEvents.name, entries, services, true) }
        sources.filterNot { it == packetEvents }.forEach { source ->
            source.inputStream().use { mergeArchive(it, source.name, entries, services, false) }
        }
        services.forEach { (name, lines) ->
            entries[name] = (lines.joinToString("\n", postfix = "\n")).toByteArray(StandardCharsets.UTF_8)
        }

        val output = outputJar.get().asFile
        output.parentFile.mkdirs()
        JarOutputStream(output.outputStream().buffered()).use { archive ->
            entries.forEach { (name, contents) ->
                val entry = ZipEntry(name)
                entry.time = 0L
                archive.putNextEntry(entry)
                archive.write(contents)
                archive.closeEntry()
            }
        }

    }

    private fun hasFabricMetadata(file: File): Boolean {

        return JarFile(file).use { it.getJarEntry("fabric.mod.json") != null }

    }

    private fun mergeArchive(
            input: InputStream,
            sourceName: String,
            entries: MutableMap<String, ByteArray>,
            services: MutableMap<String, LinkedHashSet<String>>,
            rootArchive: Boolean
    ) {

        ZipInputStream(input.buffered()).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                if (entry.isDirectory) {
                    continue
                }
                val contents = archive.readBytes()
                if (entry.name.startsWith("META-INF/jars/") && entry.name.endsWith(".jar")) {
                    val nestedName = entry.name.substringAfterLast('/')
                    if (rootArchive && isAdventureDependency(nestedName)) {
                        mergeArchive(ByteArrayInputStream(contents), nestedName, entries, services, false)
                    } else {
                        entries.putIfAbsent(entry.name, contents)
                    }
                    continue
                }
                if (entry.name == "fabric.mod.json") {
                    if (rootArchive) {
                        entries[entry.name] = stripNestedJarMetadata(contents)
                    }
                    continue
                }
                if (isSignature(entry.name) || entry.name == "META-INF/INDEX.LIST" || entry.name.endsWith("module-info.class")) {
                    continue
                }
                if (entry.name.startsWith("META-INF/services/")) {
                    val lines = services.computeIfAbsent(entry.name) { LinkedHashSet() }
                    String(contents, StandardCharsets.UTF_8).lineSequence()
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .forEach(lines::add)
                    continue
                }
                if (isLicense(entry.name)) {
                    val licensePath = "META-INF/react-licenses/${sourceName.removeSuffix(".jar")}/${entry.name.substringAfterLast('/')}"
                    entries.putIfAbsent(licensePath, contents)
                }
                entries.putIfAbsent(entry.name, contents)
            }
        }

    }

    private fun stripNestedJarMetadata(contents: ByteArray): ByteArray {

        @Suppress("UNCHECKED_CAST")
        val metadata = JsonSlurper().parseText(String(contents, StandardCharsets.UTF_8)) as MutableMap<String, Any?>
        val nestedJars = metadata["jars"] as? List<*>
        if (nestedJars != null) {
            metadata["jars"] = nestedJars.filterNot { nestedJar ->
                val file = (nestedJar as? Map<*, *>)?.get("file") as? String
                file != null && isAdventureDependency(file.substringAfterLast('/'))
            }
        }
        return (JsonOutput.prettyPrint(JsonOutput.toJson(metadata)) + "\n")
            .toByteArray(StandardCharsets.UTF_8)

    }

    private fun isAdventureDependency(name: String): Boolean {

        return name.startsWith("adventure-api-")
            || name.startsWith("adventure-key-")
            || name.startsWith("adventure-nbt-")
            || name.startsWith("adventure-text-logger-slf4j-")
            || name.startsWith("examination-api-")
            || name.startsWith("examination-string-")

    }

    private fun isSignature(name: String): Boolean {

        val upperName = name.uppercase()
        return upperName.startsWith("META-INF/")
            && listOf(".SF", ".RSA", ".DSA", ".EC").any(upperName::endsWith)

    }

    private fun isLicense(name: String): Boolean {

        val fileName = name.substringAfterLast('/').uppercase()
        return fileName.startsWith("LICENSE") || fileName.startsWith("NOTICE") || fileName.startsWith("COPYING")

    }

}
