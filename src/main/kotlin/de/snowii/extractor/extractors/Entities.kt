package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.SpawnPlacementTypes
import net.minecraft.world.entity.SpawnPlacements
import net.minecraft.world.level.storage.loot.LootTable

class Entities : Extractor.Extractor {
    override fun fileName(): String {
        return "entities.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val entitiesJson = JsonObject()
        val registryAccess = server.registries().compositeAccess()
        val ops = registryAccess.createSerializationContext(JsonOps.INSTANCE)
        val damageSource = server.overworld().damageSources().generic()

        for (entityType in BuiltInRegistries.ENTITY_TYPE) {
            val entityJson = JsonObject()
            val entityName = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).path
            entityJson.addProperty("id", BuiltInRegistries.ENTITY_TYPE.getId(entityType))

            val entity = entityType.create(server.overworld(), EntitySpawnReason.NATURAL)
            if (entity != null) {
                if (entity is LivingEntity) {
                    entityJson.addProperty("max_health", entity.maxHealth)

                    if (entityName in TARGET_HURT_SOUND_ENTITIES) {
                        val hurtSound = getHurtSound(entity, damageSource)
                        val hurtSoundId = hurtSound?.let(BuiltInRegistries.SOUND_EVENT::getKey)?.path
                        if (hurtSoundId != null) {
                            entityJson.addProperty("hurt_sound", hurtSoundId)
                        }
                    }
                }
                entityJson.addProperty("attackable", entity.isAttackable)
                entityJson.addProperty("mob", entity is Mob)
                entityJson.addProperty("limit_per_chunk", (entity as? Mob)?.maxSpawnClusterSize ?: 0)
            }

            entityJson.addProperty("summonable", entityType.canSummon())
            entityJson.addProperty("saveable", entityType.canSerialize())
            entityJson.addProperty("fire_immune", entityType.fireImmune())
            entityJson.addProperty("category", entityType.category.name)
            entityJson.addProperty("can_spawn_far_from_player", entityType.canSpawnFarFromPlayer())

            val dimension = JsonArray()
            dimension.add(entityType.width)
            dimension.add(entityType.height)
            entityJson.add("dimension", dimension)
            entityJson.addProperty("eye_height", entityType.dimensions.eyeHeight)

            if (entityType.defaultLootTable.isPresent) {
                val table = server.reloadableRegistries()
                    .getLootTable(entityType.defaultLootTable.get())
                entityJson.add(
                    "loot_table",
                    LootTable.DIRECT_CODEC.encodeStart(ops, table).orThrow
                )
            }

            val spawnRestriction = JsonObject()
            val data = SpawnPlacements.getPlacementType(entityType)
            val locationName = when (data) {
                SpawnPlacementTypes.IN_LAVA -> "IN_LAVA"
                SpawnPlacementTypes.IN_WATER -> "IN_WATER"
                SpawnPlacementTypes.ON_GROUND -> "ON_GROUND"
                SpawnPlacementTypes.NO_RESTRICTIONS -> "UNRESTRICTED"
                else -> ""
            }
            val heightmap = SpawnPlacements.getHeightmapType(entityType)

            spawnRestriction.addProperty("location", locationName)
            spawnRestriction.addProperty("heightmap", heightmap.toString())
            entityJson.add("spawn_restriction", spawnRestriction)

            entitiesJson.add(
                entityName,
                entityJson
            )
        }

        return entitiesJson
    }

    private fun getHurtSound(entity: LivingEntity, damageSource: DamageSource) =
        getHurtSoundMethod.invoke(entity, damageSource) as? net.minecraft.sounds.SoundEvent

    companion object {
        private val TARGET_HURT_SOUND_ENTITIES = setOf(
            "bogged",
            "drowned",
            "enderman",
            "husk",
            "parched",
            "skeleton",
            "stray",
            "wither_skeleton",
            "zombie",
            "zombie_villager",
        )

        private val getHurtSoundMethod = LivingEntity::class.java
            .getDeclaredMethod("getHurtSound", DamageSource::class.java)
            .apply { isAccessible = true }
    }
}
