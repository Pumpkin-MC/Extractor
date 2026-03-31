package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.SpawnEggItem

class SpawnEgg : Extractor.Extractor {
    override fun fileName(): String {
        return "spawn_egg.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val eggJson = JsonObject()

        for (spawnEggItem in BuiltInRegistries.ITEM) {
            if (!BuiltInRegistries.ITEM.getKey(spawnEggItem).path.endsWith("_spawn_egg")) continue;
            val type = SpawnEggItem.getType(spawnEggItem.defaultInstance);
            eggJson.addProperty(
                BuiltInRegistries.ITEM.getId(spawnEggItem).toString(),
                EntityType.getKey(type!!).path
            )
        }
        return eggJson
    }
}