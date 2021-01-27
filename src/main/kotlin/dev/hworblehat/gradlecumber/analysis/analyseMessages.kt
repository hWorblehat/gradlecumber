package dev.hworblehat.gradlecumber.analysis

import io.cucumber.messages.Messages
import io.cucumber.messages.Messages.Envelope.MessageCase.*
import io.cucumber.messages.Messages.GherkinDocument.Feature.FeatureChild.ValueCase
import io.cucumber.messages.Messages.TestStepFinished.TestStepResult.Status.PASSED
import io.cucumber.messages.Messages.TestStepFinished.TestStepResult.Status.SKIPPED
import io.cucumber.messages.NdjsonToMessageIterable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import io.cucumber.messages.Messages.GherkinDocument.Feature.FeatureChild.RuleChild.ValueCase as RuleChildCase

const val OK = "OK"

data class ResultInfo(
	val result: Messages.TestStepFinished.TestStepResult,
	val pickle: PickleInfo,
	val step: TestCaseStep,
	val scenario: ScenarioInfo,
	val rule: RuleInfo?,
	val feature: FeatureInfo
)

@FunctionalInterface
fun interface MessageFactory {
	fun createMessage(result: ResultInfo): String
}

@FunctionalInterface
fun interface ResultChecker {
	fun check(result: ResultInfo): String?
}

typealias ResultCheckError = ResultInfo.() -> String?

val NOT_PASSED_OR_SKIPPED: ResultChecker = ResultChecker { checkNotPassedOrSkipped(it) }

private val DEFAULT_CHECKER: ResultChecker = NOT_PASSED_OR_SKIPPED

@Throws(MalformedCucumberMessagesException::class)
fun analyseMessages(
	messages: Iterable<Messages.Envelope>,
	checker: ResultChecker = DEFAULT_CHECKER
): TestRunResults {

	val documents: MutableMap<String, DocumentInfo> = HashMap()
	val pickles: MutableMap<String, PickleNodeInfo> = HashMap()
	val testCases: MutableMap<String, Messages.TestCase> = HashMap()
	val testCaseRuns: MutableMap<String, String> = HashMap()

	val analysis = TestRunResultBuilder()

	for(message in messages) {
		when(message.messageCase) {
			GHERKIN_DOCUMENT -> documents[message.gherkinDocument.uri] = DocumentInfo(message.gherkinDocument)
			PICKLE -> pickles[message.pickle.id] = PickleNodeInfo(message.pickle)
			TEST_CASE -> testCases[message.testCase.id] = message.testCase
			TEST_CASE_STARTED -> testCaseRuns[message.testCaseStarted.id] = message.testCaseStarted.testCaseId
			TEST_CASE_FINISHED -> {
				val testCaseStartedId = message.testCaseFinished.testCaseStartedId
				val testCaseId = testCaseRuns[testCaseStartedId]
				pickles.remove(testCases[testCaseId]?.pickleId)
				testCases.remove(testCaseId)
				testCaseRuns.remove(testCaseStartedId)
			}

			TEST_STEP_FINISHED -> {
				val step = message.testStepFinished
				val result = step.testStepResult
				val testCaseId = testCaseRuns[step.testCaseStartedId] ?: throw MalformedCucumberMessagesException(
					"No testCaseStarted found with id '${step.testCaseStartedId}' for testStep '${step.testStepId}'.")
				val testCase = testCases[testCaseId] ?: throw MalformedCucumberMessagesException(
					"No testCase found with id '$testCaseId' associated with testCaseStarted '${step.testCaseStartedId}'.")
				val pickleInfo = pickles[testCase.pickleId] ?: throw MalformedCucumberMessagesException(
					"No pickle found with id '${testCase.pickleId}' for testCase '$testCaseId'.")
				val pickle = pickleInfo.pickle
				val document = documents[pickle.uri] ?: throw MalformedCucumberMessagesException(
					"No gherkinDocument found with URI '${pickle.uri}' for pickle '${pickle.id}'")
				val testCaseStep = testCase.testStepsList.find { it.id == step.testStepId } ?: throw MalformedCucumberMessagesException(
					"No testCase step found with id '${step.testStepId}' in testCase '${testCase.id}'.")

				if(pickle.astNodeIdsCount == 0) {
					throw MalformedCucumberMessagesException("Pickle '${pickle.id}' has no associated AST node IDs.")
				}

				val stepInfo = createTestCaseStep(document, pickle, testCaseStep.pickleStepId)
				val scenarioInfo = document.createScenarioInfo(pickle)
				val resultFactory = document.getResultFactory(pickle)

				val reason = checker.check(
					ResultInfo(result, pickleInfo.info, stepInfo, scenarioInfo, resultFactory.rule, document.feature)
				)

				if(reason!=null && !reason.equals(OK, ignoreCase = true)) {
					resultFactory.addResultToBuilder(
						analysis.getOrCreateFeatureResultBuilder(document.feature),
						pickle.id,
						DefaultScenarioResult(
							reason = reason,
							scenario = scenarioInfo,
							step = stepInfo,
							status = result.status,
							message = result.message
						)
					)
				}
			}

			else -> {/* Do nothing*/}
		}
	}

	return analysis.toResult()
}

