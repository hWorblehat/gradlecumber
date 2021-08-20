package io.github.hWorblehat.gradlecumber.exec

import io.cucumber.messages.Messages
import io.cucumber.messages.NdjsonToMessageIterable
import mu.KotlinLogging
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.experimental.and

private val LOGGER = KotlinLogging.logger {}

private const val UTF8_CONTINUATION_BYTE_MASK = 0b1100_0000.toByte()
private const val UTF8_CONTINUATION_BYTE_COMMON = 0b1000_0000.toByte()

private fun isUTF8CharContinuation(byte: Byte): Boolean =
	byte and UTF8_CONTINUATION_BYTE_MASK == UTF8_CONTINUATION_BYTE_COMMON

internal fun fileIndicatesCompletedTestRun(file: File): Boolean {

	try {
		// Read text from the end of the file
		val text = RandomAccessFile(file, "r").use { raf ->

			// Read a buffer of bytes from the end of the file
			val fileLen = raf.length()
			if(fileLen == 0L) {
				return false
			}
			val seek = maxOf(0L, fileLen - 512)
			val bytes = ByteArray((fileLen - seek).toInt())
			raf.seek(seek)
			val len = raf.read(bytes)

			// Look for the first byte that begins a UTF-8 character
			var start = 0
			while (start < len && isUTF8CharContinuation(bytes[start])) {
				++start
			}
			if (start == len) {
				throw IOException("Unrecognized file encoding for $file. Expecting UTF-8.")
			}

			// Convert buffer to string
			String(bytes, start, len - start, Charsets.UTF_8)
		}

		return textIndicatesCompletedTestRun(text)
	} catch(e: FileNotFoundException) {
		LOGGER.debug {"Messages file '${file}' does not exist. Bad test run presumed."}
		return false
	}
}

/**
 * Determines whether the given text indicates a completed Cucumber test run by looking for the "testRunFinished"
 * message.
 *
 * The `text` is expected to be the output of Cucumber's "message" formatter.
 * it does not need to be the whole output – only a sufficient portion from the end to contain the "testRunFinished"
 * message.
 *
 * @param text The text to inspect
 * @return `true` if the "testRunFinished" message was found
 */
internal fun textIndicatesCompletedTestRun(text: String): Boolean {
	// Look for the 'testRunFinished' Cucumber message
	val finalMessageStart = text.indexOf("{\"testRunFinished\":")
	if(finalMessageStart == -1) {
		return false
	}

	// Parse the message
	val envelope = text.substring(finalMessageStart).byteInputStream().use {
		try {
			val iter = NdjsonToMessageIterable(it).iterator()
			if (!iter.hasNext()) {
				return false
			}
			iter.next()
		} catch(ex: Exception) {
			LOGGER.debug("Error occurred when attempting to parse Cucumber message. Presumed incomplete test run.", ex)
			return false
		}
	}

	// Check the message is as expected
	return envelope.messageCase == Messages.Envelope.MessageCase.TEST_RUN_FINISHED
}