@file:Suppress("UnstableApiUsage")
package io.github.hWorblehat.gradlecumber.dsl

import io.github.hWorblehat.gradlecumber.analysis.OK as ResultOK
import io.github.hWorblehat.gradlecumber.CucumberResultInfo
import io.github.hWorblehat.gradlecumber.CucumberResultMessageFactory
import io.github.hWorblehat.gradlecumber.analysis.CucumberResultsCheckOperations
import io.github.hWorblehat.gradlecumber.analysis.ResultChecker
import io.github.hWorblehat.gradlecumber.analysis.checkNotPassedOrSkipped
import io.github.hWorblehat.gradlecumber.analysis.newCucumberResultsCheckOperations
import io.github.hWorblehat.gradlecumber.exec.CucumberExecOperations
import io.github.hWorblehat.gradlecumber.exec.DefaultCucumberExecOperations
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.SourceSet
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.inject.Inject

internal const val CUCUMBER_EXTENSION_NAME = "cucumber"
internal const val CUCUMBER_EXEC_TASK_BASE_NAME = "cucumber"
internal const val CUCUMBER_CHECK_RESULTS_TASK_BASE_NAME =  "checkCucumberResults"

interface CucumberExtension : CucumberExecOperations, CucumberResultsCheckOperations {
	companion object {
		internal val LOGGER = LoggerFactory.getLogger(CucumberExtension::class.java)
	}

	val OK: String

	val configurationName: String

	val suites: NamedDomainObjectContainer<CucumberSuite>

	fun checkResultNotPassedOrSkipped(result: CucumberResultInfo): String?

	fun checkResultNotPassedOrSkipped(result: CucumberResultInfo, messageFactory: CucumberResultMessageFactory): String?
}

internal open class DefaultCucumberExtension @Inject constructor(
	private val objects: ObjectFactory,
	execOperations: ExecOperations,
	private val cucumberConfiguration: Configuration,
	logger: Logger
) : DefaultCucumberExecOperations(execOperations, objects),
	CucumberResultsCheckOperations by objects.newCucumberResultsCheckOperations(logger),
	CucumberExtension
{

	override val OK: String = ResultOK

	override val configurationName: String = cucumberConfiguration.name

	override val suites: NamedDomainObjectContainer<CucumberSuite> = objects
		.domainObjectContainer(CucumberSuite::class.java) { name ->
			objects.newInstance(DefaultCucumberSuite::class.java, name)
		}

	override fun checkResultNotPassedOrSkipped(result: CucumberResultInfo): String? =
		checkNotPassedOrSkipped(result)

	override fun checkResultNotPassedOrSkipped(
		result: CucumberResultInfo,
		messageFactory: CucumberResultMessageFactory
	): String? = checkNotPassedOrSkipped(result, messageFactory)

	override fun cucumberExec(config: Action<in CucumberExecSpec>): ExecResult = super.cucumberExec { spec ->
		config.execute(spec)
		if (spec.classpath.find { it.name.startsWith("cucumber-core") } == null) {
			spec.classpath(cucumberConfiguration)
		}
	}

}

internal fun ExtensionContainer.createCucumberExtension(
	cucumberConfiguration: Configuration,
	logger: Logger = CucumberExtension.LOGGER
) = create(CucumberExtension::class.java, CUCUMBER_EXTENSION_NAME, DefaultCucumberExtension::class.java,
	cucumberConfiguration,
	logger
)

interface CucumberSuite : CucumberOptions, CucumberRulesSpec, Named {

	var inheritsSourceSet: DomainObjectSet<SourceSet>

	fun whenRulesUpdated(action: (ResultChecker) -> Unit)

	val cucumberExecTaskName: String

	val checkResultsTaskName: String

	val lifecycleTaskName: String

	val sourceSetName: String

}

internal open class DefaultCucumberSuite @Inject constructor(
	objects: ObjectFactory, private val name: String
) : DefaultCucumberRulesSpec(objects), CucumberSuite,
	CucumberOptions by objects.newInstance(DefaultCucumberOptions::class.java)
{

	private val rulesObservers: MutableCollection<(ResultChecker) -> Unit> = ArrayList()

	override var inheritsSourceSet: DomainObjectSet<SourceSet> = objects.domainObjectSet(SourceSet::class.java)

	override val cucumberExecTaskName: String = cucumberExecTaskName(name)
	override val checkResultsTaskName: String = cucumberCheckResultsTaskName(name)
	override val lifecycleTaskName: String = cucumberLifecycleTaskName(name)
	override val sourceSetName: String = name

	override fun getName(): String = name

	override fun rules(checker: ResultChecker) {
		super<DefaultCucumberRulesSpec>.rules(checker)
		rulesObservers.forEach { it(checker) }
	}

	override fun whenRulesUpdated(action: (ResultChecker) -> Unit) {
		action(rules.forUseAtConfigurationTime().get())
		rulesObservers.add(action)
	}

}

internal fun cucumberExecTaskName(suiteName: String) = "$CUCUMBER_EXEC_TASK_BASE_NAME${suiteName.capitalize()}"
internal fun cucumberCheckResultsTaskName(suiteName: String) = "$CUCUMBER_CHECK_RESULTS_TASK_BASE_NAME${suiteName.capitalize()}"
internal fun cucumberLifecycleTaskName(suiteName: String) = suiteName
