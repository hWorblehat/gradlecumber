package io.github.hWorblehat.gradlecumber.testutil

import io.github.hWorblehat.gradlecumber.dsl.CucumberExecSpec
import org.gradle.api.Project
import org.gradle.testkit.runner.GradleRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

private val TEST_GLUE: List<String> = System.getProperty("gradlecumber.test.gluecp")
	.split(System.getProperty("path.separator"))
val BASE_PLUGIN_ID: String = System.getProperty("gradlecumber.test.pluginName")

fun File.writeInputStream(data: InputStream) {
	outputStream().use(data::copyTo)
}

fun File.writeResource(resource: String) {
	{}::class.java.getResourceAsStream(resource).use { writeInputStream(it) }
}

interface ProjectStruct {

	fun getFile(path: String): File

	fun createFile(path: String): File {
		val file = getFile(path)
		file.parentFile.mkdirs()
		return file
	}

	fun writeMessagesSample(name: String, destFolder: String, destName: String? = null) {
		val resourceFileName = "$name.ndjson"
		createFile("$destFolder/${destName ?: resourceFileName}")
			.writeResource("/messageSamples/$resourceFileName")
	}

	fun writeDummyFeature(name: String, destFolder: String, destName: String? = null) {
		val resourceFileName = "$name.feature"
		createFile("$destFolder/${destName ?: resourceFileName}")
			.writeResource("/dummyFeatures/$resourceFileName")
	}

	fun addUnimplementedFeatures(dest: String = "unimplementedFeatures") = writeDummyFeature("unimplemented", dest)

	fun addPassingFeatures(dest: String = "passingFeatures") = writeDummyFeature("passing", dest)

	fun addFailingFeatures(dest: String = "failingFeatures") = writeDummyFeature("failing", dest)

	fun addAllFeatureFiles() {
		addPassingFeatures()
		addFailingFeatures()
		addUnimplementedFeatures()
	}

	fun createSettingsFile(projectName: String = "dummy"): File {
		val settings = createFile("settings.gradle.kts")
		settings.writeText("""
			rootProject.name = "$projectName"
		""".trimIndent())
		return settings
	}

	fun createBuildFile(ext: String = ".gradle.kts"): File = createFile("build$ext")

}

fun Project.struct(config: ProjectStruct.() -> Unit): Project {
	object : ProjectStruct {
		override fun getFile(path: String): File = file(path)
	}.config()
	return this
}

fun File.projectStruct(config: ProjectStruct.() -> Unit): File {
	object : ProjectStruct {
		override fun getFile(path: String): File = File(this@projectStruct, path)
	}.config()
	return this
}

fun pluginClasspathKts(gradleRunner: GradleRunner): String = gradleRunner
	.pluginClasspath
	.map {
		it.absolutePath.escapeWindowsFileSeparators()
	}
	.joinToString("\", \"", "classpath(files(\"", "\"))")

val testGlueKts: String = TEST_GLUE
	.map { it.escapeWindowsFileSeparators() }
	.joinToString("\", \"", "files(\"", "\")")

val testGlue: List<String> = TEST_GLUE

fun CucumberExecSpec.silenceOutput() {
	standardOutput = ByteArrayOutputStream()
	errorOutput = ByteArrayOutputStream()
}
