package io.github.hWorblehat.gradlecumber.exec

import io.github.hWorblehat.gradlecumber.testutil.readMessageSample
import io.kotest.core.spec.style.FreeSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe

class CompletedTestRunSpec : FreeSpec({

	"textIndicatesCompletedTestRun()" - {

		"should return true when given" - {

			"a 'testRunFinished' message" {
				val message = "{\"testRunFinished\":{\"success\":true,\"timestamp\":{\"seconds\":\"1610919656\",\"nanos\":164778000}}}"
				textIndicatesCompletedTestRun(message) shouldBe true
			}

			"text ending in a 'testRunFinished' message" {

				val message = """
					1000}},"timestamp":{"seconds":"1610919656","nanos":149795000},"testStepId":"428c2c58-6ec9-4f73-8ef5-1e044559049c","testCaseStartedId":"3814005c-bc98-4fb1-9bce-03b49c92bb94"}}
					{"testStepStarted":{"timestamp":{"seconds":"1610919656","nanos":158443000},"testStepId":"789ba33b-83ae-44ac-b981-74eb0c264247","testCaseStartedId":"3814005c-bc98-4fb1-9bce-03b49c92bb94"}}
					{"testStepFinished":{"testStepResult":{"status":"PASSED","duration":{"nanos":339000}},"timestamp":{"seconds":"1610919656","nanos":158782000},"testStepId":"789ba33b-83ae-44ac-b981-74eb0c264247","testCaseStartedId":"3814005c-bc98-4fb1-9bce-03b49c92bb94"}}
					{"testStepStarted":{"timestamp":{"seconds":"1610919656","nanos":159282000},"testStepId":"3bd27294-7439-440e-a337-88685370b12b","testCaseStartedId":"3814005c-bc98-4fb1-9bce-03b49c92bb94"}}
					{"testStepFinished":{"testStepResult":{"status":"PASSED","duration":{"nanos":285000}},"timestamp":{"seconds":"1610919656","nanos":159567000},"testStepId":"3bd27294-7439-440e-a337-88685370b12b","testCaseStartedId":"3814005c-bc98-4fb1-9bce-03b49c92bb94"}}
					{"testCaseFinished":{"timestamp":{"seconds":"1610919656","nanos":161155000},"testCaseStartedId":"3814005c-bc98-4fb1-9bce-03b49c92bb94"}}
					{"testRunFinished":{"success":true,"timestamp":{"seconds":"1610919656","nanos":164778000}}}
				""".trimIndent()

				textIndicatesCompletedTestRun(message) shouldBe true

			}

		}

		"should return false when given" - {

			"an empty string" {
				textIndicatesCompletedTestRun("") shouldBe false
			}

			"a non-'testRunFinished' message" {
				val text = "{\"testCaseFinished\":{\"timestamp\":{\"seconds\":\"1610919656\",\"nanos\":161155000},\"testCaseStartedId\":\"3814005c-bc98-4fb1-9bce-03b49c92bb94\"}}"
				textIndicatesCompletedTestRun(text) shouldBe false
			}

			"a non-JSON string" {
				val text = "the quick brown fox jums over the lazy dog"
				textIndicatesCompletedTestRun(text) shouldBe false

				val text2 = "$text {\"testRunFinished\":"
				textIndicatesCompletedTestRun(text2) shouldBe false
			}

			"a non-message JSON string" {
				val text = "{\"bar\":{\"testRunFinished\": \"foo\"}}"
				textIndicatesCompletedTestRun(text) shouldBe false
			}

			"a malformed 'testRunFinished' message" {
				val text = "{\"testRunFinished\":{\"success\":true,\"timestamp\":{\"seconds\":\"1610"
				textIndicatesCompletedTestRun(text) shouldBe false
			}

		}

	}

	"fileIndicatesCompletedTestRun()" - {

		"should return true when given" - {

			"a complete messages file" {
				val text = readMessageSample("singlePassingScenario")

				val file = tempfile()
				file.writeText(text)

				fileIndicatesCompletedTestRun(file) shouldBe true
			}

			"a small complete messages file" {
				val text = """
					{"meta":{"protocolVersion":"13.2.1","implementation":{"name":"cucumber-jvm","version":"6.9.1"},"runtime":{"name":"OpenJDK 64-Bit Server VM","version":"11.0.10+8"},"os":{"name":"Linux"},"cpu":{"name":"amd64"}}}
					{"testRunFinished":{"success":true,"timestamp":{"seconds":"1610968977","nanos":699533000}}}
				""".trimIndent()

				val file = tempfile()
				file.writeText(text)

				fileIndicatesCompletedTestRun(file) shouldBe true
			}

		}

		"should return false when given" - {

			"a nonexistent file" {
				val file = tempfile()
				file.delete()

				fileIndicatesCompletedTestRun(file) shouldBe false
			}

			"an empty file" {
				fileIndicatesCompletedTestRun(tempfile()) shouldBe false
			}

			"a non-UTF-8 file" {
				val file = tempfile()
				val b = 0xFF.toByte()
				val bytes = ByteArray(128) { b }
				file.writeBytes(bytes)

				fileIndicatesCompletedTestRun(tempfile()) shouldBe false
			}

		}

	}

})