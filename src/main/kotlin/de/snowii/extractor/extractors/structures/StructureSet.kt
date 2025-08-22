package de.snowii.extractor.extractors.structures

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.structure.StructureSet

class StructureSet : Extractor.Extractor {
    override fun fileName(): String {
        return "structure_set.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val finalJson = JsonObject()
        val registry =
            server.registryManager.getOrThrow(RegistryKeys.STRUCTURE_SET)
        for (setting in registry) {
            finalJson.add(
                registry.getId(setting)!!.path,
                StructureSet.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                    setting
                ).getOrThrow()
            )
        }

        return finalJson
    }
}