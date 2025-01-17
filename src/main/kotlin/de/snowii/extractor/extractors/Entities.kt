package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.DefaultAttributeRegistry
import net.minecraft.registry.Registries
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

            entitiesJson.add(
                Registries.ENTITY_TYPE.getId(entityType).path, entityJson
            )
        }

        return entitiesJson
    }
}
