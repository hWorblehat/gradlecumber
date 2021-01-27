package dev.hworblehat.gradlecumber.exec

import dev.hworblehat.gradlecumber.testutil.removeANSISequences
import dev.hworblehat.gradlecumber.testutil.silenceOutput
import dev.hworblehat.gradlecumber.testutil.struct
import dev.hworblehat.gradlecumber.testutil.testGlue
import dev.hworblehat.gradlecumber.testutil.testProject
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.gradle.api.GradleException
import java.io.ByteArrayOutputStream

class CucumberExecOperationsTest : FreeSpec({

	CucumberExecOperations::class.simpleName!! - {

		"can be created by ObjectFactory" {
			val execOps = testProject().objects.newCucumberExecOperations()
			execOps shouldNotBe null
		}

		"when executing cucumber" - {

			val project = testProject().struct {
				addAllFeatureFiles()
			}
			val cexec = project.objects.newCucumberExecOperations()

			"succeeds running passing features" {
				val result = cexec.cucumberExec {
					classpath(testGlue)
					features {
						dir("passingFeatures")
					}
					silenceOutput()
				}

				result.exitValue shouldBe 0
			}

			"adds requested stdout formatter" {
				val baos = ByteArrayOutputStream()

				val result = cexec.cucumberExec {
					classpath(testGlue)
					features {
						dir("passingFeatures")
					}
					format("pretty")
					standardOutput = baos
				}

				result.exitValue shouldBe 0

				val output = String(baos.toByteArray()).removeANSISequences()
//				println(output)
				output shouldContain Regex("Given a precondition\\s+# dummy\\.project\\.Glue")
			}

			"adds requested file formatter" {
				val outFile = project.file("${project.buildDir}/cucumber.ndjson")

				val result = cexec.cucumberExec {
					classpath(testGlue)
					features {
						dir("passingFeatures")
					}
					format("message"){
						outputTo(outFile)
					}
					silenceOutput()
				}

				result.exitValue shouldBe 0

				val output = outFile.readText()
//				println(output)
				output shouldContain "{\"testCaseStarted\":{"
			}

			"succeeds running unimplemented features" {
				val result = cexec.cucumberExec {
					classpath(testGlue)
					features {
						dir("unimplementedFeatures")
					}
					allowNonPassingTests.set(true)
					silenceOutput()
				}

				result.exitValue shouldBe 1
			}

			"succeeds running failing features" {
				val result = cexec.cucumberExec {
					classpath(testGlue)
					features {
						dir("failingFeatures")
					}
					allowNonPassingTests()
					silenceOutput()
				}

				result.exitValue shouldBe 1
			}

			"fails when bad formatter is specified" {

				shouldThrow<GradleException> {
					cexec.cucumberExec {
						classpath(testGlue)
						features {
							dir("passingFeatures")
						}
						format("fghjg").outputToStdOut()
						allowNonPassingTests(true)
						silenceOutput()
					}
				}
			}

			"the glue package can be specified" {
				val correctGlueResult = cexec.cucumberExec {
					classpath(testGlue)
					features {
						dir("passingFeatures")
					}
					glue.set("dummy")
					silenceOutput()
				}

				correctGlueResult.exitValue shouldBe 0

				shouldThrow<GradleException> {
					cexec.cucumberExec {
						classpath(testGlue)
						features {
							dir("passingFeatures")
						}
						glue.set("wrong.pkg") // should cause scenarios to be UNDEFINED, thus failing the run
						silenceOutput()
					}
				}
			}

			"a scenario name regex can be specified" {
				val correctGlueResult = cexec.cucumberExec {
					classpath(testGlue)
					features {
						dir("failingFeatures")
					}
					scenarioRegex.set("\\wassing") // Regex does not match - run should pass with 0 scenarios run
					silenceOutput()
				}

				correctGlueResult.exitValue shouldBe 0

				shouldThrow<GradleException> {
					cexec.cucumberExec {
						classpath(testGlue)
						features {
							dir("failingFeatures")
						}
						scenarioRegex.set("\\wailing") // Regex matches - run should fail on failing scenario
						silenceOutput()
					}
				}
			}

			"a tag expression can be specified" {
				val correctGlueResult = cexec.cucumberExec {
					classpath(testGlue)
					features {
						dir("failingFeatures")
					}
					tags.set("not @Failing") // Expression does not match - run should pass with 0 scenarios run
					silenceOutput()
				}

				correctGlueResult.exitValue shouldBe 0

				shouldThrow<GradleException> {
					cexec.cucumberExec {
						classpath(testGlue)
						features {
							dir("failingFeatures")
						}
						tags.set("@Failing") // Regex matches - run should fail on failing scenario
						silenceOutput()
					}
				}
			}

		}

	}

})