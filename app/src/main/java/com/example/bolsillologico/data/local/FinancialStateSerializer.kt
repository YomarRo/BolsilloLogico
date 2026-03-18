package com.example.bolsillologico.data.local

import androidx.datastore.core.Serializer
import com.example.bolsillologico.domain.model.FinancialState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object FinancialStateSerializer : Serializer<FinancialState> {
    override val defaultValue: FinancialState = FinancialState()

    override suspend fun readFrom(input: InputStream): FinancialState {
        return try {
            Json.decodeFromString(
                deserializer = FinancialState.serializer(),
                string = input.readBytes().decodeToString()
            )
        } catch (e: SerializationException) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: FinancialState, output: OutputStream) {
        output.write(
            Json.encodeToString(
                serializer = FinancialState.serializer(),
                value = t
            ).encodeToByteArray()
        )
    }
}
