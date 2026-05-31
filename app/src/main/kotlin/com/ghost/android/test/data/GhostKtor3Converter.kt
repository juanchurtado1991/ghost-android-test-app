package com.ghost.android.test.data

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.ghostInternalEncodeWithWriter
import com.ghost.serialization.ghostInternalUseFlatReader
import com.ghost.serialization.releaseScratchBuffer
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.readAvailable
import kotlin.reflect.KClass

/**
 * Ktor 3–compatible Ghost content converter for the test app.
 *
 * Mirrors the zero-extra-copy design of GhostConverterFactory (Retrofit):
 *  - Deserialize: acquires a pooled scratch buffer sized to DEFAULT_BUFFER_SIZE
 *    (large enough for typical multipage payloads), reads the channel in a
 *    manual loop, then parses directly from that buffer and returns it to the pool.
 *    No ByteArray allocation on the hot path once the pool is warm.
 *  - Serialize: pools the GhostJsonFlatWriter via ghostInternalEncodeWithWriter;
 *    the resulting ByteArray is handed straight to ByteArrayContent.
 */
@OptIn(InternalGhostApi::class)
class GhostKtor3Converter : ContentConverter {

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? {
        if (value == null) return null

        @Suppress("UNCHECKED_CAST")
        val serializer = Ghost
            .getSerializer(typeInfo.type as KClass<Any>)
            ?: return null

        val bytes = ghostInternalEncodeWithWriter { writer ->
            serializer.serialize(writer, value)
        }
        return ByteArrayContent(bytes, contentType)
    }

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any {
        var scratch =
            acquireScratchBuffer(DEFAULT_BUFFER_SIZE)

        try {
            var offset = 0

            while (true) {
                if (offset == scratch.size) {
                    val grown =
                        acquireScratchBuffer(scratch.size * 2)

                    scratch.copyInto(
                        grown,
                        0,
                        0,
                        offset
                    )

                    releaseScratchBuffer(scratch)
                    scratch = grown
                }

                val read = content.readAvailable(
                    scratch,
                    offset,
                    scratch.size - offset
                )

                if (read == -1) break
                offset += read
            }

            @Suppress("UNCHECKED_CAST")
            return ghostInternalUseFlatReader(
                scratch,
                offset
            ) { reader ->
                val serializer = Ghost.getSerializer(typeInfo.type as KClass<Any>)
                    ?: Ghost.throwError("${Ghost.NOT_FOUND} ${typeInfo.type.simpleName}. ${Ghost.MISSING_ANN}")

                serializer.deserialize(reader)
            }
        } finally {
            releaseScratchBuffer(scratch)
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 524288
    }
}
