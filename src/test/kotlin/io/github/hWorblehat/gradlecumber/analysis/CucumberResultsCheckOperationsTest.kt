package io.github.hWorblehat.gradlecumber.analysis

import io.github.hWorblehat.gradlecumber.CucumberStepStatus
import io.github.hWorblehat.gradlecumber.testutil.struct
import io.github.hWorblehat.gradlecumber.testutil.testProject
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.should
import io.kotest.matchers.types.beInstanceOf
import io.mockk.mockk
import org.slf4j.Logger

class CucumberResultsCheckOperationsTest : FreeSpec({

	val dummyLogger = mockk<Logger>(relaxed = true)

	CucumberResultsCheckOperations::class.simpleName!! - {

		"can be created" {
			val project = testProject()
			val ops = project.objects.newCucumberResultsCheckOperations(dummyLogger)

			ops should beInstanceOf(DefaultCucumberResultsCheckOperations::class)
		}

		"succeeds when given a passing test run" {
			val project = testProject()
				.struct {
					writeMessagesSample("singlePassingScenario", "messages")
				}
			val ops = project.objects.newCucumberResultsCheckOperations(dummyLogger)

			ops.checkCucumberResults {
				messages.set(project.file("messages/singlePassingScenario.ndjson"))
			}
		}

		"throws an exception when given a failing test run" {
			val project = testProject()
				.struct {
					writeMessagesSample("singleFailingScenario", "messages")
				}
			val ops = project.objects.newCucumberResultsCheckOperations(dummyLogger)

			shouldThrow<CucumberResultsException> {
				ops.checkCucumberResults {
					messages.set(project.file("messages/singleFailingScenario.ndjson"))
				}
			}
		}

		"can use custom rules" {
			val project = testProject()
				.struct {
					writeMessagesSample("singlePassingScenario", "messages")
				}
			val ops = project.objects.newInstance(DefaultCucumberResultsCheckOperations::class.java, dummyLogger)

			shouldThrow<CucumberResultsException> {
				ops.checkCucumberResults {
					messages.set(project.file("messages/singlePassingScenario.ndjson"))
					rules {
						if (result.status != CucumberStepStatus.PENDING) {
							"Oh no!"
						} else null
					}
				}
			}
		}

	}

})