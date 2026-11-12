package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.fabricmc.fabric.api.entity.FakePlayer
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityAttachment
import net.minecraft.world.entity.EntityAttachments
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntitySpawnRequest
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.SpawnPlacementTypes
import net.minecraft.world.entity.SpawnPlacements
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.attributes.DefaultAttributes
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.animal.camel.Camel
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.phys.Vec3

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

            // Ignore the spawn checks so peaceful difficulty doesn't drop the monsters.
            val entity = entityType.create(server.overworld(), EntitySpawnRequest(EntitySpawnReason.NATURAL, true))
            val poseEntity = entity ?: if (entityType == EntityTypes.PLAYER) FakePlayer.get(server.overworld()) else null
            poseEntity?.let(::poseDimensions)?.let { entityJson.add("poses", it) }
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
                        if (deathSoundId != null) {
                            entityJson.addProperty("death_sound", deathSoundId)
                        }
                    }

                    val fallSounds = entity.fallSounds
                    entityJson.addProperty("small_fall_sound", soundName(fallSounds.small()))
                    entityJson.addProperty("big_fall_sound", soundName(fallSounds.big()))
                }
                if (entity is Mob) {
                    getAmbientSound(entity)?.let { entityJson.addProperty("ambient_sound", soundName(it)) }
                }
                if (entity is AbstractHorse) {
                    addHorseSounds(entity, entityJson)
                }
                if (entity is AbstractBoat) {
                    val boat = JsonObject()
                    boat.addProperty("ride_height", rideHeightMethod.invoke(entity, entity.getDimensions(entity.pose)) as Double)
                    boat.addProperty("single_passenger_x_offset", getSinglePassengerXOffsetMethod.invoke(entity) as Float)
                    entityJson.add("boat", boat)
                }
                if (entity is Camel) {
                    entityJson.addProperty("sit_sound", soundName(getSitDownSoundMethod.invoke(entity) as SoundEvent))
                    entityJson.addProperty("stand_sound", soundName(getStandUpSoundMethod.invoke(entity) as SoundEvent))
                    entityJson.addProperty("dash_sound", soundName(getDashingSoundMethod.invoke(entity) as SoundEvent))
                    entityJson.addProperty("dash_ready_sound", soundName(getDashReadySoundMethod.invoke(entity) as SoundEvent))
                    @Suppress("UNCHECKED_CAST")
                    val saddleSound = getSaddleSoundMethod.invoke(entity) as Holder<SoundEvent>
                    entityJson.addProperty("saddle_sound", soundName(saddleSound.value()))
                }
                entityJson.addProperty("sound_source", entity.soundSource.name)
                entityJson.addProperty("movement_emission", getMovementEmission(entity).name)
                getSwimSound(entity)?.let { entityJson.addProperty("swim_sound", soundName(it)) }
                entityJson.addProperty("swim_splash_sound", soundName(getSwimSplashSound(entity)))
                entityJson.addProperty("swim_high_speed_splash_sound", soundName(getSwimHighSpeedSplashSound(entity)))
                entityJson.addProperty("attackable", entity.isAttackable)
                entityJson.addProperty("mob", entity is Mob)
                entityJson.addProperty("animal", entity is Animal)
                entityJson.addProperty("limit_per_chunk", (entity as? Mob)?.maxSpawnClusterSize ?: 0)

                // Size and attachments of mobs that can be babies
                if (entity is Mob) {
                    entity.setBaby(true)
                    if (entity.isBaby) {
                        val baby = dimensionsJson(entity.getDimensions(Pose.STANDING))
                        poseDimensions(entity)?.let { baby.add("poses", it) }
                        if (entity is AbstractHorse) {
                            addHorseSounds(entity, baby)
                        }
                        entityJson.add("baby", baby)
                    }
                }
            }
            entityJson.addProperty("is_enemy", entity is Enemy)

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

            val attachments = entityType.dimensions.attachments()
            entityJson.add("passenger_attachments", attachmentPoints(attachments, EntityAttachment.PASSENGER))
            entityJson.add("vehicle_attachment", point(attachments.get(EntityAttachment.VEHICLE, 0, 0.0f)))

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

    private fun dimensionsJson(dimensions: EntityDimensions): JsonObject {
        val json = JsonObject()
        val size = JsonArray()
        size.add(dimensions.width())
        size.add(dimensions.height())
        json.add("dimension", size)
        json.addProperty("eye_height", dimensions.eyeHeight())
        val attachments = dimensions.attachments()
        json.add("passenger_attachments", attachmentPoints(attachments, EntityAttachment.PASSENGER))
        json.add("vehicle_attachment", point(attachments.get(EntityAttachment.VEHICLE, 0, 0.0f)))
        return json
    }

    private fun poseDimensions(entity: Entity): JsonObject? {
        val standing = dimensionsJson(entity.getDimensions(Pose.STANDING))
        val poses = JsonObject()
        for (pose in Pose.entries) {
            val dimensions = dimensionsJson(entity.getDimensions(pose))
            if (dimensions != standing) {
                poses.add(pose.name.lowercase(), dimensions)
            }
        }
        return if (poses.size() == 0) null else poses
    }

    private fun attachmentPoints(attachments: EntityAttachments, attachment: EntityAttachment): JsonArray {
        val points = JsonArray()
        var index = 0
        while (true) {
            val point = attachments.getNullable(attachment, index, 0.0f) ?: break
            points.add(point(point))
            index++
        }
        return points
    }

    private fun point(point: Vec3): JsonArray {
        val array = JsonArray()
        array.add(point.x)
        array.add(point.y)
        array.add(point.z)
        return array
    }

    private fun getHurtSound(entity: LivingEntity, damageSource: DamageSource) =
        getHurtSoundMethod.invoke(entity, damageSource) as? net.minecraft.sounds.SoundEvent

    private fun getDeathSound(entity: LivingEntity) =
        getDeathSoundMethod.invoke(entity) as? net.minecraft.sounds.SoundEvent

    private fun addHorseSounds(horse: AbstractHorse, json: JsonObject) {
        (getEatingSoundMethod.invoke(horse) as? SoundEvent)?.let { json.addProperty("eating_sound", soundName(it)) }
        (getAngrySoundMethod.invoke(horse) as? SoundEvent)?.let { json.addProperty("angry_sound", soundName(it)) }
    }

    private fun getAmbientSound(entity: Mob) =
        getAmbientSoundMethod.invoke(entity) as? net.minecraft.sounds.SoundEvent

    private fun getMovementEmission(entity: Entity) =
        getMovementEmissionMethod.invoke(entity) as Entity.MovementEmission

    private fun getSwimSound(entity: Entity) =
        getSwimSoundMethod.invoke(entity) as? net.minecraft.sounds.SoundEvent

    private fun getSwimSplashSound(entity: Entity) =
        getSwimSplashSoundMethod.invoke(entity) as net.minecraft.sounds.SoundEvent

    private fun getSwimHighSpeedSplashSound(entity: Entity) =
        getSwimHighSpeedSplashSoundMethod.invoke(entity) as net.minecraft.sounds.SoundEvent

    private fun soundName(sound: net.minecraft.sounds.SoundEvent): String =
        BuiltInRegistries.SOUND_EVENT.getKey(sound)?.path ?: error("Unregistered entity sound ${sound.location()}")

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
        private val getMovementEmissionMethod = Entity::class.java
            .getDeclaredMethod("getMovementEmission")
            .apply { isAccessible = true }
        private val getAmbientSoundMethod = Mob::class.java
            .getDeclaredMethod("getAmbientSound")
            .apply { isAccessible = true }
        private val getEatingSoundMethod = AbstractHorse::class.java
            .getDeclaredMethod("getEatingSound")
            .apply { isAccessible = true }
        private val getAngrySoundMethod = AbstractHorse::class.java
            .getDeclaredMethod("getAngrySound")
            .apply { isAccessible = true }
        private val rideHeightMethod = AbstractBoat::class.java
            .getDeclaredMethod("rideHeight", EntityDimensions::class.java)
            .apply { isAccessible = true }
        private val getSinglePassengerXOffsetMethod = AbstractBoat::class.java
            .getDeclaredMethod("getSinglePassengerXOffset")
            .apply { isAccessible = true }
        private val getSitDownSoundMethod = Camel::class.java
            .getDeclaredMethod("getSitDownSound")
            .apply { isAccessible = true }
        private val getStandUpSoundMethod = Camel::class.java
            .getDeclaredMethod("getStandUpSound")
            .apply { isAccessible = true }
        private val getDashingSoundMethod = Camel::class.java
            .getDeclaredMethod("getDashingSound")
            .apply { isAccessible = true }
        private val getDashReadySoundMethod = Camel::class.java
            .getDeclaredMethod("getDashReadySound")
            .apply { isAccessible = true }
        private val getSaddleSoundMethod = Camel::class.java
            .getDeclaredMethod("getSaddleSound")
            .apply { isAccessible = true }
        private val getSwimSoundMethod = Entity::class.java
            .getDeclaredMethod("getSwimSound")
            .apply { isAccessible = true }
        private val getSwimSplashSoundMethod = Entity::class.java
            .getDeclaredMethod("getSwimSplashSound")
            .apply { isAccessible = true }
        private val getSwimHighSpeedSplashSoundMethod = Entity::class.java
            .getDeclaredMethod("getSwimHighSpeedSplashSound")
            .apply { isAccessible = true }
    }
}
