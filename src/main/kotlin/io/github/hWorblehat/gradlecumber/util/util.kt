package io.github.hWorblehat.gradlecumber.util

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.SourceSetContainer

internal val NO_ACTION: Action<Any> = Action {}

internal val Project.sourceSets: SourceSetContainer
	get() = extensions.getByType(SourceSetContainer::class.java)

internal fun <T> Iterable<Provider<T>>.liftProvider(providers: ProviderFactory): Provider<out List<T>> =
	fold(providers.provider<MutableList<T>> { ArrayList() }) { listProvider, elementProvider ->
		listProvider.zip(elementProvider) { list, element ->
			list.add(element)
			list
		}
	}