@Throws(MalformedCucumberMessagesException::class)
fun analyseMessages(messages: Iterable<Messages.Envelope>, checkError: ResultCheckError): TestRunResults =
	analyseMessages(messages, ResultChecker { it.checkError() })

@Throws(MalformedCucumberMessagesException::class, IOException::class)
fun analyseMessages(messages: InputStream, checker: ResultChecker = DEFAULT_CHECKER): TestRunResults =
	analyseMessages(NdjsonToMessageIterable(messages), checker)

@Throws(MalformedCucumberMessagesException::class, IOException::class)
fun analyseMessages(messages: InputStream, checkError: ResultCheckError): TestRunResults =
	analyseMessages(messages, ResultChecker { it.checkError() })

@Throws(MalformedCucumberMessagesException::class, IOException::class)
fun analyseMessages(messages: File, checker: ResultChecker = DEFAULT_CHECKER): TestRunResults =
	messages.inputStream().use { analyseMessages(it, checker) }

@Throws(MalformedCucumberMessagesException::class, IOException::class)
fun analyseMessages(messages: File, checkError: ResultCheckError): TestRunResults =
	analyseMessages(messages, ResultChecker { it.checkError() })

@Throws(MalformedCucumberMessagesException::class, IOException::class)
fun analyseMessages(messages: Path, checker: ResultChecker = DEFAULT_CHECKER): TestRunResults =
	Files.newInputStream(messages).use { analyseMessages(it, checker) }

@Throws(MalformedCucumberMessagesException::class, IOException::class)
fun analyseMessages(messages: Path, checkError: ResultCheckError): TestRunResults =
	analyseMessages(messages, ResultChecker { it.checkError() })

fun checkNotPassedOrSkipped(
	result: ResultInfo,
	messageFactory: MessageFactory = MessageFactory {"${it.step.type} ${it.result.status}"}
): String? =
	if(result.result.status!=PASSED && result.result.status!=SKIPPED && !result.result.willBeRetried) {
		messageFactory.createMessage(result)
	} else null

