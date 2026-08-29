package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.server.MinecraftServer
import net.minecraft.stats.StatFormatter
import net.minecraft.stats.StatType

class Stats : Extractor.Extractor {
    override fun fileName(): String {
        return "stats.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val statsJson = JsonObject()
        val ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE)

        for (statType in BuiltInRegistries.STAT_TYPE) {
            val typeId = BuiltInRegistries.STAT_TYPE.getKey(statType).toString()
            val typeJson = JsonObject()

            typeJson.addProperty("id", BuiltInRegistries.STAT_TYPE.getId(statType))

            // Accessing fields made accessible via accesswidener
            @Suppress("UNCHECKED_CAST")
            val registry = statType.registry as net.minecraft.core.Registry<Any>
            
            @Suppress("UNCHECKED_CAST")
            val registryId = (BuiltInRegistries.REGISTRY as net.minecraft.core.Registry<net.minecraft.core.Registry<*>>).getKey(registry)
            typeJson.addProperty("registry", registryId?.toString() ?: "unknown")

            val displayName = statType.displayName
            typeJson.add("display_name", ComponentSerialization.CODEC.encodeStart(ops, displayName).getOrThrow())

            @Suppress("UNCHECKED_CAST")
            typeJson.add("entries", extractEntries(statType as StatType<Any>, registry))

            statsJson.add(typeId, typeJson)
        }

        return statsJson
    }

    private fun <T : Any> extractEntries(statType: StatType<T>, registry: net.minecraft.core.Registry<T>): JsonObject {
        val entriesJson = JsonObject()
        for (stat in statType) {
            val value = stat.value
            val valueId = registry.getKey(value).toString()
            
            val statJson = JsonObject()
            statJson.addProperty("id", registry.getId(value))
            statJson.addProperty("formatter", getFormatterName(stat.formatter))
            entriesJson.add(valueId, statJson)
        }
        return entriesJson
    }

    private fun getFormatterName(formatter: StatFormatter): String {
        return when (formatter) {
            StatFormatter.DEFAULT -> "default"
            StatFormatter.DIVIDE_BY_TEN -> "divide_by_ten"
            StatFormatter.DISTANCE -> "distance"
            StatFormatter.TIME -> "time"
            else -> "unknown"
        }
    }
}
