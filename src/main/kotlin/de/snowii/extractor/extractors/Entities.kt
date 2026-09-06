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
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.SpawnPlacementTypes
import net.minecraft.world.entity.SpawnPlacements
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.attributes.DefaultAttributes
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
                    entityJson.addProperty("experience_reward", entity.getBaseExperienceReward(server.overworld()))

                    if (entityName !in TARGET_SOUND_BLACKLIST) {
                        val hurtSound = getHurtSound(entity, damageSource)
                        val hurtSoundId = hurtSound?.let(BuiltInRegistries.SOUND_EVENT::getKey)?.path
                        if (hurtSoundId != null) {
                            entityJson.addProperty("hurt_sound", hurtSoundId)
                        }

                        val deathSound = getDeathSound(entity)
                        val deathSoundId = deathSound?.let(BuiltInRegistries.SOUND_EVENT::getKey)?.path
                        if (deathSound != null) {
                            entityJson.addProperty("death_sound", deathSoundId)
                        }
                    }
                }
                entityJson.addProperty("attackable", entity.isAttackable)
                entityJson.addProperty("mob", entity is Mob)
                entityJson.addProperty("limit_per_chunk", (entity as? Mob)?.maxSpawnClusterSize ?: 0)
            }

            @Suppress("UNCHECKED_CAST")
            if (DefaultAttributes.hasSupplier(entityType as EntityType<out LivingEntity>)) {
                val supplier = DefaultAttributes.getSupplier(entityType)

                // Backwards compatibility for top-level max_health
                val maxHealth = Attributes.MAX_HEALTH
                if (supplier.hasAttribute(maxHealth)) {
                    entityJson.addProperty("max_health", supplier.getBaseValue(maxHealth))
                }

                val attributesArray = JsonArray()
                for (attribute in BuiltInRegistries.ATTRIBUTE) {
                    val holder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute)
                    if (supplier.hasAttribute(holder)) {
                        val attributeJson = JsonObject()
                        attributeJson.addProperty(
                            BuiltInRegistries.ATTRIBUTE.getKey(attribute)!!.path,
                            supplier.getBaseValue(holder)
                        )
                        attributesArray.add(attributeJson)
                    }
                }
                entityJson.add("attributes", attributesArray)
            }

            entityJson.addProperty("summonable", entityType.canSummon())
            entityJson.addProperty("saveable", entityType.canSerialize())
            entityJson.addProperty("fire_immune", entityType.fireImmune())
            entityJson.addProperty("category", entityType.category.name)
            entityJson.addProperty("can_spawn_far_from_player", entityType.canSpawnFarFromPlayer())
            entityJson.addProperty("client_tracking_range", entityType.clientTrackingRange())
            entityJson.addProperty("update_interval", entityType.updateInterval())
            entityJson.addProperty("track_deltas", entityType.trackDeltas())

            val dimension = JsonArray()
            dimension.add(entityType.width)
            dimension.add(entityType.height)
            entityJson.add("dimension", dimension)
            entityJson.addProperty("eye_height", entityType.dimensions.eyeHeight)
            entityJson.addProperty("spawn_dimensions_scale", entityType.spawnDimensionsScale)

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

    private fun getDeathSound(entity: LivingEntity) =
        getDeathSoundMethod.invoke(entity) as? net.minecraft.sounds.SoundEvent

    companion object {
        private val TARGET_SOUND_BLACKLIST = setOf(
            "slime",
            "copper_golem"
        )

        private val getHurtSoundMethod = LivingEntity::class.java
            .getDeclaredMethod("getHurtSound", DamageSource::class.java)
            .apply { isAccessible = true }
        private val getDeathSoundMethod = LivingEntity::class.java
            .getDeclaredMethod("getDeathSound")
            .apply { isAccessible = true }
    }
}
