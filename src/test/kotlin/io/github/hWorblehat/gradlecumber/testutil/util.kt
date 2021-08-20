package io.github.hWorblehat.gradlecumber.testutil

import io.kotest.core.TestConfiguration
import org.apache.commons.io.file.PathUtils
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

val ANSI_SEQUENCE_REGEX = Regex("(?:\\x1B\\x5B|\\x9B)[\\x30-\\x3f]*[\\x20-\\x2F]*[\\x30-\\x7E]")

fun String.removeANSISequences(): String = replace(ANSI_SEQUENCE_REGEX, "")
fun String.escapeWindowsFileSeparators() = replace("\\", "\\\\")

fun TestConfiguration.cleanupAfter(dir: Path): Path {
	afterSpec {
		try {
			PathUtils.delete(dir)
		} catch(e: IOException) {/* ignore */}
	}
	return dir
}

fun TestConfiguration.tempdir(prefix: String? = null): File = cleanupAfter(Files.createTempDirectory(prefix)).toFile()

fun TestConfiguration.cleanupAfter(project: Project): Project {
	cleanupAfter(project.projectDir.toPath())
	return project
}

fun TestConfiguration.testProject(): Project = cleanupAfter(ProjectBuilder.builder().build())
