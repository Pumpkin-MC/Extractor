package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer

class InputControlType : Extractor.Extractor {
    override fun fileName(): String {
        return "input_control_type.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val InputControlTypeJson = JsonObject()
        val registry = server.registryManager.getOrThrow(RegistryKeys.INPUT_CONTROL_TYPE)

        for (inputControlType in registry.streamEntries().toList()) {
            val id = registry.getId(inputControlType.value())
            InputControlTypeJson.addProperty(id.toString(), registry.getRawId(inputControlType.value()))
        }

        return InputControlTypeJson
    }
}