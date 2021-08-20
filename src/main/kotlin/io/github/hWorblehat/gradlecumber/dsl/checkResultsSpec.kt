package io.github.hWorblehat.gradlecumber.dsl

import io.github.hWorblehat.gradlecumber.analysis.NOT_PASSED_OR_SKIPPED
import io.github.hWorblehat.gradlecumber.analysis.ResultCheckError
import io.github.hWorblehat.gradlecumber.analysis.ResultChecker
import io.cucumber.messages.Messages
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import javax.inject.Inject

interface CucumberRulesSpec {

	@get:InputFiles
	val rulesInputFiles: ConfigurableFileCollection

	@get:Input
	val rulesInputProperties: MapProperty<String, Any>

	@get:Internal
	val rules: Provider<out ResultChecker>

	fun rules(checker: ResultChecker)

	fun rules(checkResults: ResultCheckError) = rules(ResultChecker { it.checkResults() })

	fun fromRulesSpec(other: CucumberRulesSpec)

}

interface CucumberResultsAnalysisSpec : CucumberRulesSpec {

	@get:InputFile
	val messages: RegularFileProperty

}

internal open class DefaultCucumberRulesSpec @Inject constructor(
	objects: ObjectFactory
) : CucumberRulesSpec {

	@get:InputFiles
	override val rulesInputFiles: ConfigurableFileCollection = objects.fileCollection()

	@get:Input
	override val rulesInputProperties: MapProperty<String, Any> =
		objects.mapProperty(String::class.java, Any::class.java)

	@get:Internal
	override val rules: Property<ResultChecker> = objects.property(ResultChecker::class.java)
		.convention(NOT_PASSED_OR_SKIPPED)

	override fun rules(checker: ResultChecker) {
		rules.set(checker)
	}

	override fun fromRulesSpec(other: CucumberRulesSpec) {
		rulesInputFiles.from(other.rulesInputFiles)
		rulesInputProperties.putAll(other.rulesInputProperties)
		rules.set(other.rules)
	}

}

internal open class DefaultCucumberResultsAnalysisSpec @Inject constructor(
	objects: ObjectFactory
) : DefaultCucumberRulesSpec(objects), CucumberResultsAnalysisSpec {

	@get:InputFile
	override val messages: RegularFileProperty = objects.fileProperty()

}
