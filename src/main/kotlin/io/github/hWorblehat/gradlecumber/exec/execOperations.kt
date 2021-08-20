@file:Suppress("UnstableApiUsage")
package io.github.hWorblehat.gradlecumber.exec

import io.github.hWorblehat.gradlecumber.dsl.CucumberExecOptions
import io.github.hWorblehat.gradlecumber.dsl.CucumberExecSpec
import io.github.hWorblehat.gradlecumber.dsl.CucumberFormatOptions
import io.github.hWorblehat.gradlecumber.dsl.DefaultCucumberExecOptions
import io.github.hWorblehat.gradlecumber.dsl.DefaultCucumberExecSpec
import mu.KotlinLogging
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec
import java.io.File
import java.net.URI
import javax.inject.Inject

private val LOGGER = KotlinLogging.logger {}

private const val CUCUMBER_MAIN = "io.cucumber.core.cli.Main"

interface CucumberExecOperations {
	fun cucumberExec(config: Action<in CucumberExecSpec>): ExecResult
	fun cucumberExec(config: CucumberExecSpec.() -> Unit): ExecResult
}

internal fun configureDefaults(execSpec: CucumberExecSpec) {
	execSpec.main = CUCUMBER_MAIN
}

internal fun CucumberExecOptions.toJavaExecSpec(execSpec: JavaExecSpec, fileGenerator: () -> File): File? {
	formats.forEach { execSpec.args("--plugin", it.asArgString()) }

	val ret = if(allowNonPassingTests.get() && !execSpec.isIgnoreExitValue) {
		execSpec.isIgnoreExitValue = true

		val existingMessageFormat = formats.find { it.pluginName.get()=="message" && it.destFile.isPresent }
		if(existingMessageFormat!=null) {
			existingMessageFormat.destFile.get()
		} else {
			val messagesToCheck = fileGenerator()
			execSpec.args("--plugin", CucumberFormatOptions.asArgString("message",
				URI.create("file://${messagesToCheck.absoluteFile}")))
			messagesToCheck
		}
	} else null

	if(glue.isPresent) execSpec.args("--glue", glue.get())
	if(tags.isPresent) execSpec.args("--tags", tags.get())
	if(scenarioRegex.isPresent) execSpec.args("--name", scenarioRegex.get())

	execSpec.args(features.args.get())

	LOGGER.info { "Cucumber arguments: ${execSpec.args?.joinToString(" ")}" }

	return ret
}

internal fun checkForErrors(result: ExecResult, messages: File?) {
	if(result.exitValue!=0 && messages!=null && !fileIndicatesCompletedTestRun(messages)) {
		result.assertNormalExitValue()
	}
}

internal open class DefaultCucumberExecOperations @Inject constructor (
	private val execOps: ExecOperations,
	private val objectFactory: ObjectFactory
) : CucumberExecOperations {

	override fun cucumberExec(config: Action<in CucumberExecSpec>): ExecResult {
		var messagesToCheck: File? = null

		val result =  execOps.javaexec {
			val cucumberOpts = objectFactory.newInstance(DefaultCucumberExecOptions::class.java)
			val cucumberExecSpec = DefaultCucumberExecSpec(it, cucumberOpts)
			configureDefaults(cucumberExecSpec)

			config.execute(cucumberExecSpec)

			messagesToCheck = cucumberOpts.toJavaExecSpec(it) {
				File.createTempFile("cucumber_messages", ".ndjson")
			}
		}

		checkForErrors(result, messagesToCheck)

		return result
	}

	override fun cucumberExec(config: CucumberExecSpec.() -> Unit): ExecResult {
		return cucumberExec { spec -> config(spec) }
	}

}

internal fun ObjectFactory.newCucumberExecOperations(): CucumberExecOperations =
	newInstance(DefaultCucumberExecOperations::class.java)
