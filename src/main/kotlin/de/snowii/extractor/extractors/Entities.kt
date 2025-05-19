package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.InventoryOwner
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.ai.brain.Activity
import net.minecraft.entity.ai.brain.Brain
import net.minecraft.entity.ai.brain.MemoryModuleType
import net.minecraft.entity.ai.brain.sensor.Sensor
import net.minecraft.entity.ai.brain.sensor.SensorType
import net.minecraft.entity.ai.brain.task.Task
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.loot.LootTable
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryOps
import com.mojang.authlib.GameProfile
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.network.packet.c2s.common.SyncedClientOptions
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.lang.reflect.ParameterizedType
import java.util.UUID

class Entities : Extractor.Extractor {
    override fun fileName(): String {
        return "entities.json"
    }

    fun detailedStringifyTask(task: Task<*>): String {
        var type_name:String;
        if(task.javaClass.simpleName.isEmpty())
            type_name = "OneShot";
        else
            type_name = task.javaClass.simpleName;
        return type_name +
                "(" +
                task::class.java.declaredFields.joinToString(", ") { field ->
                    field.isAccessible = true // Allow access to private fields
                    "${field.name}: ${field.get(task)}"
                } +
                ")"
    }

    fun extractEntityBrainTasks(entity: LivingEntity): JsonArray {
        val tasksJson = JsonArray()
        val brain: Brain<*> = entity.brain
        try {
            val tasksField = brain.javaClass.getDeclaredField("tasks")
            tasksField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val tasksMap = tasksField.get(brain) as? Map<Int, Map<Activity, Set<Task<*>>>>
            tasksMap?.forEach { (priority, activityMap) ->
                activityMap.forEach { (activity, taskSet) ->
                    tasksJson.add(
                            JsonObject().apply {
                                addProperty("activity", activity.toString())
                                addProperty("priority", priority)
                                val taskSetJson = JsonArray()
                                taskSet.forEach { task ->
                                    taskSetJson.add(detailedStringifyTask(task))
                                }
                                add("tasks", taskSetJson)
                            }
                    )
                }
            }
        } catch (e: NoSuchFieldException) {
            println("Error: 'tasks' field not found in Brain class.")
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            println("Error: Could not access 'tasks' field in Brain class.")
            e.printStackTrace()
        }
        return tasksJson
    }

    fun extractEntityBrainSensors(entity: LivingEntity): JsonArray {
        val sensorsJson = JsonArray()
        val brain: Brain<*> = entity.brain
        try {
            val sensorsField = brain.javaClass.getDeclaredField("sensors")
            sensorsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val sensorsMap = sensorsField.get(brain) as? Map<SensorType<out Sensor<*>>, Sensor<*>>
            sensorsMap?.forEach { (sensor, _) ->sensorsJson.add(Registries.SENSOR_TYPE.getId(sensor).toString())}
        } catch (e: NoSuchFieldException) {
            println("Error: 'sensors' field not found in Brain class.")
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            println("Error: Could not access 'sensors' field in Brain class.")
            e.printStackTrace()
        }
        return sensorsJson
    }

    fun extractEntityBrainMemory(entity: LivingEntity): JsonArray {
        val sensorsJson = JsonArray()
        val brain: Brain<*> = entity.brain
        try {
            val memoriesField = brain.javaClass.getDeclaredField("memories")
            memoriesField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val memoriesMap = memoriesField.get(brain) as? Map<MemoryModuleType<*>, *>
            memoriesMap?.forEach { (memoryType, _) -> sensorsJson.add(memoryType.toString()) }
        } catch (e: NoSuchFieldException) {
            println("Error: 'memories' field not found in Brain class.")
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            println("Error: Could not access 'memories' field in Brain class.")
            e.printStackTrace()
        }
        return sensorsJson
    }

