package io.github.hWorblehat.gradlecumber

import io.github.hWorblehat.gradlecumber.analysis.CucumberResultsException
import io.github.hWorblehat.gradlecumber.analysis.MalformedCucumberMessagesException
import io.github.hWorblehat.gradlecumber.analysis.analyseMessages
import io.github.hWorblehat.gradlecumber.analysis.checkTestRunResults
import io.github.hWorblehat.gradlecumber.dsl.CucumberResultsAnalysisSpec
import io.github.hWorblehat.gradlecumber.dsl.DefaultCucumberResultsAnalysisSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.IOException
import javax.inject.Inject

open class CucumberCheckResults @Inject constructor(
	objects: ObjectFactory,
	layout: ProjectLayout
) : DefaultTask(), CucumberResultsAnalysisSpec by objects.newInstance(DefaultCucumberResultsAnalysisSpec::class.java) {

	@OutputFile
	val logFile = objects.fileProperty()
		.convention(layout.buildDirectory.file("${javaClass.simpleName.decapitalize()}/$name"))

	@TaskAction
	@Throws(CucumberResultsException::class, MalformedCucumberMessagesException::class, IOException::class)
	fun checkResults() {
		val results = analyseMessages(this)
		val f = logFile.get().asFile
		f.parentFile.mkdirs()
		f.bufferedWriter().use {
			results.appendTo(it)
		}
		checkTestRunResults(results, logger)
	}

}