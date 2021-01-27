@file:Suppress("UnstableApiUsage")
package dev.hworblehat.gradlecumber.dsl

import dev.hworblehat.gradlecumber.util.NO_ACTION
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import java.io.File
import java.nio.file.Path
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

interface CucumberFeaturesSpec {

	@get:InputFiles
	val allFiles: FileCollection

	@get:Input
	val args: Provider<out List<String>>

	@get:Internal
	val includesClasspath: Provider<out Boolean>

	fun extends(features: CucumberFeaturesSpec)

	fun files(vararg paths: Any, config: Action<in ConfigurableFileCollection> = NO_ACTION): ConfigurableFileCollection

	fun dir(path: String)

	fun dir(dir: File)

	fun dir(path: Path) = dir(path.toFile())

	fun dir(dir: Directory)

	fun dir(provider: Provider<out Directory>)

	fun file(path: String, config: Action<in FeatureFileRef> = NO_ACTION): FeatureFileRef

	fun file(path: File, config: Action<in FeatureFileRef> = NO_ACTION): FeatureFileRef

	fun file(path: Path, config: Action<in FeatureFileRef> = NO_ACTION): FeatureFileRef = file(path.toFile(), config)

	fun file(path: RegularFile, config: Action<in FeatureFileRef> = NO_ACTION): FeatureFileRef

	fun file(provider: Provider<out RegularFile>, config: Action<in FeatureFileRef> = NO_ACTION): FeatureFileRef

	fun classpath(path: String, config: Action<in FeatureFileRef> = NO_ACTION): FeatureFileRef

	fun rerun(path: String)

	fun rerun(path: File)

	fun rerun(path: Path) = rerun(path.toFile())

	fun rerun(path: RegularFile)

	fun rerun(provider: Provider<out RegularFile>)

	// Kotlin extensions

	fun file(path: String, config: FeatureFileRef.() -> Unit): FeatureFileRef
			= file(path) { ref: FeatureFileRef -> ref.config() }

	fun file(path: File, config: FeatureFileRef.() -> Unit): FeatureFileRef
			= file(path) { ref: FeatureFileRef -> ref.config() }

	fun file(path: Path, config: FeatureFileRef.() -> Unit): FeatureFileRef
			= file(path) { ref: FeatureFileRef -> ref.config() }

	fun file(path: RegularFile, config: FeatureFileRef.() -> Unit): FeatureFileRef
			= file(path) { ref: FeatureFileRef -> ref.config() }

	fun file(provider: Provider<out RegularFile>, config: FeatureFileRef.() -> Unit): FeatureFileRef
			= file(provider) { ref: FeatureFileRef -> ref.config() }

	fun classpath(path: String, config: FeatureFileRef.() -> Unit): FeatureFileRef
			= classpath(path) { ref: FeatureFileRef -> ref.config() }

	fun files(vararg paths: Any, config: ConfigurableFileCollection.() -> Unit): ConfigurableFileCollection
			= files(paths) { cfc: ConfigurableFileCollection -> cfc.config() }

}

interface FeatureFileRef {

	val lines: Set<Int>

}

internal fun ObjectFactory.newCucumberFeaturesSpec(): CucumberFeaturesSpec =
	newInstance(DefaultCucumberFeaturesSpec::class.java)

