package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.IExtractor
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer


class Particles : IExtractor {
    override fun fileName(): String {
        return "particles.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val particlesJson = JsonArray()
        for (particle in Registries.PARTICLE_TYPE) {
            particlesJson.add(
                Registries.PARTICLE_TYPE.getId(particle)!!.path,
            )
        }

        return particlesJson
    }
}
