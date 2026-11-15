package com.example.data.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Handles API fields that may arrive as either integer (0/1) or boolean (false/true).
 */
object FlexibleBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleBoolean", PrimitiveKind.BOOLEAN)

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }

    override fun deserialize(decoder: Decoder): Boolean {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val element = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return false
        element.booleanOrNull?.let { return it }
        element.intOrNull?.let { return it != 0 }
        return element.content.equals("true", ignoreCase = true) || element.content == "1"
    }
}

/**
 * Handles viewer counts arriving as either string ("402") or numeric (402).
 */
object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }

    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return 0
        element.intOrNull?.let { return it }
        return element.content.trim().toIntOrNull() ?: 0
    }
}

@Serializable
data class PpvStreamsResponse(
    val success: Boolean = false,
    val timestamp: Long = 0L,
    @SerialName("READ_ME") val readMe: String? = null,
    val performance: Double? = null,
    val streams: List<PpvCategoryDto> = emptyList(),
)

@Serializable
data class PpvCategoryDto(
    val id: Int = 0,
    val category: String = "",
    @Serializable(with = FlexibleBooleanSerializer::class)
    @SerialName("always_live")
    val alwaysLive: Boolean = false,
    val streams: List<PpvStreamDto> = emptyList(),
)

@Serializable
data class PpvStreamDto(
    val id: Int = 0,
    val name: String = "",
    val tag: String? = null,
    @SerialName("source_tag") val sourceTag: String? = null,
    val poster: String? = null,
    val blurhash: String? = null,
    val colors: List<String> = emptyList(),
    @SerialName("uri_name") val uriName: String? = null,
    @SerialName("starts_at") val startsAt: Long? = null,
    @SerialName("ends_at") val endsAt: Long? = null,
    @Serializable(with = FlexibleBooleanSerializer::class)
    @SerialName("always_live")
    val alwaysLive: Boolean = false,
    val locale: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    val iframe: String? = null,
    val allowpaststreams: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val viewers: Int = 0,
    val substreams: List<PpvSubstreamDto> = emptyList(),
)

@Serializable
data class PpvSubstreamDto(
    val id: Int = 0,
    val name: String? = null,
    val tag: String? = null,
    @SerialName("uri_name") val uriName: String? = null,
    @SerialName("source_tag") val sourceTag: String? = null,
    val locale: String? = null,
    val iframe: String? = null,
    val quality: String? = null,
    val language: String? = null,
)
