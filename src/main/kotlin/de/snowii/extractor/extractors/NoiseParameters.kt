package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler

class NoiseParameters : Extractor.Extractor {
    override fun fileName(): String {
        return "noise_parameters.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val noisesJson = JsonObject()
        val noiseParameterRegistry =
            server.registryManager.getOrThrow(RegistryKeys.NOISE_PARAMETERS)
        for (noise in noiseParameterRegistry) {
            noisesJson.add(
                noiseParameterRegistry.getId(noise)!!.path,
                DoublePerlinNoiseSampler.NoiseParameters.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                    noise
                ).getOrThrow()
            )
        }

        return noisesJson
    }
}