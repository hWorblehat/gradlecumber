package dev.hworblehat.gradlecumber

import dev.hworblehat.gradlecumber.dsl.CucumberExecOptions
import dev.hworblehat.gradlecumber.dsl.CucumberExecSpec
import dev.hworblehat.gradlecumber.dsl.DefaultCucumberExecOptions
import dev.hworblehat.gradlecumber.exec.checkForErrors
import dev.hworblehat.gradlecumber.exec.configureDefaults
import dev.hworblehat.gradlecumber.exec.toJavaExecSpec
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitResult.TERMINATE
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class CucumberExec @Inject constructor(
	private val objects: ObjectFactory
) : JavaExec(),
	CucumberExecSpec,
	CucumberExecOptions by DefaultCucumberExecOptions(objects)
{

	@get:OutputFiles
	@get:Optional
	protected val formatDests: MapProperty<String, File> = objects.mapProperty(String::class.java, File::class.java)

	@get:Internal
	val formatDestFiles: Provider<Map<String, File>> = formatDests

	@get:InputFiles
	@get:SkipWhenEmpty
	protected val featureFiles: FileCollection

	init {
		configureDefaults(this)

		formats.whenObjectAdded { format ->
			formatDests.put(format.name, format.destFile)
		}

		val ff = objects.fileCollection()
		ff.from(features.allFiles.filter(::containsFeatureFiles))
		ff.from(features.includesClasspath.map { includesClasspath ->
			if(includesClasspath) classpath else objects.fileCollection()
		})
		featureFiles = ff
	}

	fun formatDestFile(formatName: String): Provider<File> = formatDestFiles.map { it[formatName]!! }

	@Option(option = "scenario-name", description = "Filters the cucumber scenarios to be run by the given regex")
	fun setScenarioNameRegex(regex: String) {
		scenarioRegex.set(regex)
	}

	@Option(option = "tags", description = "Filters the cucumber scenarios to be run by the given tag expression")
	fun setTags(tagExpression: String) {
		tags.set(tagExpression)
	}

	@TaskAction
	override fun exec() {
		val messagesToCheck = toJavaExecSpec(this) {
			temporaryDir.mkdirs()
			File(temporaryDir, "messages.ndjson")
		}
		super.exec()
		checkForErrors(executionResult.get(), messagesToCheck)
	}

	private fun containsFeatureFiles(file: File): Boolean {
		var containsFiles = false
		if(file.exists()) {
			try {
				Files.walkFileTree(file.toPath(), object : SimpleFileVisitor<Path>() {
					override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult =
						if (file?.fileName.toString().endsWith(".feature")) {
							containsFiles = true
							TERMINATE
						} else super.visitFile(file, attrs)
				})
			} catch(ex: IOException) {
				logger.info("IOException occurred when searching for feature files.", ex)
			}
		}
		return containsFiles
	}

}
