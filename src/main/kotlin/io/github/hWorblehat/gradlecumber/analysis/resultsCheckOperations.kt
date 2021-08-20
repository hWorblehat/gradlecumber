package io.github.hWorblehat.gradlecumber.analysis

import io.github.hWorblehat.gradlecumber.dsl.CucumberResultsAnalysisSpec
import io.github.hWorblehat.gradlecumber.dsl.DefaultCucumberResultsAnalysisSpec
import mu.KotlinLogging
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.jvm.Throws

private val LOGGER = KotlinLogging.logger {}

interface CucumberResultsCheckOperations {

	@Throws(CucumberResultsException::class, MalformedCucumberMessagesException::class, IOException::class)
	fun checkCucumberResults(messages: File, rules: ResultChecker)

	@Throws(CucumberResultsException::class, MalformedCucumberMessagesException::class, IOException::class)
	fun checkCucumberResults(messages: File, checkError: ResultCheckError)

	@Throws(CucumberResultsException::class, MalformedCucumberMessagesException::class, IOException::class)
	fun checkCucumberResults(config: Action<CucumberResultsAnalysisSpec>)

	@Throws(CucumberResultsException::class, MalformedCucumberMessagesException::class, IOException::class)
	fun checkCucumberResults(config: CucumberResultsAnalysisSpec.() -> Unit)

}

class CucumberResultsException (
	val result: TestRunResults
) : Exception("Found ${result.problemCount} ${if(result.problemCount==1) "problem" else "problems"} with Cucumber results") {

	fun logResults(logger: Logger = LOGGER.underlyingLogger) {
		val sb = StringBuilder().append(message).append(':').appendLine().appendLine()
		result.appendTo(sb)
		logger.error(sb.toString())
	}

}

@Throws(MalformedCucumberMessagesException::class, IOException::class)
internal fun analyseMessages(spec: CucumberResultsAnalysisSpec): TestRunResults =
	analyseMessages(spec.messages.get().asFile, spec.rules.get())

@Throws(CucumberResultsException::class)
internal fun checkTestRunResults(result: TestRunResults, logger: Logger) {
	if(result.problemCount > 0) {
		val ex = CucumberResultsException(result)
		ex.logResults(logger)
		throw ex
	}
}

internal open class DefaultCucumberResultsCheckOperations @Inject constructor(
	private val objects: ObjectFactory, private val logger: Logger
) : CucumberResultsCheckOperations {

	@Throws(CucumberResultsException::class, MalformedCucumberMessagesException::class, IOException::class)
	override fun checkCucumberResults(messages: File, rules: ResultChecker) =
		checkTestRunResults(analyseMessages(messages, rules), logger)

	@Throws(CucumberResultsException::class, MalformedCucumberMessagesException::class, IOException::class)
	override fun checkCucumberResults(messages: File, checkError: ResultCheckError) =
		checkCucumberResults(messages, ResultChecker { it.checkError() })

	@Throws(CucumberResultsException::class, MalformedCucumberMessagesException::class, IOException::class)
	override fun checkCucumberResults(config: Action<CucumberResultsAnalysisSpec>) {
		val spec = DefaultCucumberResultsAnalysisSpec(objects)
		config.execute(spec)
		checkTestRunResults(analyseMessages(spec), logger)
	}

	@Throws(CucumberResultsException::class, MalformedCucumberMessagesException::class, IOException::class)
	override fun checkCucumberResults(config: CucumberResultsAnalysisSpec.() -> Unit) =
		checkCucumberResults( Action<CucumberResultsAnalysisSpec> { it.config() })

}

internal fun ObjectFactory.newCucumberResultsCheckOperations(logger: Logger): CucumberResultsCheckOperations =
	newInstance(DefaultCucumberResultsCheckOperations::class.java, logger)