internal fun createTestCaseStep(
	documentInfo: DocumentInfo,
	pickle: Messages.Pickle,
	pickleStepId: String?
): TestCaseStep {
	if (pickleStepId == null || pickleStepId.isEmpty()) {
		return HookInfo
	} else {
		val pickleStep = pickle.stepsList.find { it.id == pickleStepId } ?: throw MalformedCucumberMessagesException(
			"No pickle step found with id '$pickleStepId' in pickle with id '${pickle.id}'."
		)
		if (pickleStep.astNodeIdsCount == 0) {
			throw MalformedCucumberMessagesException("Pickle step '$pickleStepId' in pickle '${pickle.id}' has no associated AST node IDs.")
		}
		val stepAstNodeId = pickleStep.astNodeIdsList[0]
		val astStep = documentInfo.stepsByAstNodeId[stepAstNodeId] ?: throw MalformedCucumberMessagesException(
			"No AST node with id '$stepAstNodeId' for pickle step '${pickleStep.id}' in gherkinDocument '${documentInfo.feature.uri}'."
		)
		val background = documentInfo.backgroundsByAstNodeId[stepAstNodeId]

		return if (background == null) {
			StepInfo(
				keyword = astStep.keyword,
				text = pickleStep.text,
				line = astStep.location.line,
			)
		} else {
			BackgroundStepInfo(
				keyword = astStep.keyword,
				text = pickleStep.text,
				line = astStep.location.line,
				background = background,
			)
		}

	}
}

internal interface ResultCollector {
	fun addResult(pickleId: String, value: DefaultScenarioResult)
}

internal interface ResultBuilder {
	fun toResult(): AbstractAnalysisResult
}

internal class RuleResultBuilder(
	private val rule: RuleInfo
) : ResultBuilder, ResultCollector {
	private val scenarioResults = ArrayList<DefaultScenarioResult>()

	override fun toResult(): DefaultRuleResults = DefaultRuleResults(
		rule = rule,
		scenarios = scenarioResults
	)

	override fun addResult(pickleId: String, value: DefaultScenarioResult) {
		scenarioResults.add(value)
	}
}

internal class FeatureResultBuilder(
	private val feature: FeatureInfo
) : ResultBuilder, ResultCollector {
	private val childResults = LinkedHashMap<String, ResultBuilder>()

	override fun addResult(pickleId: String, value: DefaultScenarioResult) {
		childResults[pickleId] = ScenarioResultHolder(value)
	}

	fun getOrCreateRuleResultBuilder(astNodeId: String, rule: RuleInfo): RuleResultBuilder {
		val resultBuilder = childResults.computeIfAbsent(astNodeId) { RuleResultBuilder(rule) }
		if(resultBuilder is RuleResultBuilder) {
			return resultBuilder
		}
		throw MalformedCucumberMessagesException(
			"Multiple AST nodes appear to be associated with id '$astNodeId' in gherkinDocument '${feature.uri}'")
	}

	override fun toResult(): DefaultFeatureResults = DefaultFeatureResults(
		feature = feature,
		allChildren = childResults.values.map { it.toResult() }.toList()
	)

	companion object {

		private class ScenarioResultHolder(
			private val result: DefaultScenarioResult
		) : ResultBuilder {
			override fun toResult(): DefaultScenarioResult = result
		}

	}

}

internal class TestRunResultBuilder : ResultBuilder {
	private val featureResults = LinkedHashMap<URI, FeatureResultBuilder>()

	fun getOrCreateFeatureResultBuilder(feature: FeatureInfo): FeatureResultBuilder {
		return featureResults.computeIfAbsent(feature.uri) { FeatureResultBuilder(feature) }
	}

	override fun toResult(): DefaultTestRunResults =
		DefaultTestRunResults(featureResults.values.map { it.toResult() })

}

internal class DocumentInfo(document: Messages.GherkinDocument) {

	val feature = FeatureInfo(
		name = document.feature.name,
		line = document.feature.location.line,
		uri = URI.create(document.uri)
	)

	private val childrenByAstNodeId: Map<String, ResultFactory<FeatureResultBuilder>>
	val stepsByAstNodeId: Map<String, Messages.GherkinDocument.Feature.Step>
	val backgroundsByAstNodeId: Map<String, BackgroundInfo>
	private val scenarioLinesByAstId: Map<String, Int>

