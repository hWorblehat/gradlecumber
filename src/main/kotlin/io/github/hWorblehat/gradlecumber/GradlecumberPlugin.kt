@file:Suppress("UnstableApiUsage")
package io.github.hWorblehat.gradlecumber

import io.github.hWorblehat.gradlecumber.analysis.NOT_PASSED_OR_SKIPPED
import io.github.hWorblehat.gradlecumber.dsl.CucumberSuite
import io.github.hWorblehat.gradlecumber.dsl.createCucumberExtension
import io.github.hWorblehat.gradlecumber.util.sourceSets
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import java.io.File

internal const val CUCUMBER_CONFIGURATION_NAME = "cucumber"

class GradlecumberPlugin : Plugin<Project> {
	override fun apply(project: Project) {

	    project.plugins.apply(JavaBasePlugin::class.java)

		val cucumberConfiguration = project.configurations.create(CUCUMBER_CONFIGURATION_NAME) {
			it.isCanBeConsumed = false
			it.isCanBeResolved = false
		}

        val extension = project.extensions.createCucumberExtension(cucumberConfiguration)

        extension.suites.all { suite ->

			val messageFormatName = "message"

			configureDefaults(project, suite, messageFormatName)

			val sourceSet = createSourceSet(project, suite)
			configureConfigurations(project, suite, sourceSet, cucumberConfiguration)

			val hasRules = project.objects.property(Boolean::class.java).convention(false)
			val checkTaskProp: Property<Task> = project.objects.property(Task::class.java)

			val cucumberExec = createCucumberExecTask(project, suite, sourceSet, allowNonPassingTests = hasRules)
			listenForRulesChanges(project, suite,
				messagesFile = cucumberExec.flatMap { it.formatDestFile(messageFormatName) },
				hasRulesProp = hasRules,
				checkResultsTaskProp = checkTaskProp
			)

			createLifecycleTask(project, suite, cucumberExec, checkTaskProp)
        }

		project.plugins.withType(JavaPlugin::class.java) {

			extension.suites.all {
				it.inheritsSourceSet.add(project.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME))
			}

		}

	}
}

private fun configureDefaults(project: Project, suite: CucumberSuite, messageFormatName: String) {
	suite.features {
		dir("src/${suite.name}/gherkin")
	}

	suite.format(messageFormatName) {
		pluginName.set("message")
		destURI.set(project.layout.buildDirectory.map {
			it.file("cucumberMessages/${suite.name}/${suite.name}.ndjson").asFile.toURI()
		})
	}
}

private fun createSourceSet(project: Project, suite: CucumberSuite): SourceSet {
	val inheritSourceSetOutputs = project.provider { suite.inheritsSourceSet }.map {
		it.map(SourceSet::getOutput)
	}

	val sourceSet = project.sourceSets.create(suite.sourceSetName)

	val compileClasspath = project.files()
	compileClasspath.from(inheritSourceSetOutputs)
	compileClasspath.from(project.configurations.named(sourceSet.compileClasspathConfigurationName))

	val runtimeClasspath = project.files()
	runtimeClasspath.from(sourceSet.output)
	runtimeClasspath.from(inheritSourceSetOutputs)
	runtimeClasspath.from(project.configurations.named(sourceSet.runtimeClasspathConfigurationName))

	sourceSet.compileClasspath = compileClasspath
	sourceSet.runtimeClasspath = runtimeClasspath

	return sourceSet
}

