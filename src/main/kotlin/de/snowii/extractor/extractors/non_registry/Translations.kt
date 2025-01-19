package de.snowii.extractor.extractors.non_registry

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.server.MinecraftServer
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class Translations : Extractor.Extractor {
    private val gson: Gson = Gson()

    override fun fileName(): String {
        return "en_us.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val inputStream = this.javaClass.getResourceAsStream("/assets/minecraft/lang/en_us.json") ?: throw IllegalArgumentException("Could not find lang en_us.json")

        return inputStream.use { stream ->
            gson.fromJson(
                InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonObject::class.java
            ) as JsonObject
        }
    }
}