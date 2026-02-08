package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer

class DialogBodyType : Extractor.Extractor {
    override fun fileName(): String {
        return "dialog_body_type.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val dialogBodyJson = JsonObject()
        val registry = server.registryManager.getOrThrow(RegistryKeys.DIALOG_BODY_TYPE)

        for (dialogType in registry.streamEntries().toList()) {
            val id = registry.getId(dialogType.value())
            dialogBodyJson.addProperty(id.toString(), registry.getRawId(dialogType.value()))
        }

        return dialogBodyJson
    }
}