package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.item.equipment.trim.ArmorTrimPattern
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer

class TrimPatterns : Extractor.Extractor {
    override fun fileName(): String {
        return "trim_patterns.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val patterns = JsonObject()

        for (pattern in server.registryManager.getOrThrow(RegistryKeys.TRIM_PATTERN).streamEntries().toList()) {
            patterns.add(
                pattern.key.get().value.toString(),
                ArmorTrimPattern.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                    pattern.value()
                ).getOrThrow()
            )
        }

        return patterns
    }
}