@Suppress("DEPRECATION")
private fun configureConfigurations(
	project: Project,
	suite: CucumberSuite,
	sourceSet: SourceSet,
	cucumberConfiguration: Configuration
) {
	project.configurations.named(sourceSet.implementationConfigurationName) { conf ->
		conf.extendsFrom(cucumberConfiguration)
	}

	suite.inheritsSourceSet.whenObjectAdded { inherits ->
		project.configurations.apply {
			extend(sourceSet, inherits) { compileOnlyConfigurationName }
			extend(sourceSet, inherits) { implementationConfigurationName }
			extend(sourceSet, inherits) { runtimeOnlyConfigurationName }
		}
	}

	suite.inheritsSourceSet.whenObjectRemoved { inherits ->
		project.configurations.apply {
			unextend(sourceSet, inherits) { compileOnlyConfigurationName }
			unextend(sourceSet, inherits) { implementationConfigurationName }
			unextend(sourceSet, inherits) { runtimeOnlyConfigurationName }
		}
	}

}

private fun ConfigurationContainer.extend(
	child: SourceSet, extends: SourceSet, getConfigurationName: SourceSet.() -> String
) = getByName(child.getConfigurationName()).extendsFrom(getByName(extends.getConfigurationName()))

private fun ConfigurationContainer.unextend(
	child: SourceSet, extends: SourceSet, getConfigurationName: SourceSet.() -> String
) {
	val childConfiguration = getByName(child.getConfigurationName())
	val extendsConfiguration = getByName(extends.getConfigurationName())
	childConfiguration.setExtendsFrom(childConfiguration.extendsFrom - listOf(extendsConfiguration))
}

private fun createCucumberExecTask(
	project: Project,
	suite: CucumberSuite,
	sourceSet: SourceSet,
	allowNonPassingTests: Provider<out Boolean>
) = project.tasks.register(suite.cucumberExecTaskName, CucumberExec::class.java) { task ->
	task.description = "Runs Cucumber against the '${suite.name}' suite"
	task.fromCucumberOptions(suite)
	task.classpath(sourceSet.runtimeClasspath)
	task.allowNonPassingTests.set(allowNonPassingTests)
}

private fun createCheckResultsTask(project: Project, suite: CucumberSuite, messagesFile: Provider<File>) =
	project.tasks.register(suite.checkResultsTaskName, io.github.hWorblehat.gradlecumber.CucumberCheckResults::class.java) { task ->
		task.description = "Validates the results of running the '${suite.name}' Cucumber suite against the specified rules"
		task.fromRulesSpec(suite)
		task.messages.fileProvider(messagesFile)
	}

private fun createLifecycleTask(project: Project,
	suite: CucumberSuite,
	execTask: Provider<out Task>,
	checkResultsTask: Provider<out Task>
): Provider<Task> {
	val lifecycle = project.tasks.register(suite.lifecycleTaskName) {
		it.group = JavaBasePlugin.VERIFICATION_GROUP
		it.description = "Lifecycle task to run Cucumber and validate the results for the '${suite.name}' suite"
		it.dependsOn(execTask.map { listOf(it) }.orElse(emptyList()))
		it.dependsOn(checkResultsTask.map { listOf(it) }.orElse(emptyList()))
	}

	project.tasks.named(JavaBasePlugin.CHECK_TASK_NAME) {
		it.dependsOn(lifecycle)
	}

	return lifecycle
}

private fun listenForRulesChanges(project: Project, suite: CucumberSuite,
	messagesFile: Provider<File>,
	hasRulesProp: Property<in Boolean>,
	checkResultsTaskProp: Property<in io.github.hWorblehat.gradlecumber.CucumberCheckResults>
) {
	var checkTask: TaskProvider<out io.github.hWorblehat.gradlecumber.CucumberCheckResults>? = null

	suite.whenRulesUpdated { rules ->

		if(rules == NOT_PASSED_OR_SKIPPED) {
			hasRulesProp.set(false)
			if(checkTask!=null) {
				project.tasks.removeIf { it.name == checkTask?.name }
			}
			checkTask = null
			checkResultsTaskProp.set(null as io.github.hWorblehat.gradlecumber.CucumberCheckResults?)
		} else {

			hasRulesProp.set(true)
			if(checkTask==null) {
				checkTask = createCheckResultsTask(project, suite, messagesFile)
				checkResultsTaskProp.set(checkTask!!)
			}

		}
	}
}