package dev.hworblehat.gradlecumber

import dev.hworblehat.gradlecumber.testutil.pluginClasspathKts
import dev.hworblehat.gradlecumber.testutil.projectStruct
import dev.hworblehat.gradlecumber.testutil.struct
import dev.hworblehat.gradlecumber.testutil.tempdir
import dev.hworblehat.gradlecumber.testutil.testProject
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*

class CucumberCheckResultsTest : FreeSpec({

	"${CucumberCheckResults::class.simpleName} task" - {

		"can be created as part of a project" {
			val project = testProject()
			val task = project.tasks.create("cucumberCheckResults", CucumberCheckResults::class.java)

			task should beInstanceOf(CucumberCheckResults::class)
		}

		"can be executed successfully when given a passing test run" {
			val project = testProject()
				.struct {
					writeMessagesSample("singlePassingScenario", "messages")
				}

			val task = project.tasks.create("cucumberCheckResults", CucumberCheckResults::class.java)
			task.messages.set(project.file("messages/singlePassingScenario.ndjson"))

			task.checkResults()
		}

		"can be run as part of a build" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				writeMessagesSample("singlePassingScenario", "messages")

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberCheckResults::class.qualifiedName}>("checkCucumberResults") {
						messages.set(project.file("messages/singlePassingScenario.ndjson"))
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner
				.withProjectDir(projectDir)
				.withArguments("--stacktrace", "checkCucumberResults")
				.build()

			gradleRun.task(":checkCucumberResults")?.outcome shouldBe SUCCESS
		}

		"should fail when given a failing test run" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				writeMessagesSample("singleFailingScenario", "messages")

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberCheckResults::class.qualifiedName}>("checkCucumberResults") {
						messages.set(project.file("messages/singleFailingScenario.ndjson"))
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner
				.withProjectDir(projectDir)
				.withArguments("--stacktrace", "checkCucumberResults")
				.buildAndFail()

			gradleRun.task(":checkCucumberResults")?.outcome shouldBe FAILED
		}

		"should be up-to-date when the messages file has not changed" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				writeMessagesSample("singlePassingScenario", "messages", "cucumber.ndjson")

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}

					tasks.register<${CucumberCheckResults::class.qualifiedName}>("checkCucumberResults") {
						messages.set(project.file("messages/cucumber.ndjson"))
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner
				.withProjectDir(projectDir)
				.withArguments("--stacktrace", "--info", "checkCucumberResults")

			val result1 = gradleRun.build()
			result1.task(":checkCucumberResults")?.outcome shouldBe SUCCESS

			val result2 = gradleRun.build()
//			println(result2.output)
			result2.task(":checkCucumberResults")?.outcome shouldBe UP_TO_DATE
		}

		"should be out-of-date when the messages file changes" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				writeMessagesSample("singlePassingScenario", "messages", "cucumber.ndjson")

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberCheckResults::class.qualifiedName}>("checkCucumberResults") {
						messages.set(project.file("messages/cucumber.ndjson"))
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner
				.withProjectDir(projectDir)
				.withArguments("--stacktrace", "checkCucumberResults")

			val result1 = gradleRun.build()
			result1.task(":checkCucumberResults")?.outcome shouldBe SUCCESS

			projectDir.projectStruct {
				writeMessagesSample("singleFailingScenario", "messages", "cucumber.ndjson")
			}
			val result2 = gradleRun.buildAndFail()
			result2.task(":checkCucumberResults")?.outcome shouldBe FAILED
		}

		"can use custom rules" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				writeMessagesSample("singlePassingScenario", "messages")

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberCheckResults::class.qualifiedName}>("checkCucumberResults") {
						messages.set(project.file("messages/singlePassingScenario.ndjson"))
						rules {
							if (result.status != ${CucumberStepStatus::class.qualifiedName}.PENDING) {
								"Oh no!"
							} else null
						}
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner
				.withProjectDir(projectDir)
				.withArguments("--stacktrace", "checkCucumberResults")
				.buildAndFail()

			gradleRun.task(":checkCucumberResults")?.outcome shouldBe FAILED
		}

	}

})