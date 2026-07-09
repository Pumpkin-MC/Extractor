package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer

class WorldClock : Extractor.Extractor {
    override fun fileName(): String {
        return "world_clock.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val worldClockJson = JsonArray()
        val registry =
            server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
        for (clock in registry) {
            worldClockJson.add(
                registry.getKey(clock)!!.path,
            )
        }

        return worldClockJson
    }
}