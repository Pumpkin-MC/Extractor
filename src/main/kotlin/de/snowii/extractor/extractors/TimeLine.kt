package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.timeline.Timeline

class TimeLine : Extractor.Extractor {
    override fun fileName(): String {
        return "timeline.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val worldClockJson = JsonObject()
        val ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE)
        val registry =
            server.registryAccess().lookupOrThrow(Registries.TIMELINE)
        for (timeline in registry) {
            val sub = Timeline.DIRECT_CODEC
                .encodeStart(ops,timeline)
                .getOrThrow() as JsonObject;
            worldClockJson.add(
                registry.getKey(timeline)!!.path,
                sub
            )
        }
        return worldClockJson
    }
}