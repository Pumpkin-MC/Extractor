package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer

class DialogType : Extractor.Extractor {
    override fun fileName(): String {
        return "dialog_type.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val dialogTypeJson = JsonObject()
        val registry = server.registryManager.getOrThrow(RegistryKeys.DIALOG_TYPE)

        for (dialogType in registry.streamEntries().toList()) {
            val id = registry.getId(dialogType.value())
            dialogTypeJson.addProperty(id.toString(), registry.getRawId(dialogType.value()))
        }

        return dialogTypeJson
    }
}