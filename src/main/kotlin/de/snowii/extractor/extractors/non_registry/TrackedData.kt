package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializer
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.FishingHook
import java.lang.reflect.Modifier

class TrackedData : Extractor.Extractor {
    override fun fileName(): String {
        return "tracked_data.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val world = server.overworld()
        val result = JsonObject()

        // Map serializer instances to their name (matching meta_data_type.json) and ID
        val serializerInfo = mutableMapOf<EntityDataSerializer<*>, Pair<String, Int>>()
        val serializerClass = EntityDataSerializers::class.java
        for (field in serializerClass.declaredFields) {
            if (Modifier.isStatic(field.modifiers) && EntityDataSerializer::class.java.isAssignableFrom(field.type)) {
                try {
                    field.isAccessible = true
                    val serializer = field.get(null) as EntityDataSerializer<*>
                    val id = EntityDataSerializers.getSerializedId(serializer)
                    if (id != -1) {
                        serializerInfo[serializer] = Pair(field.name.lowercase(), id)
                    }
                } catch (e: Exception) {
                }
            }
        }

        // Helper to extract fields for a class hierarchy
        fun extractFieldsForHierarchy(entityClass: Class<*>): JsonObject {
            val fieldsObj = JsonObject()
            val hierarchy = mutableListOf<Class<*>>()
            var curr: Class<*>? = entityClass
            while (curr != null && Entity::class.java.isAssignableFrom(curr)) {
                hierarchy.add(0, curr) // Superclasses first
                curr = curr.superclass
            }

            for (cls in hierarchy) {
                for (field in cls.declaredFields) {
                    if (field.type == EntityDataAccessor::class.java && Modifier.isStatic(field.modifiers)) {
                        try {
                            field.isAccessible = true
                            val accessor = field.get(null) as EntityDataAccessor<*>
                            val (serName, serId) = serializerInfo[accessor.serializer()] ?: Pair("unknown", -1)

                            val fieldData = JsonObject()
                            fieldData.addProperty("id", accessor.id())
                            fieldData.addProperty("type", serName)
                            fieldData.addProperty("type_id", serId)

                            fieldsObj.add(field.name, fieldData)
                        } catch (e: Exception) {
                        }
                    }
                }
            }
            return fieldsObj
        }

        val visitedClasses = mutableSetOf<Class<*>>()

        // Process all registered entity types
        BuiltInRegistries.ENTITY_TYPE.forEach { entityType ->
            val entityName = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).path

            val entityInstance: Entity? = try {
                entityType.create(world, EntitySpawnReason.TRIGGERED)
            } catch (e: Exception) {
                null
            }

            val entityClass: Class<*>? = entityInstance?.javaClass ?: when (entityName) {
                "player" -> Player::class.java
                "fishing_bobber" -> FishingHook::class.java
                else -> null
            }

            if (entityClass != null) {
                var curr: Class<*>? = entityClass
                while (curr != null && Entity::class.java.isAssignableFrom(curr)) {
                    visitedClasses.add(curr)
                    curr = curr.superclass
                }

                val fields = extractFieldsForHierarchy(entityClass)
                result.add(entityName, fields)
            }

            entityInstance?.discard()
        }

        // Also add base/abstract classes (e.g. "entity", "living_entity", "mob", "display", etc.)
        for (cls in visitedClasses) {
            val snakeName = cls.simpleName.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
            if (!result.has(snakeName)) {
                val fields = extractFieldsForHierarchy(cls)
                if (fields.size() > 0) {
                    result.add(snakeName, fields)
                }
            }
        }

        return result
    }
}