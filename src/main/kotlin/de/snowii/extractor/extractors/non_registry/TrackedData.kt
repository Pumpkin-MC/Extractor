package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.entity.Entity
import net.minecraft.entity.data.TrackedData
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer

class TrackedData : Extractor.Extractor {
    override fun fileName(): String {
        return "tracked_data.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val world = server.overworld
        val result = JsonObject()

        Registries.ENTITY_TYPE.forEach { entityType ->
            val entityInstance: Entity? = try {
                entityType.create(world, null)
            } catch (e: Exception) {
                null
            }

            if (entityInstance != null) {
                var currentClass: Class<*>? = entityInstance.javaClass

                while (currentClass != null && Entity::class.java.isAssignableFrom(currentClass)) {
                    for (field in currentClass.declaredFields) {
                        if (field.type == TrackedData::class.java) {
                            try {
                                field.isAccessible = true
                                val trackedData = field.get(null) as TrackedData<*>

                                result.addProperty(field.name, trackedData.id())
                            } catch (e: Exception) {
                            }
                        }
                    }
                    currentClass = currentClass.superclass
                }
                entityInstance.discard()
            }
        }

        return result
    }


}