    fun extractMetadataForClass(entityClass: Class<out Entity>): JsonArray {
        val metadataJson = JsonArray()
        val alreadyProcessedObjects: MutableSet<TrackedData<*>> = mutableSetOf()

        var currentClassInHierarchy: Class<*>? = entityClass
        while (currentClassInHierarchy != null && Entity::class.java.isAssignableFrom(currentClassInHierarchy)) {
            for (field in currentClassInHierarchy.declaredFields) {
                if (Modifier.isStatic(field.modifiers) && TrackedData::class.java.isAssignableFrom(field.type)) {
                    try {
                        field.isAccessible = true
                        val td = field.get(null) as? TrackedData<*>
                        if (td != null && alreadyProcessedObjects.add(td)) {
                            val metadataEntry = JsonObject()

                            metadataEntry.addProperty("field_name", field.name)
                            metadataEntry.addProperty("network_id", td.id)

                            var genericTypeName = "Unknown"
                            val genericFieldType: Type = field.genericType
                            if (genericFieldType is ParameterizedType) {
                                val actualTypeArguments = genericFieldType.actualTypeArguments
                                if (actualTypeArguments.isNotEmpty()) 
                                    genericTypeName = actualTypeArguments[0].typeName
                                        .replace("java.lang.", "")
                                        .replace("java.util.", "")
                                        .replace("org.joml.", "")
                                        .replace("net.minecraft.", "")
                                        .replace("com.mojang.", "")
                                        .replace("util.math.", "")
                                        .replace("text.", "")
                                        .replace("particle.", "")
                                        .replace("entity.", "")
                                        .replace("block.", "")
                                        .replace("passive.", "")
                                        .replace("village.", "")
                                        .replace("registry.entry.", "")
                                        .replace("item.", "")
                                        .replace("decoration.painting.", "")
                                        .replace("nbt.", "")
                            }
                            metadataEntry.addProperty("type_name", genericTypeName)
                            metadataJson.add(metadataEntry)
                        }
                    } catch (e: IllegalAccessException) {
                        println("Could not access field: ${field.name} in ${currentClassInHierarchy.name} ${e.message}")
                    } catch (e: Exception) {
                        println("Error processing field: ${field.name} in ${currentClassInHierarchy.name} ${e.message}")
                    }
                }
            }
            currentClassInHierarchy = currentClassInHierarchy.superclass
        }
        return metadataJson
    }

    fun createEntity(entityType:EntityType<*>, server: MinecraftServer): Entity? {
        if(entityType == EntityType.PLAYER){
            val FAKE_PLAYER_UUID_STRING = "6d6d6d6d-6d6d-6d6d-6d6d-6d6d6d6d6d6d"
            val FAKE_PLAYER_UUID: UUID = UUID.fromString(FAKE_PLAYER_UUID_STRING)
            val FAKE_PLAYER_NAME = "[FakePlayer]Extractor"
            val gameProfile = GameProfile(FAKE_PLAYER_UUID, FAKE_PLAYER_NAME)
            val syncedClientOptions = SyncedClientOptions.createDefault()
            return ServerPlayerEntity(server, server.overworld, gameProfile, syncedClientOptions)
        }
        else return entityType.create(server.overworld!!, SpawnReason.NATURAL)
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val entitiesJson = JsonObject()
        for (entityType in Registries.ENTITY_TYPE) {
            val entityJson = JsonObject()
            entityJson.addProperty("id", Registries.ENTITY_TYPE.getRawId(entityType))
            entityJson.addProperty("translation_key", entityType.translationKey)
            val entity = createEntity(entityType, server)
            if (entity != null) {
                if (entity is LivingEntity) {
                    entityJson.addProperty("max_health", entity.maxHealth)
                    entityJson.add("brain_tasks", extractEntityBrainTasks(entity))
                    entityJson.add("brain_sensors", extractEntityBrainSensors(entity))
                    entityJson.add("brain_memories", extractEntityBrainMemory(entity))
                    if(entity.canAvoidTraps())
                        entityJson.addProperty("can_avoid_traps", true)
                    if(entity.maxAir != 300)
                        entityJson.addProperty("max_air", entity.maxAir)
                    if(entity is InventoryOwner){
                        entity.inventory?.let { inventory ->
                            entityJson.addProperty("inventory_size", inventory.size())
                        }
                    }
                }
                entityJson.addProperty("attackable", entity.isAttackable)
                entityJson.addProperty("step_height", entity.stepHeight)
                entityJson.addProperty("can_freeze", entity.canFreeze())
                entityJson.addProperty("can_hit", entity.canHit())
                entityJson.addProperty("is_collidable", entity.isCollidable)
                if(!entity.canBeHitByProjectile())
                    entityJson.addProperty("can_be_hit_by_projectile", false)
                entityJson.add("metadata", extractMetadataForClass(entity.javaClass))
            }
            entityJson.addProperty("summonable", entityType.isSummonable)
            entityJson.addProperty("fire_immune", entityType.isFireImmune)
            entityJson.addProperty("saveable", entityType.isSaveable)
            entityJson.addProperty("spawnable_far_from_player", entityType.isSpawnableFarFromPlayer)
            entityJson.addProperty("max_track_distance", entityType.maxTrackDistance)
            entityJson.addProperty("track_tick_interval", entityType.trackTickInterval)
            entityJson.addProperty("spawn_group", entityType.spawnGroup.toString())

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

            entitiesJson.add(
                Registries.ENTITY_TYPE.getId(entityType).path, entityJson
            )
        }

        return entitiesJson
    }
}
