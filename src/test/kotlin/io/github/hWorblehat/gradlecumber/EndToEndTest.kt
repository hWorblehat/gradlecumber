package io.github.hWorblehat.gradlecumber

import io.github.hWorblehat.gradlecumber.testutil.BASE_PLUGIN_ID
import io.github.hWorblehat.gradlecumber.testutil.projectStruct
import io.github.hWorblehat.gradlecumber.testutil.tempdir
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.file.exist
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import java.io.File

class EndToEndTest : FreeSpec({

	"Given a project using the plugin" - {

		val projectDir = tempdir().projectStruct {
			createSettingsFile()

			createBuildFile().writeText("""
					plugins {
						`java`
						id("$BASE_PLUGIN_ID")
					}
					
					import ${GradlecumberPlugin::class.java.`package`.name}.*
					
					repositories {
						jcenter()
					}
					
					val featureTest by cucumber.suites.creating {
						rules { result ->
							if(result.pickle.tags.contains("@failing")) cucumber.OK
							else cucumber.checkResultNotPassedOrSkipped(result)
						}
					}
					
					dependencies {
						cucumber.configurationName(cucumberJava("6.+"))
						"featureTestImplementation"("org.opentest4j:opentest4j:1.2.+")
					}
					
					val strictCheck by tasks.registering {
					
						val msgs = project.tasks
							.named<${CucumberExec::class.simpleName}>(featureTest.cucumberExecTaskName)
							.flatMap { it.formatDestFile("message") }
							
						inputs.file(msgs)
					
						doLast {
							println("Checking cucumber results strictly")
							cucumber.checkCucumberResults {
								messages.fileProvider(msgs)
							}
						}
					}
					
					val checkNoFeatureTestsExist by tasks.registering(${io.github.hWorblehat.gradlecumber.CucumberCheckResults::class.simpleName}::class) {
						messages.fileProvider(project.tasks
							.named<${CucumberExec::class.simpleName}>(featureTest.cucumberExecTaskName)
							.flatMap { it.formatDestFile("message") }
						)
						
						rules { "Test exists when it shouldn't" }
					}
				""".trimIndent())

			createFile("src/main/java/pkg/MyClass.java").writeText("""
				package pkg;
				
				public class MyClass {
					
					private boolean somethingHasBeenDone = false;
					
					public void doSomething() {
						somethingHasBeenDone = true;
					}
					
					public boolean hasSomethingBeenDone() {
						return somethingHasBeenDone;
					}
					
				}
			""".trimIndent())

			createFile("src/featureTest/java/glue/Glue.java").writeText("""
				package glue;
				
				import pkg.MyClass;
				import org.opentest4j.AssertionFailedError;
				import io.cucumber.java.en.*;
				
				public class Glue {
				
					private MyClass testSubject = null;
				
					@Given("MyClass")
					public void givenMyClass() {
						testSubject = new MyClass();
					}
					
					@When("it does something")
					public void whenItDoesSomething() {
						testSubject.doSomething();
					}
					
					@When("nothing is done")
					public void whenNothingIsDone() {
						// do nothing 
					}
					
					@Then("something has been done")
					public void somethingHasBeenDone() {
						if(!testSubject.hasSomethingBeenDone()) {
							throw new AssertionFailedError("Nothing had been done.");
						}
					}
				
				}
			""".trimIndent())

			createFile("src/featureTest/gherkin/pkg/MyClass.feature").writeText("""
				Feature: My Class
				
					Scenario: MyClass remembers when something has been done
						Given MyClass
						When it does something
						Then something has been done
						
					@failing
					Scenario: MyClass remembers when nothing has been done
						Given MyClass
						When nothing is done
						Then something has been done
						
			""".trimIndent())
		}

		val gradleRunner = GradleRunner.create()
			.withPluginClasspath()
			.withProjectDir(projectDir)

		"running 'build' includes the cucumber tests" {
			val result = shouldNotThrowAny { gradleRunner.withArguments("build").build() }

			result.taskPaths(SUCCESS) shouldContainInOrder listOf(
				":classes",
				":cucumberFeatureTest",
				":checkCucumberResultsFeatureTest"
			)

			File(projectDir, "build/cucumberMessages/featureTest/featureTest.ndjson") should exist()
		}

		"running ad-hoc checkCucumberResults works as expected" {
			val result = shouldNotThrowAny { gradleRunner.withArguments("strictCheck").buildAndFail() }

			result.task(":strictCheck").let {
				it shouldNotBe null
				it?.outcome shouldBe FAILED
			}
			result.output shouldContain "Step FAILED:"
			result.output shouldContain "Scenario: MyClass remembers when nothing has been done"
		}

		"running manually defined ${io.github.hWorblehat.gradlecumber.CucumberCheckResults::class.simpleName} task triggers the referenced ${CucumberExec::class.simpleName} task" {
			val result = shouldNotThrowAny { gradleRunner.withArguments("clean", "checkNoFeatureTestsExist").buildAndFail() }

			result.task(":cucumberFeatureTest").let {
				it shouldNotBe null
				it?.outcome shouldBe SUCCESS
			}

			result.task(":checkNoFeatureTestsExist").let {
				it shouldNotBe null
				it?.outcome shouldBe FAILED
			}

			result.output shouldContain "Test exists when it shouldn't"
		}

		"running a subsequent 'check' doesn't rerun up-to-date cucumber checks" {
			val result = shouldNotThrowAny { gradleRunner.withArguments("check").build() }

			result.task(":featureTestClasses")!!.outcome shouldBe UP_TO_DATE
			result.task(":cucumberFeatureTest")!!.outcome shouldBe UP_TO_DATE
			result.task(":checkCucumberResultsFeatureTest")!!.outcome shouldBe SUCCESS
		}

	}

})