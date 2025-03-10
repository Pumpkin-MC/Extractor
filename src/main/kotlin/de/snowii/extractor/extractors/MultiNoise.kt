package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList
import net.minecraft.world.biome.source.util.MultiNoiseUtil

/**
 * An extractor for MultiNoiseBiomeSourceParameterList that fully serializes NoiseHypercube and ParameterRange data.
 */
class MultiNoise : Extractor.Extractor {

    override fun fileName(): String {
        return "multi_noise.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val registryManager: DynamicRegistryManager.Immutable = server.registryManager
        val multiNoiseRegistry: Registry<MultiNoiseBiomeSourceParameterList> =
            registryManager.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)

        val rootJson = JsonObject()
        multiNoiseRegistry.streamEntries().forEach { entry ->
            val keyPath = entry.key.orElseThrow().value.path
            val paramListValue = entry.value()

            val paramListJson = JsonObject()

            val noiseEntries = paramListValue.entries.entries
            noiseEntries.forEach { pair ->
                val hypercube = pair.first
                val biomeEntry = pair.second

                val biomeKey = biomeEntry.key.orElseThrow().value.toString()

                val noiseJson = noiseHypercubeToJson(hypercube)

                paramListJson.add(biomeKey, noiseJson)
            }

            rootJson.add(keyPath, paramListJson)
        }


        return rootJson
    }

    /**
     * Converts a NoiseHypercube into a JsonObject.
     */
    private fun noiseHypercubeToJson(hypercube: MultiNoiseUtil.NoiseHypercube): JsonObject {
        val json = JsonObject()

        json.add("temperature", parameterRangeToJson(hypercube.temperature()))
        json.add("humidity", parameterRangeToJson(hypercube.humidity()))
        json.add("continentalness", parameterRangeToJson(hypercube.continentalness()))
        json.add("erosion", parameterRangeToJson(hypercube.erosion()))
        json.add("depth", parameterRangeToJson(hypercube.depth()))
        json.add("weirdness", parameterRangeToJson(hypercube.weirdness()))
        json.addProperty("offset", hypercube.offset())

        return json
    }

    /**
     * Converts a ParameterRange into a JsonObject.
     */
    private fun parameterRangeToJson(parameterRange: MultiNoiseUtil.ParameterRange): JsonArray {
        val array = JsonArray()
        array.add(parameterRange.min())
        array.add(parameterRange.max())
        return array
    }
}
