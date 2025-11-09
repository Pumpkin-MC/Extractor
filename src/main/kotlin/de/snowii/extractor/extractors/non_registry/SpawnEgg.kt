package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.item.SpawnEggItem
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer

class SpawnEgg : Extractor.Extractor {
    override fun fileName(): String {
        return "spawn_egg.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val eggJson = JsonObject()

        for (spawnEggItem in SpawnEggItem.getAll()) {
            val type = spawnEggItem.getEntityType(spawnEggItem.defaultStack)
            eggJson.addProperty(
                Registries.ITEM.getRawId(spawnEggItem).toString(),
                Registries.ENTITY_TYPE.getId(type).path
            )
        }
        return eggJson
    }
}