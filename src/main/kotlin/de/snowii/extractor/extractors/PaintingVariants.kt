package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.entity.decoration.painting.PaintingVariant
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer

class PaintingVariants : Extractor.Extractor {
    override fun fileName(): String {
        return "painting_variants.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val paintingVariants = JsonObject()

        for (paintingVariant in server.registryManager.getOrThrow(RegistryKeys.PAINTING_VARIANT).streamEntries().toList()) {
            paintingVariants.add(
                paintingVariant.key.get().value.toString(),
                PaintingVariant.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                    paintingVariant.value()
                ).getOrThrow()
            )
        }

        return paintingVariants
    }
}