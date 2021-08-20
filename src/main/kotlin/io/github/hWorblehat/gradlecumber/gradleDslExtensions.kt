package io.github.hWorblehat.gradlecumber

import io.github.hWorblehat.gradlecumber.analysis.MessageFactory
import io.github.hWorblehat.gradlecumber.analysis.ResultChecker
import io.github.hWorblehat.gradlecumber.analysis.ResultInfo
import io.github.hWorblehat.gradlecumber.dsl.CucumberExtension
import io.cucumber.messages.Messages
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler

typealias CucumberResultInfo = ResultInfo
typealias CucumberStepStatus = Messages.TestStepFinished.TestStepResult.Status
typealias CucumberResultChecker = ResultChecker
typealias CucumberResultMessageFactory = MessageFactory

val Project.cucumber: CucumberExtension
	get() = extensions.getByType(CucumberExtension::class.java)

val ConfigurationContainer.cucumber: NamedDomainObjectProvider<Configuration>
	get() = named(CUCUMBER_CONFIGURATION_NAME)



fun DependencyHandler.cucumberJvmLib(library: String, version: String): Dependency =
	create("io.cucumber:$library:$version")

fun DependencyHandler.cucumberJava(version: String): Dependency =
	cucumberJvmLib("cucumber-java", version)

fun DependencyHandler.cucumberJava8(version: String): Dependency =
	cucumberJvmLib("cucumber-java8", version)
