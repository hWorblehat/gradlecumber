package dev.hworblehat.gradlecumber

import dev.hworblehat.gradlecumber.analysis.ResultChecker
import dev.hworblehat.gradlecumber.dsl.CucumberExtension
import dev.hworblehat.gradlecumber.dsl.cucumberCheckResultsTaskName
import dev.hworblehat.gradlecumber.dsl.cucumberExecTaskName
import dev.hworblehat.gradlecumber.dsl.cucumberLifecycleTaskName
import dev.hworblehat.gradlecumber.testutil.BASE_PLUGIN_ID
import dev.hworblehat.gradlecumber.testutil.projectStruct
import dev.hworblehat.gradlecumber.testutil.tempdir
import dev.hworblehat.gradlecumber.testutil.testProject
import dev.hworblehat.gradlecumber.util.sourceSets
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SKIPPED

@Suppress("UnstableApiUsage")
class GradlecumberPluginTest : FreeSpec({

	GradlecumberPlugin::class.simpleName!! - {

		"Applying the plugin creates associated model objects" - {
			val project = testProject()
			project.plugins.apply(GradlecumberPlugin::class.java)

			"the 'cucumber' extension" {
				val ext = project.extensions.getByName("cucumber")

				ext shouldNotBe null
				ext should beInstanceOf<CucumberExtension>()
			}

			"a 'cucumber' dependency configuration" {
				val conf = project.configurations.cucumber

				conf shouldNotBe null
			}
		}

		"Plugin can be applied by ID" {
			val project = testProject()
			project.plugins.apply(BASE_PLUGIN_ID)

			shouldNotThrow<Exception> {
				project.extensions.getByName("cucumber")
			}
		}

		"Adding a suite creates corresponding Gradle model objects" - {

			val project = testProject()
			project.plugins.apply(GradlecumberPlugin::class.java)

			project.cucumber.suites.register("testSuite")

			"a source set that" - {

				"exists" {
					shouldNotThrow<Exception> {
						project.sourceSets.getByName("testSuite")
					}
				}

				"has an 'implementation' configuration that extends the 'cucumber' configuration" {
					project.dependencies.apply {
						add(project.cucumber.configurationName, cucumberJava("6.+"))
					}

					val deps = project.configurations.getByName("testSuiteImplementation").allDependencies

					deps.forOne { dep ->
						dep.group shouldBe "io.cucumber"
						dep.name shouldBe "cucumber-java"
					}
				}
			}

			"a ${CucumberExec::class.simpleName} task with" - {
				val task = project.tasks.named(cucumberExecTaskName("testSuite"), CucumberExec::class.java).get()

				"a defined features directory" {
					task.features.args.get() shouldContainExactly listOf("${project.projectDir.absolutePath}/src/testSuite/gherkin")
				}

				"a message formatter" {
					val format = task.formats.named("message").get()

					format.pluginName.get() shouldBe "message"
					format.destFile.isPresent shouldBe true
				}
			}

			"a lifecycle task" {
				val lifecycle = project.tasks.named(cucumberLifecycleTaskName("testSuite")).get()

				lifecycle.actions should beEmpty()
				lifecycle.group shouldBe LifecycleBasePlugin.VERIFICATION_GROUP
			}

		}

		"Adding rules to a suite splits apart execution and analysis" - {

			val project = testProject()
			project.plugins.apply(GradlecumberPlugin::class.java)

			val rules = ResultChecker { "Dummy" }

			project.cucumber.suites.register("testSuite") {
				it.rules(rules)
			}

			"the ${CucumberExec::class.simpleName} task will not fail if tests don't pass" {
				val task = project.tasks.named(cucumberExecTaskName("testSuite"), CucumberExec::class.java).get()
				task.allowNonPassingTests.get() shouldBe true
			}

			"a ${CucumberCheckResults::class.simpleName} task is added with" - {
				val task = project.tasks.named(cucumberCheckResultsTaskName("testSuite"), CucumberCheckResults::class.java).get()

				"a defined messages input file" {
					task.messages.isPresent shouldBe true
				}

				"the rules that were specified on the suite" {
					task.rules.get() shouldBe rules
				}
			}

		}

		"A test suite's configuration is passed on to its corresponding tasks" - {

			val project = testProject()
			project.plugins.apply(GradlecumberPlugin::class.java)

			"adding a formatter" {
				project.cucumber.suites.register("extraFormat") {
					it.format("dave") {
						pluginName.set("pretty")
					}
				}

				val task = project.tasks.named(cucumberExecTaskName("extraFormat"), CucumberExec::class.java).get()
				val format = task.formats.named("dave").get()

				format.pluginName.get() shouldBe "pretty"
				format.destURI.isPresent shouldBe false
			}

			"adding features" {
				project.cucumber.suites.register("extraFeatures") {
					it.features {
						rerun("rerun.txt")
					}
				}

				val task = project.tasks.named(cucumberExecTaskName("extraFeatures"), CucumberExec::class.java).get()

				task.features.allFiles shouldContain project.file("rerun.txt")
				task.features.args.get() shouldContain "@${project.file("rerun.txt").absolutePath}"
			}

			"adding rules inputs" {

				project.cucumber.suites.register("rulesInputs") {
					it.rules {
						project.cucumber.OK
					}
					it.rulesInputProperties.put("foo", "bar")
					it.rulesInputFiles.from("spec.txt")
				}

				val task = project.tasks.named(cucumberCheckResultsTaskName("rulesInputs"), CucumberCheckResults::class.java).get()

				task.rulesInputProperties.get() shouldContain ("foo" to "bar")
				task.rulesInputFiles shouldContain project.file("spec.txt")
			}

		}

		"Adding the 'java' plugin causes suites to inherit the 'main' source set" {
			val project = testProject()
			project.plugins.apply(GradlecumberPlugin::class.java)

			val suite = project.cucumber.suites.create("dummySuite")

			project.plugins.apply("java")

			suite.inheritsSourceSet shouldContain project.sourceSets.getByName("main")
		}

	}

	"${GradlecumberPlugin::class.simpleName} Task Dependencies" - {

		"Given a project containing a suite without custom rules" - {

			val projectDir = tempdir().projectStruct {
				createSettingsFile()

				createBuildFile().writeText("""
					plugins {
						id("$BASE_PLUGIN_ID")
					}
					
					import ${GradlecumberPlugin::class.java.`package`.name}.*
					
					val featureTest by cucumber.suites.registering

					// Disable tasks, so they just show as skipped
					tasks.all {
						enabled = false
					}
				""".trimIndent())
			}

			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(projectDir)

			"executing cucumber depends on building the corresponding source set" {
				val tasks = gradleRunner.withArguments("cucumberFeatureTest").build().taskPaths(SKIPPED)

				tasks shouldContainInOrder listOf(":featureTestClasses", ":cucumberFeatureTest")
			}

			"cucumber lifecycle depends on executing cucumber" {
				val tasks = gradleRunner.withArguments("--stacktrace", "featureTest").build().taskPaths(SKIPPED)

				tasks shouldContainInOrder listOf(":cucumberFeatureTest", ":featureTest")
			}

			"check depends on cucumber lifecycle" {
				val tasks = gradleRunner.withArguments("check").build().taskPaths(SKIPPED)

				tasks shouldContainInOrder listOf(":featureTest", ":check")
			}
		}

		"Given a project containing a suite with custom rules" - {

			val projectDir = tempdir().projectStruct {
				createSettingsFile()

				createBuildFile().writeText("""
					plugins {
						id("$BASE_PLUGIN_ID")
					}
					
					import ${GradlecumberPlugin::class.java.`package`.name}.*
					
					val featureTest by cucumber.suites.registering {
						rules { "Dummy" }
					}

					// Disable tasks, so they just show as skipped
					tasks.all {
						enabled = false
					}
				""".trimIndent())
			}

			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(projectDir)

			"checking results depends on executing cucumber" {
				val tasks = gradleRunner.withArguments("checkCucumberResultsFeatureTest").build().taskPaths(SKIPPED)

				tasks shouldContainInOrder listOf(":cucumberFeatureTest", ":checkCucumberResultsFeatureTest")
			}

			"cucumber lifecycle depends on checking results" {
				val tasks = gradleRunner.withArguments("featureTest").build().taskPaths(SKIPPED)

				tasks shouldContainInOrder listOf(":checkCucumberResultsFeatureTest", ":featureTest")
			}
		}

		"Given a project containing a suite that inherits another source set" - {
			val projectDir = tempdir().projectStruct {
				createSettingsFile()

				createBuildFile().writeText("""
					plugins {
						id("$BASE_PLUGIN_ID")
					}
					
					import ${GradlecumberPlugin::class.java.`package`.name}.*
					
					val foo by sourceSets.creating
					val bar by sourceSets.creating
					
					dependencies {
						"fooRuntimeOnly"(bar.output)
					}
					
					val featureTest by cucumber.suites.registering {
						inheritsSourceSet.add(foo)
					}

					// Disable tasks, so they just show as skipped
					tasks.all {
						enabled = false
					}
				""".trimIndent())
			}

			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(projectDir)

			"compiling the suite depends on compiling the other source set" {
				val tasks = gradleRunner.withArguments("compileFeatureTestJava").build().taskPaths(SKIPPED)

				tasks shouldContainInOrder listOf(":fooClasses", ":compileFeatureTestJava")
				tasks shouldNotContain ":barClasses"
			}

			"running cucumber depends on compiling the other source set's runtime dependencies" {
				val tasks = gradleRunner.withArguments("cucumberFeatureTest").build().taskPaths(SKIPPED)

				tasks shouldContainInOrder listOf(":barClasses", ":cucumberFeatureTest")
			}
		}

		"Given a project applying the 'java' plugin and containing a suite " - {
			val projectDir = tempdir().projectStruct {
				createSettingsFile()

				createBuildFile().writeText("""
					plugins {
						`java`
						id("$BASE_PLUGIN_ID")
					}
					
					import ${GradlecumberPlugin::class.java.`package`.name}.*
					
					val featureTest by cucumber.suites.registering

					// Disable tasks, so they just show as skipped
					tasks.all {
						enabled = false
					}
				""".trimIndent())
			}

			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(projectDir)

			"compiling the suite depends on compiling the other main set" {
				val tasks = gradleRunner.withArguments("compileFeatureTestJava").build().taskPaths(SKIPPED)

				tasks shouldContainInOrder listOf(":classes", ":compileFeatureTestJava")
			}
		}

		"Given a project applying the 'java' plugin, containing a suite explicitly set not to inherit from other source sets" - {
			val projectDir = tempdir().projectStruct {
				createSettingsFile()

				createBuildFile().writeText("""
					plugins {
						`java`
						id("$BASE_PLUGIN_ID")
					}
					
					import ${GradlecumberPlugin::class.java.`package`.name}.*
					
					val featureTest by cucumber.suites.registering {
						inheritsSourceSet.clear()
					}

					// Disable tasks, so they just show as skipped
					tasks.all {
						enabled = false
					}
				""".trimIndent())
			}

			val gradleRunner = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(projectDir)

			"running cucumber does not depend on compiling main" {
				val tasks = gradleRunner.withArguments("--stacktrace", "cucumberFeatureTest").build().taskPaths(SKIPPED)

				tasks shouldNotContain ":classes"
			}
		}

	}

})
