package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer

class DialogAction : Extractor.Extractor {
    override fun fileName(): String {
        return "dialog_action.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val dialogActionJson = JsonObject()
        val registry = server.registryManager.getOrThrow(RegistryKeys.DIALOG_ACTION_TYPE)

        for (dialogType in registry.streamEntries().toList()) {
            val id = registry.getId(dialogType.value())
            dialogActionJson.addProperty(id.toString(), registry.getRawId(dialogType.value()))
        }

        return dialogActionJson
    }
}