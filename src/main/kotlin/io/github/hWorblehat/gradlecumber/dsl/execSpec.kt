@file:Suppress("UnstableApiUsage")
package io.github.hWorblehat.gradlecumber.dsl

import io.github.hWorblehat.gradlecumber.util.NO_ACTION
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.process.JavaExecSpec
import javax.inject.Inject

interface CucumberOptions {

	@get:Nested
	val features: CucumberFeaturesSpec

	@get:Input
	@get:Optional
	val tags: Property<String>

	@get:Nested
	val formats: NamedDomainObjectContainer<CucumberFormatOptions>

	@get:Input
	@get:Optional
	val glue: Property<String>

	@get:Input
	@get:Optional
	val scenarioRegex: Property<String>

	fun format(name: String, config: Action<in CucumberFormatOptions> = NO_ACTION): CucumberFormatOptions

	fun features(config: Action<in CucumberFeaturesSpec>) = config.execute(features)

	fun format(name: String, config: CucumberFormatOptions.() -> Unit): CucumberFormatOptions =
		format(name) { f: CucumberFormatOptions -> f.config() }

	fun features(config: CucumberFeaturesSpec.() -> Unit) =
		features { f: CucumberFeaturesSpec -> f.config() }

	fun fromCucumberOptions(convention: CucumberOptions) {
		features.extends(convention.features)
		tags.set(convention.tags)
		glue.set(convention.glue)
		scenarioRegex.set(convention.scenarioRegex)
		convention.formats.all { format ->
			formats.register(format.name) {
				it.fromFormatOptions(format)
			}
		}
	}

}

interface CucumberExecOptions : CucumberOptions{

	@get:Input
	val allowNonPassingTests: Property<Boolean>

	fun allowNonPassingTests(value: Boolean = true) = allowNonPassingTests.set(value)

}

interface CucumberExecSpec : JavaExecSpec, CucumberExecOptions

internal open class DefaultCucumberOptions @Inject constructor(
	objects: ObjectFactory
): CucumberOptions {

	@get:Nested
	override val features: CucumberFeaturesSpec = objects.newCucumberFeaturesSpec()

	@get:Nested
	override val formats: NamedDomainObjectContainer<CucumberFormatOptions> =
		objects.domainObjectContainer(CucumberFormatOptions::class.java)

	@get:Input
	@get:Optional
	override val tags: Property<String> = objects.property(String::class.java)

	@get:Input
	@get:Optional
	override val glue: Property<String> = objects.property(String::class.java)

	@get:Input
	@get:Optional
	override val scenarioRegex: Property<String> = objects.property(String::class.java)

	override fun format(name: String, config: Action<in CucumberFormatOptions>): CucumberFormatOptions {
		return formats.create(name, config)
	}

}

internal open class DefaultCucumberExecOptions @Inject constructor(
	objects: ObjectFactory,
): DefaultCucumberOptions(objects), CucumberExecOptions {

	@get:Input
	override val allowNonPassingTests: Property<Boolean> = objects.property(Boolean::class.java)
		.convention(false)

}

internal class DefaultCucumberExecSpec(
	private val execSpec: JavaExecSpec,
	private val cucumberOptions: CucumberExecOptions
) : CucumberExecSpec, JavaExecSpec by execSpec, CucumberExecOptions by cucumberOptions
