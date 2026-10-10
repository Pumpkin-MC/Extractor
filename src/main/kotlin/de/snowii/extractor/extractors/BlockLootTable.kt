package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer

/**
 * Emits only the blocks whose loot table differs from the default
 * `minecraft:blocks/{block.name}`. This covers all `wallVariant()` blocks
 * (wall torches, wall signs, banners, coral fans, skulls, ...) whose loot
 * table is inherited from their base block at registration time and is
 * therefore not present in the vanilla datapack.
 */
class BlockLootTables : Extractor.Extractor {

    override fun fileName(): String = "block_loot_tables.json"

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevel = JsonObject()
        val overrides = JsonObject()

        for (block in BuiltInRegistries.BLOCK) {
            val blockKey = BuiltInRegistries.BLOCK.getKey(block)
            val lootTableId: Identifier = (block.lootTable
                .orElse(null)
                ?: continue).identifier()
            val defaultId = Identifier.fromNamespaceAndPath("minecraft", "blocks/${blockKey.path}")

            if (lootTableId != defaultId) {
                overrides.addProperty(blockKey.path, lootTableId.toString())
            }
        }

        topLevel.add("overrides", overrides)
        return topLevel
    }
}