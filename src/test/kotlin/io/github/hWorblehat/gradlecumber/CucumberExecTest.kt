package io.github.hWorblehat.gradlecumber

import io.github.hWorblehat.gradlecumber.testutil.pluginClasspathKts
import io.github.hWorblehat.gradlecumber.testutil.projectStruct
import io.github.hWorblehat.gradlecumber.testutil.silenceOutput
import io.github.hWorblehat.gradlecumber.testutil.struct
import io.github.hWorblehat.gradlecumber.testutil.tempdir
import io.github.hWorblehat.gradlecumber.testutil.testGlue
import io.github.hWorblehat.gradlecumber.testutil.testGlueKts
import io.github.hWorblehat.gradlecumber.testutil.testProject
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.file.exist
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.api.GradleException
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import java.io.File

@Suppress("UnstableApiUsage")
class CucumberExecTest : FreeSpec({

	"${CucumberExec::class.simpleName} task" - {

		"can be run" {
			val project = testProject().struct { addUnimplementedFeatures() }

			val task = project.tasks.create("cucumber", CucumberExec::class.java)
			with(task) {
				classpath(testGlue)
				features.dir("unimplementedFeatures")
				format("json")
				allowNonPassingTests()

				silenceOutput()
			}

			task.exec()

			task.executionResult.get().exitValue shouldBe 1
		}

		"should fail if cucumber fails" {
			val project = testProject().struct { addUnimplementedFeatures() }

			val task = project.tasks.create("cucumber", CucumberExec::class.java)
			with(task) {
				classpath(testGlue)
				features.dir("unimplementedFeatures")
				format("bad formatter name")
				allowNonPassingTests()

				silenceOutput()
			}

			shouldThrow<GradleException> {
				task.exec()
			}

		}

		"can be run as part of a build" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				addPassingFeatures()

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberExec::class.qualifiedName}>("cucumberExec") {
						classpath($testGlueKts)
						features.dir("passingFeatures")
					}
				""".trimIndent())
			}

			val result = shouldNotThrowAny {
				gradleRunner.withProjectDir(projectDir).withArguments("--stacktrace", "cucumberExec").build()
			}

			result.task(":cucumberExec")?.outcome shouldBe SUCCESS
		}

		"can be modified using command line options" - {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				addFailingFeatures()

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					// As-is, this task will fail as its running failing tests
					tasks.register<${CucumberExec::class.qualifiedName}>("cucumberExec") {
						classpath($testGlueKts)
						features.dir("failingFeatures")
					}
				""".trimIndent())
			}

			gradleRunner.withProjectDir(projectDir)

			"scenario name regex with --scenario-name" {
				val result = shouldNotThrowAny {
					gradleRunner.withArguments("cucumberExec", "--scenario-name", "pail.ng").build()
				}
				result.task(":cucumberExec")!!.outcome shouldBe SUCCESS
			}

			"tag expressions with --tags" {
				val result = shouldNotThrowAny {
					gradleRunner.withArguments("cucumberExec", "--tags", "not @Failing").build()
				}
				result.task(":cucumberExec")!!.outcome shouldBe SUCCESS
			}

			"options are documented in help" {
				val result = shouldNotThrowAny {
					gradleRunner.withArguments("help", "--task", "cucumberExec").build()
				}
				result.output shouldContain "--scenario-name"
				result.output shouldContain "--tags"
			}
		}

		"is skipped when no feature files are specified" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberExec::class.qualifiedName}>("cucumberExec") {
						classpath($testGlueKts)
						features.dir("features") // This folder does not exist
					}
				""".trimIndent())
			}

			val result = shouldNotThrowAny {
				gradleRunner.withProjectDir(projectDir).withArguments("cucumberExec").build()
			}

			result.task(":cucumberExec")!!.outcome shouldBe NO_SOURCE
		}

		"is not skipped when features from the classpath are specified" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberExec::class.qualifiedName}>("cucumberExec") {
						classpath($testGlueKts)
						features.classpath("features/feat.feature")
					}
				""".trimIndent())
			}

			val result = shouldNotThrowAny {
				gradleRunner.withProjectDir(projectDir).withArguments("cucumberExec").buildAndFail()
			}

			result.task(":cucumberExec")!!.outcome shouldBe FAILED
		}

		"should depend on its feature files" {

			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				addPassingFeatures()

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					val prepareFeatures by tasks.registering(Copy::class) {
						destinationDir = file("${'$'}buildDir/features")
						from("passingFeatures")
					}
					
					tasks.register<${CucumberExec::class.qualifiedName}>("cucumberExec") {
						classpath($testGlueKts)
						features.files(prepareFeatures)
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner
				.withProjectDir(projectDir)
				.withArguments("--stacktrace", "cucumberExec")
				.build()

//			println(gradleRun.output)
			gradleRun.taskPaths(SUCCESS) shouldContainExactly listOf(":prepareFeatures", ":cucumberExec")

		}

		"should allow other tasks to depend on its formatter outputs" {

			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				addPassingFeatures()

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					val cucumberExec by tasks.registering(${CucumberExec::class.qualifiedName}::class) {
						classpath($testGlueKts)
						features.dir("passingFeatures")
						format("html").outputTo(file("${'$'}buildDir/cucumber/report.html"))
					}
					
					val gatherReports by tasks.registering(Copy::class) {
						destinationDir = file("reports")
						from(cucumberExec.flatMap { it.formatDestFile("html") })
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner
				.withProjectDir(projectDir)
				.withArguments("--full-stacktrace", "gatherReports")
				.build()

//			println(gradleRun.output)
			gradleRun.taskPaths(SUCCESS) shouldContainExactly listOf(":cucumberExec", ":gatherReports")
			File(projectDir, "reports/report.html") should exist()
		}

		"should be up-to-date if no inputs have changed" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				addPassingFeatures()

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberExec::class.qualifiedName}>("cucumberExec") {
						classpath($testGlueKts)
						features.dir("passingFeatures")
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner
				.withProjectDir(projectDir)
				.withArguments("cucumberExec")

			gradleRun.build() // First run
			val result = gradleRun.build() // Second run

			result.task(":cucumberExec")?.outcome shouldBe UP_TO_DATE
		}

		"should be out-of-date if input files have changed" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				addPassingFeatures("features")

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberExec::class.qualifiedName}>("cucumberExec") {
						classpath($testGlueKts)
						features.dir("features")
						allowNonPassingTests()
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner
				.withProjectDir(projectDir)
				.withArguments("cucumberExec")

			gradleRun.build() // First run

			// Add some more feature files
			projectDir.projectStruct {
				addUnimplementedFeatures("features")
			}

			val result = gradleRun.build() // Second run

			result.task(":cucumberExec")?.outcome shouldBe SUCCESS
		}

		"should be out-of-date if input properties have changed" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				addUnimplementedFeatures("features")

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberExec::class.qualifiedName}>("cucumberExec") {
						classpath($testGlueKts)
						features.dir("features")
						allowNonPassingTests((project.property("allowNonPassing") as String).toBoolean())
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner.withProjectDir(projectDir)

			val result1 = gradleRun.withArguments("-PallowNonPassing=true", "cucumberExec").build()
			result1.task(":cucumberExec")?.outcome shouldBe SUCCESS

			val result2 = gradleRun.withArguments("-PallowNonPassing=true", "cucumberExec").build()
			result2.task(":cucumberExec")?.outcome shouldBe UP_TO_DATE

			val result3 = gradleRun.withArguments("-PallowNonPassing=false", "cucumberExec").buildAndFail()
			result3.task(":cucumberExec")?.outcome shouldBe FAILED
		}

		"should be out-of-date if nested input properties have changed" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				addPassingFeatures("features")

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberExec::class.qualifiedName}>("cucumberExec") {
						classpath($testGlueKts)
						features.dir("features")
						format(project.property("cucumberFormat") as String)
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner.withProjectDir(projectDir)

			val result1 = gradleRun.withArguments("-P", "cucumberFormat=pretty", "cucumberExec").build()
			result1.task(":cucumberExec")?.outcome shouldBe SUCCESS

			val result2 = gradleRun.withArguments("-P", "cucumberFormat=html", "cucumberExec").build()
			result2.task(":cucumberExec")?.outcome shouldBe SUCCESS
		}

		"should be out-of-date if formatter output have changed" {
			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()

			val projectDir = tempdir().projectStruct {
				createSettingsFile()
				addPassingFeatures("features")

				createBuildFile().writeText("""
					buildscript {
						dependencies {
							${pluginClasspathKts(gradleRunner)}
						}
					}
					
					tasks.register<${CucumberExec::class.qualifiedName}>("cucumberExec") {
						classpath($testGlueKts)
						features.dir("features")
						format("message") {
							outputTo(file("cucumber.ndjson"))
						}
					}
				""".trimIndent())
			}

			val gradleRun = gradleRunner.withProjectDir(projectDir)

			val result1 = gradleRun.withArguments("cucumberExec").build()
			result1.task(":cucumberExec")?.outcome shouldBe SUCCESS

			val result2 = gradleRun.withArguments("cucumberExec").build()
			result2.task(":cucumberExec")?.outcome shouldBe UP_TO_DATE

			File(projectDir, "cucumber.ndjson").delete()

			val result3 = gradleRun.withArguments("cucumberExec").build()
			result3.task(":cucumberExec")?.outcome shouldBe SUCCESS
		}

	}



})