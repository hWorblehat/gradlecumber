package io.github.hWorblehat.gradlecumber.dsl

import io.github.hWorblehat.gradlecumber.analysis.CucumberResultsException
import io.github.hWorblehat.gradlecumber.testutil.testGlue
import io.github.hWorblehat.gradlecumber.testutil.silenceOutput
import io.github.hWorblehat.gradlecumber.testutil.struct
import io.github.hWorblehat.gradlecumber.testutil.testProject
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import io.mockk.mockk
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.slf4j.Logger

class CucumberExtensionTest : FreeSpec({

	CucumberExtension::class.simpleName!! - {

		val dummyLogger = mockk<Logger>(relaxed = true)

		fun Project.createConfiguration(): Configuration = project.configurations.create("cucumber")

		"can be created" {
			val project = testProject()
			val ext = project.extensions.createCucumberExtension(project.createConfiguration(), dummyLogger)

			ext shouldNotBe null
			ext should beInstanceOf(CucumberExtension::class)
		}

		"should allow suites to be added" {
			val project = testProject()
			val ext = project.extensions.createCucumberExtension(project.createConfiguration(), dummyLogger)
			val featureTest = ext.suites.create("featureTest")
			featureTest shouldNotBe null
			featureTest should beInstanceOf(CucumberSuite::class)
		}

		"can be used to run cucumber" - {
			val project = testProject().struct {
				addPassingFeatures()
			}
			val cucumberConfiguration = project.createConfiguration()
			val ext = project.extensions.createCucumberExtension(cucumberConfiguration, dummyLogger)

			"with a specified classpath" {
				shouldNotThrow<Exception> {
					ext.cucumberExec {
						features.dir("passingFeatures")
						classpath(testGlue)
						silenceOutput()
					}
				}
			}

			"by automatically using the 'cucumber' classpath" {
				project.dependencies.add(cucumberConfiguration.name, project.files(testGlue))

				shouldNotThrow<Exception> {
					ext.cucumberExec {
						features.dir("passingFeatures")
						silenceOutput()
					}
				}
			}
		}

		"can be used to check results" {
			val project = testProject().struct {
				writeMessagesSample("singleFailingScenario", "messages")
			}
			val ext = project.extensions.createCucumberExtension(project.createConfiguration(), dummyLogger)

			shouldThrow<CucumberResultsException> {
				ext.checkCucumberResults {
					messages.set(project.file("messages/singleFailingScenario.ndjson"))
				}
			}
		}

	}

})