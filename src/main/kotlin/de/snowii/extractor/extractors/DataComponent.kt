package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer


class DataComponent : Extractor.Extractor {
    override fun fileName(): String {
        return "data_component.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val dataComponentJson = JsonObject()
        val list = server.registryManager.getOrThrow(RegistryKeys.DATA_COMPONENT_TYPE).streamEntries().toList();
        for (item in list) {
            dataComponentJson.addProperty(item.value().toString(), Registries.DATA_COMPONENT_TYPE.getRawId(item.value()));
        }
        return dataComponentJson
    }
}
