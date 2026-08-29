package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.synth.NormalNoise

class NoiseParameters : Extractor.Extractor {
    override fun fileName(): String {
        return "noise_parameters.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val noisesJson = JsonObject()
        val registryAccess = server.registryAccess()

        val registry = registryAccess.lookupOrThrow(Registries.NOISE)

        val ops = JsonOps.INSTANCE

        registry.listElements().forEach { holder ->
            val noise = holder.value()
            val key = holder.key()

            val json = NormalNoise.NoiseParameters.DIRECT_CODEC.encodeStart(
                ops,
                noise
            ).getOrThrow()


            noisesJson.add(
                key.identifier().toString(),
                json
            )
        }

        return noisesJson
    }
}