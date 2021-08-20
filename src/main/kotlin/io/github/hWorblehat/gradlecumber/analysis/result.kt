package io.github.hWorblehat.gradlecumber.analysis

import io.cucumber.messages.Messages.TestStepFinished.TestStepResult
import java.lang.IllegalStateException

interface AnalysisResult {
	fun appendTo(out: Appendable)
	val problemCount: Int
}

interface ScenarioResult : AnalysisResult {
	val reason: String
	val scenario: ScenarioInfo
	val step: TestCaseStep
	val status: TestStepResult.Status
	val message: String?
}

interface ScenariosResults {
	val scenarios: List<ScenarioResult>
	val allScenarios: List<ScenarioResult>
}

interface RuleResults : ScenariosResults, AnalysisResult {
	val rule: RuleInfo
}

interface FeatureResults : ScenariosResults, AnalysisResult {
	val feature: FeatureInfo
	val allChildren: List<AnalysisResult>
	val rules: List<RuleResults>
}

interface TestRunResults : AnalysisResult {
	val features: List<FeatureResults>
}

internal abstract class AbstractAnalysisResult : AnalysisResult {

	private val message: String by lazy {
		val sb = StringBuilder()
		appendTo(sb, 0)
		sb.toString()
	}

	internal abstract fun appendTo(out: Appendable, indent: Int)

	override fun appendTo(out: Appendable) {
		out.append(message)
	}

	fun Appendable.appendErrorCount(): Appendable =
		append(problemCount.toString()).append(" bad test ").append(if(problemCount==1) "case" else "cases")

	companion object {

		internal fun Appendable.appendIndent(indent: Int): Appendable {
			repeat(indent) {
				append('\t')
			}
			return this
		}
	}
}

internal class DefaultScenarioResult(
	override val reason: String,
	override val scenario: ScenarioInfo,
	override val step: TestCaseStep,
	override val status: TestStepResult.Status,
	override val message: String?
) : AbstractAnalysisResult(), ScenarioResult {

	override val problemCount: Int = 1

	override fun appendTo(out: Appendable, indent: Int) {

		out.appendIndent(indent).append(reason).append(':').appendLine()
		out.appendIndent(indent+1).append("-> ").append(scenario.toString()).appendLine()
		if(step is StepInfo){
			out.appendIndent(indent+1).append("->   ")
			if(step is BackgroundStepInfo) {
				out.append(step.background.toString()).appendLine()
				out.appendIndent(indent+1).append("->     ")
			}
			out.append(step.toString()).appendLine()
		}
		message?.lineSequence()?.forEach {
			out.appendIndent(indent+1).append(it).appendLine()
		}
	}

}

internal abstract class AbstractScenariosAnalysisResult(
	private val children: List<AbstractAnalysisResult>
) : AbstractAnalysisResult(), ScenariosResults {

	override val problemCount: Int by lazy {
		children.sumBy {
			it.problemCount
		}
	}

	protected fun appendChildren(out: Appendable, indent: Int) {
		children.forEach {
			out.appendLine()
			it.appendTo(out, indent+1)
		}
	}
}

internal class DefaultRuleResults(
	override val rule: RuleInfo,
	override val scenarios: List<DefaultScenarioResult>
) : AbstractScenariosAnalysisResult(scenarios), RuleResults {

	override val allScenarios: List<ScenarioResult> = scenarios

	override fun appendTo(out: Appendable, indent: Int) {
		out.appendIndent(indent).append(rule.toString()).append(" [").appendErrorCount().append("]:").appendLine()
		appendChildren(out, indent)
	}

}

internal class DefaultFeatureResults(
	override val feature: FeatureInfo,
	override val allChildren: List<AbstractAnalysisResult>
) : AbstractScenariosAnalysisResult(allChildren), FeatureResults {

	override val rules: List<RuleResults> by lazy { allChildren.filterIsInstance(RuleResults::class.java) }
	override val scenarios: List<ScenarioResult> by lazy { allChildren.filterIsInstance(ScenarioResult::class.java) }
	override val allScenarios: List<ScenarioResult> by lazy {
		allChildren.flatMap {
			when(it) {
				is RuleResults -> it.allScenarios
				is ScenarioResult -> listOf(it)
				else -> throw IllegalStateException()
			}
		}
	}

	override fun appendTo(out: Appendable, indent: Int) {
		out.appendIndent(indent).append("Gherkin Document: ").append(feature.uri.toString()).appendLine()
		out.appendIndent(indent).append(feature.toString()).append(" [").appendErrorCount().append("]:").appendLine()
		appendChildren(out, indent)
	}

}

internal class DefaultTestRunResults (
	override val features: List<DefaultFeatureResults>
) : AbstractAnalysisResult(), TestRunResults {

	override val problemCount: Int by lazy {
		features.sumBy {
			it.problemCount
		}
	}

	override fun appendTo(out: Appendable, indent: Int) {
		features.forEach {
			it.appendTo(out, indent)
		}
	}

}