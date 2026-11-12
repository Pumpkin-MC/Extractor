package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStackTemplate

class Items : Extractor.Extractor {
    override fun fileName(): String {
        return "items.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val itemsJson = JsonObject()
        val registryAccess = server.registryAccess()

        val ops = registryAccess.createSerializationContext(JsonOps.INSTANCE)

        val registry = registryAccess.lookupOrThrow(Registries.ITEM)

        registry.listElements().forEach { holder ->
            val realItem = holder.value()
            val itemKey = holder.key()

            val itemJson = JsonObject()

            itemJson.addProperty("id", registry.getId(realItem))

            realItem.craftingRemainder?.let { remainder ->
                itemJson.add(
                    "crafting_remainder",
                    ItemStackTemplate.CODEC.encodeStart(ops, remainder).getOrThrow()
                )
            }

            itemJson.add(
                "components",
                DataComponentMap.CODEC.encodeStart(
                    ops,
                    realItem.components()
                ).getOrThrow()
            )

            itemsJson.add(itemKey.identifier().path, itemJson)
        }

        return itemsJson
    }
}