package io.github.hWorblehat.gradlecumber.analysis

import io.github.hWorblehat.gradlecumber.testutil.openMessageSample
import io.cucumber.messages.Messages.TestStepFinished.TestStepResult.Status.FAILED
import io.cucumber.messages.Messages.TestStepFinished.TestStepResult.Status.UNDEFINED
import io.kotest.core.spec.style.FreeSpec
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

class AnalyseMessagesTest : FreeSpec({

	"analyseMessages() returns results which" - {

		"given a single passing scenario" - {
			val result = openMessageSample("singlePassingScenario").use { analyseMessages(it) }

			"contains no errors" {
				result.problemCount shouldBe 0
			}

		}

		"given a single failing scenario" - {
			val result = openMessageSample("singleFailingScenario").use { analyseMessages(it) }

//			result.appendTo(System.out)

			"contains a single error" {
				result.problemCount shouldBe 1
				result.features shouldHaveSize 1
				result.features[0].scenarios shouldHaveSize 1
			}

			val feature = result.features[0]

			"references the correct feature" {
				feature.feature.name shouldBe "failing"
			}

			val error = feature.scenarios[0]

			"references the correct scenario" {
				error.scenario.name shouldBe "failing"
			}

			"references the correct step" {
				val step = error.step
				step should beInstanceOf(StepInfo::class)
				if(step is StepInfo) {
					step.text shouldBe "it fails"
				}
			}

			"has the correct status" {
				error.status shouldBe FAILED
			}

		}

		"given a single unimplemented scenario" - {
			val result = openMessageSample("singleUnimplementedScenario").use { analyseMessages(it) }

//			result.appendTo(System.out)

			"contains a single error" {
				result.problemCount shouldBe 1
				result.features shouldHaveSize 1
				result.features[0].scenarios shouldHaveSize 1
			}

			"has the correct status" {
				result.features[0].scenarios[0].status shouldBe UNDEFINED
			}

		}

		"given multiple features & scenarios" - {
			val result = openMessageSample("1Failing1Passing1Unimplemented").use { analyseMessages(it) }

//			result.appendTo(System.out)

			"contains the correct number of error" {
				result.problemCount shouldBe 2
			}

			"references the correct number of features" {
				result.features shouldHaveSize 2
			}

			"associates errors with the correct features and scenarios" {
				result.features.forAll {
					it.scenarios shouldHaveSize 1
				}

				result.features.forOne {
					it.feature.name shouldBe "failing"
					val s = it.scenarios[0]
					s.scenario.name shouldBe "failing"
					s.status shouldBe FAILED
				}

				result.features.forOne {
					it.feature.name shouldBe "unimplemented"
					val s = it.scenarios[0]
					s.scenario.name shouldBe "unimplemented"
					s.status shouldBe UNDEFINED
				}
			}
		}

		"given a feature containing rules" - {
			val result = openMessageSample("2RulesWith1ErrorInEach").use { analyseMessages(it) }

//			result.appendTo(System.out)

			"contains the correct number of errors" {
				result.problemCount shouldBe 2
				result.features shouldHaveSize 1
			}

			val feature = result.features[0]

			"references the correct number of rules" {
				feature.allChildren shouldHaveSize 2
				feature.rules shouldHaveSize 2
				feature.scenarios should beEmpty()
			}

			"associates errors with the correct rules and scenarios" {
				feature.rules.forAll {
					it.scenarios shouldHaveSize 1
				}

				feature.rules.forOne {
					it.rule.name shouldBe "Rules should be included"
					val s = it.scenarios[0]
					s.scenario.name shouldBe "This should fail"
					s.status shouldBe FAILED
				}

				feature.rules.forOne {
					it.rule.name shouldBe "Multiple rules are allowed"
					val s = it.scenarios[0]
					s.scenario.name shouldBe "This is unimplemented"
					s.status shouldBe UNDEFINED
				}
			}
		}

		"given rules with background steps" - {
			val result = openMessageSample("2RulesWithBackground").use { analyseMessages(it) }

//			result.appendTo(System.out)

			"contains the correct number of errors" {
				result.problemCount shouldBe 2
				result.features shouldHaveSize 1
			}

			val feature = result.features[0]

			"references the correct number of rules" {
				feature.allChildren shouldHaveSize 2
				feature.rules shouldHaveSize 2
				feature.scenarios should beEmpty()
			}

			"references the background when a background step fails" {
				feature.rules.forOne {
					it.rule.name shouldBe("backstories can fail")
					val s = it.scenarios[0]
					s.scenario.name shouldBe "pre-failure"
					val step = s.step
					step should beInstanceOf(BackgroundStepInfo::class)
					if(step is BackgroundStepInfo) {
						step.background.name shouldBe "bad backstory"
					}
				}
			}

			"references the scenario when a scenario step fails" {
				feature.rules.forOne {
					it.rule.name shouldBe("backstories can succeed")
					val s = it.scenarios[0]
					s.scenario.name shouldBe "failure"
					val step = s.step
					step.type shouldBe TestCaseStepType.STEP
				}
			}
		}

		"given a scenario with step variables" - {
			val result = openMessageSample("1ScenarioWithVariables").use { analyseMessages(it) }

//			result.appendTo(System.out)

			"contains a single error" {
				result.problemCount shouldBe 1
				result.features shouldHaveSize 1
				result.features[0].scenarios shouldHaveSize 1
			}

			val error = result.features[0].scenarios[0]

			"references the correct step" {
				(error.step as StepInfo).text shouldBe "3 equals 4"
			}

			"has the correct status" {
				error.status shouldBe FAILED
			}
		}

		"given a scenario outline" - {
			val result = openMessageSample("scenarioOutlineWith3Examples").use { analyseMessages(it) }

//			result.appendTo(System.out)

			"contains the correct number of errors error" {
				result.problemCount shouldBe 2
				result.features shouldHaveSize 1
				result.features[0].scenarios shouldHaveSize 2
			}

			"contains the expected errors" {
				result.features[0].scenarios.forOne {
					it.scenario.name shouldBe "Outline var"
					(it.step as StepInfo).text shouldBe "3 equals 4"
				}
				result.features[0].scenarios.forOne {
					it.scenario.name shouldBe "Outline baz"
					(it.step as StepInfo).text shouldBe "123 equals 3231"
				}
			}
		}

		"given a scenario containing a data table" - {
			val result = openMessageSample("scenarioWithDataTable").use { analyseMessages(it) }

//			result.appendTo(System.out)

			"contains a single error" {
				result.problemCount shouldBe 1
			}

			"references the correct step" {
				(result.features[0].scenarios[0].step as StepInfo).text shouldBe "this table fails:"
			}
		}

		"given a scenario with a failing hook" - {
			val result = openMessageSample("scenarioWithFailingHook").use { analyseMessages(it) }

//			result.appendTo(System.out)

			"contains a single error" {
				result.problemCount shouldBe 1
			}

			"references the failing hook" {
				val scenario = result.features[0].scenarios[0]
				scenario.scenario.name shouldBe "Hook"
				scenario.step.type shouldBe TestCaseStepType.HOOK
			}
		}

	}

})