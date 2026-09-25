package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import java.util.*

class Effect : Extractor.Extractor {
    override fun fileName(): String {
        return "effect.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val json = JsonObject()
        val registryAccess = server.registryAccess()
        val ops = registryAccess.createSerializationContext(JsonOps.INSTANCE)
        val registry = registryAccess.lookupOrThrow(Registries.MOB_EFFECT)

        for (effect in BuiltInRegistries.MOB_EFFECT) {
            val itemJson = JsonObject()

            itemJson.addProperty("id", BuiltInRegistries.MOB_EFFECT.getId(effect))
            itemJson.addProperty("category", effect.category.name)
            itemJson.addProperty("color", effect.color)

            if (effect.blendInDurationTicks != 0 || effect.blendOutDurationTicks != 0 || effect.blendOutAdvanceTicks != 0) {
                itemJson.addProperty("fade_in_ticks", effect.blendInDurationTicks)
                itemJson.addProperty("fade_out_ticks", effect.blendOutDurationTicks)
                itemJson.addProperty("fade_out_threshold_ticks", effect.blendOutAdvanceTicks)
            }

            itemJson.addProperty("translation_key", effect.descriptionId)

            val soundField = MobEffect::class.java.getDeclaredField("soundOnAdded")
            soundField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val soundOpt = soundField.get(effect) as? Optional<SoundEvent>
            soundOpt?.ifPresent { soundEvent ->
                val soundKey = BuiltInRegistries.SOUND_EVENT.getKey(soundEvent)
                itemJson.addProperty("apply_sound", soundKey?.toString())
            }

            val attributeModifiersJson = JsonArray()
            effect.createModifiers(0) { attributeHolder, modifier ->
                val modJson = AttributeModifier.CODEC.encodeStart(ops, modifier).getOrThrow().asJsonObject
                modJson.addProperty(
                    "attribute",
                    attributeHolder.unwrapKey().get().identifier().path
                )
                attributeModifiersJson.add(modJson)
            }
            itemJson.add("attribute_modifiers", attributeModifiersJson)

            val effectKey = BuiltInRegistries.MOB_EFFECT.getKey(effect)
            json.add(effectKey!!.path, itemJson)
        }

        return json
    }
}