package dev.hworblehat.gradlecumber.dsl

import dev.hworblehat.gradlecumber.analysis.ResultChecker
import dev.hworblehat.gradlecumber.testutil.testProject
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify

class CucumberSuiteTest : FreeSpec({

	DefaultCucumberSuite::class.simpleName!! - {

		"notifies observers when rules are updated" {
			val project = testProject()
			val suite = project.objects.newInstance(DefaultCucumberSuite::class.java, "testSuite")

			val observer = mockk<(ResultChecker) -> Unit>(relaxed = true)
			val resultChecker = ResultChecker { "Hello" }

			suite.whenRulesUpdated(observer)
			suite.rules(resultChecker)

			suite.rules.get() shouldBe resultChecker

			verify(exactly = 1) {
				observer(resultChecker)
			}
		}

	}

})