package com.ghost.tv.data.remote.util

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * A Gson TypeAdapter that handles fields that could be either a String, an Array of Strings,
 * or even an Object (which it skips). This is common in some IPTV providers' APIs.
 */
class FlexibleStringAdapter : TypeAdapter<String?>() {
    override fun write(out: JsonWriter, value: String?) {
        out.value(value)
    }

    override fun read(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                val sb = StringBuilder()
                reader.beginArray()
                while (reader.hasNext()) {
                    val nextToken = reader.peek()
                    if (nextToken != JsonToken.BEGIN_ARRAY && nextToken != JsonToken.BEGIN_OBJECT && nextToken != JsonToken.END_ARRAY) {
                        val value = try {
                            reader.nextString()
                        } catch (e: Exception) {
                            reader.skipValue()
                            null
                        }
                        if (value != null) {
                            if (sb.isNotEmpty()) sb.append(", ")
                            sb.append(value)
                        }
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endArray()
                sb.toString()
            }
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.BEGIN_OBJECT -> {
                reader.skipValue()
                null
            }
            else -> {
                try {
                    reader.nextString()
                } catch (e: Exception) {
                    reader.skipValue()
                    null
                }
            }
        }
    }
}
