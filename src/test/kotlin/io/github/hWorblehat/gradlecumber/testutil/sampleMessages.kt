package io.github.hWorblehat.gradlecumber.testutil

import java.io.InputStream

fun openMessageSample(name: String): InputStream =
	{}::class.java.getResourceAsStream("/messageSamples/$name.ndjson")

fun readMessageSample(name: String): String = openMessageSample(name)
	.reader(Charsets.UTF_8)
	.use { it.readText() }
