package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.SpawnLocationTypes
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.SpawnRestriction
import net.minecraft.loot.LootTable
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer

class Entities : Extractor.Extractor {
    override fun fileName(): String {
        return "entities.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val entitiesJson = JsonObject()
        for (entityType in Registries.ENTITY_TYPE) {
            val entityJson = JsonObject()
            entityJson.addProperty("id", Registries.ENTITY_TYPE.getRawId(entityType))
            val entity = entityType.create(server.overworld!!, SpawnReason.NATURAL)
            if (entity != null) {
                if (entity is LivingEntity) {
                    entityJson.addProperty("max_health", entity.maxHealth)
                }
                entityJson.addProperty("attackable", entity.isAttackable)
            }
            entityJson.addProperty("summonable", entityType.isSummonable)
            entityJson.addProperty("fire_immune", entityType.isFireImmune)
            val dimension = JsonArray()
            dimension.add(entityType.width)
            dimension.add(entityType.height)
            entityJson.add("dimension", dimension)
            entityJson.addProperty("eye_height", entityType.dimensions.eyeHeight)
            if (entityType.lootTableKey.isPresent) {
                val table = server.reloadableRegistries
                    .getLootTable(entityType.lootTableKey.get() as RegistryKey<LootTable?>)
                entityJson.add(
                    "loot_table", LootTable::CODEC.get().encodeStart(
                        RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                        table
                    ).getOrThrow()
                )
            }
            val spawnRestriction = JsonObject()
            val location = SpawnRestriction.getLocation(entityType)
            val locationName = when (location) {
                SpawnLocationTypes::IN_LAVA.get() -> {
                    "IN_LAVA"
                }

                SpawnLocationTypes::IN_WATER.get() -> {
                    "IN_WATER"
                }

                SpawnLocationTypes::ON_GROUND.get() -> {
                    "ON_GROUND"
                }

                SpawnLocationTypes::UNRESTRICTED.get() -> {
                    "UNRESTRICTED"
                }

                else -> {
                    ""
                }
            }

            spawnRestriction.addProperty("location", locationName)
            spawnRestriction.addProperty("heightmap", SpawnRestriction.getHeightmapType(entityType).toString())
            entityJson.add("spawn_restriction", spawnRestriction)

            entitiesJson.add(
                Registries.ENTITY_TYPE.getId(entityType).path, entityJson
            )
        }

        return entitiesJson
    }
}