internal open class DefaultCucumberFeaturesSpec @Inject constructor(
	private val objects: ObjectFactory,
	layout: ProjectLayout,
	providers: ProviderFactory
) : CucumberFeaturesSpec {

	private val projectDir = layout.projectDirectory
	private val features: MutableList<CucumberFeatureRef> = ArrayList()

	@get:InputFiles
	override val allFiles: ConfigurableFileCollection = objects.fileCollection()

	@get: Input
	override val args: Provider<out List<String>> = providers.provider {
		val args = ArrayList<String>()
		features.forEach { it.addArgString(args) }
		args
	}

	@get:Internal
	override val includesClasspath: Provider<Boolean> = providers.provider {
		for(f in features) {
			if(f.isIncludesClasspath()) return@provider true
		}
		false
	}

	private fun addFeature(ref: CucumberFeatureRef) {
		features.add(ref)
		ref.addFiles(allFiles)
	}

	override fun extends(features: CucumberFeaturesSpec) {
		addFeature(ExtendingFeaturesRef(features))
	}

	override fun files(vararg paths: Any, config: Action<in ConfigurableFileCollection>): ConfigurableFileCollection {
		val files = objects.fileCollection()
		files.from(paths)
		config.execute(files)
		addFeature(FileCollectionFeatureRef(files))
		return files
	}

	override fun dir(path: String) = dir(projectDir.dir(path))

	override fun dir(dir: File) {
		val prop = objects.directoryProperty()
		prop.set(dir)
		dir(prop)
	}

	override fun dir(dir: Directory) {
		val prop = objects.directoryProperty()
		prop.set(dir)
		dir(prop)
	}

	override fun dir(provider: Provider<out Directory>) {
		addFeature(DirFeatureRef(provider))
	}

	override fun file(path: String, config: Action<in FeatureFileRef>): FeatureFileRef =
		file(projectDir.file(path), config)

	override fun file(path: File, config: Action<in FeatureFileRef>): FeatureFileRef {
		val prop = objects.fileProperty()
		prop.set(path)
		return file(prop, config)
	}

	override fun file(path: RegularFile, config: Action<in FeatureFileRef>): FeatureFileRef {
		val prop = objects.fileProperty()
		prop.set(path)
		return file(prop, config)
	}

	override fun file(provider: Provider<out RegularFile>, config: Action<in FeatureFileRef>): FeatureFileRef {
		val ref = RegularFileFeatureRef(provider)
		config.execute(ref)
		addFeature(ref)
		return ref
	}

	override fun classpath(path: String, config: Action<in FeatureFileRef>): FeatureFileRef {
		val ref = ClasspathFeatureRef(path)
		config.execute(ref)
		addFeature(ref)
		return ref
	}

	override fun rerun(path: String) = rerun(projectDir.file(path))

	override fun rerun(path: File) {
		val prop = objects.fileProperty()
		prop.set(path)
		rerun(prop)
	}

	override fun rerun(path: RegularFile) {
		val prop = objects.fileProperty()
		prop.set(path)
		rerun(prop)
	}

	override fun rerun(provider: Provider<out RegularFile>) {
		addFeature(RerunFeatureRef(provider))
	}

}

internal interface CucumberFeatureRef {
	fun addArgString(args: MutableList<in String>)
	fun addFiles(files: ConfigurableFileCollection) {}
	fun isIncludesClasspath(): Boolean = false
}

internal abstract class SingleFeatureRef : CucumberFeatureRef, FeatureFileRef {

	override val lines: Set<Int> = TreeSet()

	protected fun appendLines(sb: StringBuilder) = lines.forEach { sb.append(':').append(it) }

}

internal class ClasspathFeatureRef(
	private val path: String
) : SingleFeatureRef() {

	override fun addArgString(args: MutableList<in String>) {
		val sb = StringBuilder("classpath:").append(path)
		appendLines(sb)
		args.add(sb.toString())
	}

	override fun isIncludesClasspath(): Boolean = true

}

internal class DirFeatureRef(
	private val provider: Provider<out Directory>
) : CucumberFeatureRef {

	override fun addArgString(args: MutableList<in String>) {
		args.add(provider.get().asFile.absolutePath)
	}

	override fun addFiles(files: ConfigurableFileCollection) {
		files.from(provider)
	}

}

internal class RegularFileFeatureRef (
	private val provider: Provider<out RegularFile>
) : SingleFeatureRef() {

	override fun addArgString(args: MutableList<in String>) {
		val sb = StringBuilder(provider.get().asFile.absolutePath)
		appendLines(sb)
		args.add(sb.toString())
	}

	override fun addFiles(files: ConfigurableFileCollection) {
		files.from(provider)
	}

}

internal class RerunFeatureRef (
	private val provider: Provider<out RegularFile>
) : CucumberFeatureRef {

	override fun addArgString(args: MutableList<in String>) {
		args.add("@${provider.get().asFile.absolutePath}")
	}

	override fun addFiles(files: ConfigurableFileCollection) {
		files.from(provider)
	}

}

internal class FileCollectionFeatureRef (
	private val files: FileCollection
) : CucumberFeatureRef {

	override fun addArgString(args: MutableList<in String>) {
		args.addAll(files.files.map { it.absolutePath })
	}

	override fun addFiles(files: ConfigurableFileCollection) {
		files.from(this.files)
	}

}

internal class ExtendingFeaturesRef (
	private val extends: CucumberFeaturesSpec
) : CucumberFeatureRef {

	override fun addArgString(args: MutableList<in String>) {
		args.addAll(extends.args.get())
	}

	override fun addFiles(files: ConfigurableFileCollection) {
		files.from(extends.allFiles)
	}

	override fun isIncludesClasspath(): Boolean = extends.includesClasspath.get()

}
