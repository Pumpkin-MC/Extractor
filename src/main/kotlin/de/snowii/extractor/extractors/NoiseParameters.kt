package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer

class NoiseParameters : Extractor.Extractor {
    override fun fileName(): String {
        return "noise_parameters.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val noisesJson = JsonObject()
        val noiseParameterRegistry =
            server.registryManager.getOrThrow(RegistryKeys.NOISE_PARAMETERS)
        for (noise in noiseParameterRegistry) {
            val noiseJson = JsonObject()
            noiseJson.addProperty("first_octave", noise.firstOctave)
            val amplitudesJson = JsonArray()
            noise.amplitudes.forEach { amplitude ->
                amplitudesJson.add(amplitude)
            }
            noiseJson.add("amplitudes", amplitudesJson)
            noisesJson.add(
                noiseParameterRegistry.getId(noise)!!.path,
                noiseJson
            )
        }

        return noisesJson
    }
}