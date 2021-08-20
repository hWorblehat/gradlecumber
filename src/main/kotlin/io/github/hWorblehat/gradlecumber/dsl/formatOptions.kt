package io.github.hWorblehat.gradlecumber.dsl

import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import java.io.File
import java.net.URI
import javax.inject.Inject

open class CucumberFormatOptions @Inject constructor(
	private val name: String,
	objects: ObjectFactory
) : Named {

	@get:Optional
	@get:Input
	val destURI: Property<URI> = objects.property(URI::class.java)

	@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
	@get:Internal
	val destFile: Provider<File> = destURI.map { uri ->
		if (uri.scheme == "file") File(uri) else null
	}

	@get:Internal
	val destinationType get(): CucumberFormatDestinationType = when {
		destFile.isPresent -> CucumberFormatDestinationType.FILE
		destURI.isPresent -> CucumberFormatDestinationType.URL
		else -> CucumberFormatDestinationType.STDOUT
	}

	@Input
	override fun getName(): String = name

	@get:Input
	val pluginName: Property<String> = objects.property(String::class.java).convention(name)

	fun outputToStdOut() {
		destURI.set(null as URI?)
	}

	fun outputTo(dest: URI) {
		destURI.set(dest)
	}

	fun outputTo(dest: File) = outputTo(URI.create("file://${dest.absoluteFile}"))

	fun asArgString(): String = asArgString(pluginName.get(), destURI.orNull)

	fun fromFormatOptions(convention: CucumberFormatOptions) {
		destURI.convention(convention.destURI)
		pluginName.convention(convention.pluginName)
	}

	companion object {

		internal fun asArgString(name: String, dest: URI?): String {
			val argString = StringBuilder(name)
			if(dest!=null) {
				argString.append(':').append(dest)
			}
			return argString.toString()
		}
	}

}

enum class CucumberFormatDestinationType {
	STDOUT,
	FILE,
	URL
}