package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer

class EntityAttributes : Extractor.Extractor {
    override fun fileName(): String {
        return "attributes.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val screensJson = JsonObject()
        for (attribute in Registries.ATTRIBUTE) {


            screensJson.addProperty(
                Registries.ATTRIBUTE.getId(attribute)!!.path,
                attribute.defaultValue
            )
        }

        return screensJson
    }
}