	init {
		val stepsById = HashMap<String, Messages.GherkinDocument.Feature.Step>()
		val backgroundsByStepId = HashMap<String, BackgroundInfo>()
		val scenarioLinesById = HashMap<String, Int>()

		fun addSteps(steps: Iterable<Messages.GherkinDocument.Feature.Step>) {
			steps.associateByTo(stepsById) { it.id }
		}

		fun addScenario(scenarios: MutableMap<String, in ScenarioNodeInfo>, scenario: Messages.GherkinDocument.Feature.Scenario) {
			scenarioLinesById[scenario.id] = scenario.location.line
			scenarios[scenario.id] = ScenarioNodeInfo
			addSteps(scenario.stepsList)
		}

		fun addBackground(background: Messages.GherkinDocument.Feature.Background) {
			addSteps(background.stepsList)
			val info = BackgroundInfo(
				name = background.name,
				line = background.location.line
			)
			background.stepsList.associateTo(backgroundsByStepId) { it.id to info }
		}

		fun addRule(rules: MutableMap<String, in RuleNodeInfo>, rule: Messages.GherkinDocument.Feature.FeatureChild.Rule) {
			val children = HashMap<String, ScenarioNodeInfo>()
			for(child in rule.childrenList) {
				when(child.valueCase) {
					RuleChildCase.SCENARIO -> addScenario(children, child.scenario)
					RuleChildCase.BACKGROUND -> addBackground(child.background)
					else -> {/* Do nothing */}
				}
			}
			val info = RuleNodeInfo(rule)
			rules[rule.id] = info
			children.keys.forEach { rules[it] = info }
		}

		val children = HashMap<String, ResultFactory<FeatureResultBuilder>>()

		for(child in document.feature.childrenList) {
			when(child.valueCase) {
				ValueCase.SCENARIO -> addScenario(children, child.scenario)
				ValueCase.BACKGROUND -> addBackground(child.background)
				ValueCase.RULE -> addRule(children, child.rule)
				else -> {/* Do nothing */}
			}
		}

		childrenByAstNodeId = children
		stepsByAstNodeId = stepsById
		backgroundsByAstNodeId = backgroundsByStepId
		scenarioLinesByAstId = scenarioLinesById
	}

	private fun scenarioNotFound(pickle: Messages.Pickle): Nothing {
		throw MalformedCucumberMessagesException(
			"No gherkinDocument node found with id '${pickle.astNodeIdsList[0]}' for pickle '${pickle.id}' in document '${feature.uri}'.")
	}

	fun getResultFactory(pickle: Messages.Pickle): ResultFactory<FeatureResultBuilder> =
		childrenByAstNodeId[pickle.astNodeIdsList[0]] ?: scenarioNotFound(pickle)

	fun createScenarioInfo(pickle: Messages.Pickle) = ScenarioInfo(
		name = pickle.name,
		line = scenarioLinesByAstId[pickle.astNodeIdsList[0]] ?: scenarioNotFound(pickle)
	)

}

internal interface ResultFactory<in T> {
	val rule: RuleInfo?
	fun addResultToBuilder(builder: T, pickleId: String, result: DefaultScenarioResult)
}

internal object ScenarioNodeInfo : ResultFactory<ResultCollector> {

	override fun addResultToBuilder(builder: ResultCollector, pickleId: String, result: DefaultScenarioResult) =
		builder.addResult(pickleId, result)

	override val rule: RuleInfo? = null

}

internal class RuleNodeInfo(
	r: Messages.GherkinDocument.Feature.FeatureChild.Rule
) : ResultFactory<FeatureResultBuilder> {

	private val astNodeId: String = r.id

	override val rule = RuleInfo(
		name = r.name,
		line = r.location.line
	)

	override fun addResultToBuilder(
		builder: FeatureResultBuilder,
		pickleId: String,
		result: DefaultScenarioResult
	) = builder
		.getOrCreateRuleResultBuilder(astNodeId, rule)
		.addResult(pickleId, result)

}

internal class PickleNodeInfo(
	val pickle: Messages.Pickle
) {

	val info = PickleInfo(
		name = pickle.name,
		stepText = pickle.stepsList.map { it.text },
		tags = pickle.tagsList.map { it.name }
	)
}

class MalformedCucumberMessagesException(message: String) : Exception(message)
