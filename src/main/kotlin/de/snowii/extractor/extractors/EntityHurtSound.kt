package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.LivingEntity

class EntityHurtSound : Extractor.Extractor {
    override fun fileName(): String = "entity_hurt_sound.json"

    override fun extract(server: MinecraftServer): JsonElement {
        val hurtSoundsJson = JsonObject()
        val level = server.overworld()
        val damageSource = level.damageSources().generic()

        for (entityName in TARGET_ENTITYS) {
            val entityType = BuiltInRegistries.ENTITY_TYPE.getValue(entityId(entityName))
            val entity = entityType.create(level, EntitySpawnReason.NATURAL) ?: continue
            if (entity !is LivingEntity) continue

            val hurtSound = getHurtSound(entity, damageSource) ?: continue
            val hurtSoundId = BuiltInRegistries.SOUND_EVENT.getKey(hurtSound)?.path ?: continue

            hurtSoundsJson.addProperty(entityName, hurtSoundId)
        }

        return hurtSoundsJson
    }

    private fun entityId(entityName: String) = Identifier.withDefaultNamespace(entityName)

    private fun getHurtSound(entity: LivingEntity, damageSource: DamageSource) =
        getHurtSoundMethod.invoke(entity, damageSource) as? net.minecraft.sounds.SoundEvent

    companion object {
        private val TARGET_ENTITYS = listOf(
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
