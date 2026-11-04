package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.component.SuspiciousStewEffects
import net.minecraft.world.level.block.SuspiciousEffectHolder

class SuspiciousEffects : Extractor.Extractor {
    override fun fileName(): String {
        return "suspicious_effects.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE)
        val effectsJson = JsonObject()
        for (item in BuiltInRegistries.ITEM) {
            val holder = SuspiciousEffectHolder.tryGet(item) ?: continue
            effectsJson.add(
                BuiltInRegistries.ITEM.getKey(item).path,
                SuspiciousStewEffects.CODEC.encodeStart(ops, holder.suspiciousEffects).getOrThrow()
            )
        }

        return effectsJson
    }
}
