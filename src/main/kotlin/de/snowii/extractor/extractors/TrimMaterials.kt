package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.item.equipment.trim.ArmorTrimMaterial
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer

class TrimMaterials : Extractor.Extractor {
    override fun fileName(): String {
        return "trim_materials.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val materials = JsonObject()

        for (material in server.registryManager.getOrThrow(RegistryKeys.TRIM_MATERIAL).streamEntries().toList()) {
            materials.add(
                material.key.get().value.toString(),
                ArmorTrimMaterial.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                    material.value()
                ).getOrThrow()
            )
        }

        return materials
    }
}