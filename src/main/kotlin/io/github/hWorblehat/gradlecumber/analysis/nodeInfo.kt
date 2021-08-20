package io.github.hWorblehat.gradlecumber.analysis

import java.net.URI

abstract class TitleNodeInfo internal constructor (
	val keyword: String,
	val name: String,
	val line: Int
) {
	override fun toString(): String = "$keyword: $name  (line:$line)"
}

class FeatureInfo internal constructor (name: String, line: Int, val uri: URI)
	: TitleNodeInfo("Feature", name, line)

class RuleInfo internal constructor (name: String, line: Int)
	: TitleNodeInfo("Rule", name, line)

class ScenarioInfo internal constructor (name: String, line: Int)
	: TitleNodeInfo("Scenario", name, line)

class BackgroundInfo internal constructor (name: String, line: Int)
	: TitleNodeInfo("Background", name, line)

class PickleInfo(
	val name: String,
	val stepText: List<String>,
	val tags: List<String>
)

enum class TestCaseStepType(private val str: String) {
	HOOK("Hook"),
	STEP("Step"),
	BACKGROUND_STEP("Background Step")
	;

	override fun toString(): String = str
}

sealed class TestCaseStep (val type: TestCaseStepType)

open class StepInfo protected constructor(
	type: TestCaseStepType,
	val keyword: String,
	val text: String,
	val line: Int
) : TestCaseStep(type) {

	internal constructor(keyword: String, text: String, line: Int)
		: this(TestCaseStepType.STEP, keyword, text, line)

	override fun toString(): String = "$keyword$text  (line:$line)"
}

class BackgroundStepInfo internal constructor(
	keyword: String,
	text: String,
	line: Int,
	val background: BackgroundInfo
) : StepInfo(TestCaseStepType.BACKGROUND_STEP, keyword, text, line)

object HookInfo : TestCaseStep (TestCaseStepType.HOOK)