package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.monster.warden.WardenAi
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity
import java.lang.reflect.Modifier

class Warden : Extractor.Extractor {
    override fun fileName(): String {
        return "warden.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val json = JsonObject()
        json.add("warden", constants(Warden::class.java))
        json.add("warden_ai", constants(WardenAi::class.java))
        json.add("spawn_tracker", constants(WardenSpawnTracker::class.java))

        val shrieker = constants(SculkShriekerBlockEntity::class.java)
        val soundField = SculkShriekerBlockEntity::class.java.getDeclaredField("SOUND_BY_LEVEL")
        soundField.isAccessible = true
        val sounds = JsonObject()
        for ((level, sound) in (soundField.get(null) as Map<*, *>).entries.sortedBy { it.key as Int }) {
            sounds.addProperty(
                level.toString(),
                BuiltInRegistries.SOUND_EVENT.getKey(sound as SoundEvent)!!.path
            )
        }
        shrieker.add("SOUND_BY_LEVEL", sounds)
        json.add("sculk_shrieker", shrieker)

        return json
    }

    private fun constants(clazz: Class<*>): JsonObject {
        val json = JsonObject()
        for (field in clazz.declaredFields) {
            if (!Modifier.isStatic(field.modifiers) || !Modifier.isFinal(field.modifiers)) continue
            if (!field.type.isPrimitive) continue
            field.isAccessible = true
            when (val value = field.get(null)) {
                is Boolean -> json.addProperty(field.name, value)
                is Number -> json.addProperty(field.name, value)
            }
        }
        return json
    }
}
