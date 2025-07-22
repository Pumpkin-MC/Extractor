package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.recipe.Ingredient
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.MinecraftServer
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import java.util.Optional


class Effect : Extractor.Extractor {
    override fun fileName(): String {
        return "effect.json"
    }


    override fun extract(server: MinecraftServer): JsonElement {
        val json = JsonObject()
        for (potion in server.registryManager.getOrThrow(RegistryKeys.STATUS_EFFECT).streamEntries().toList()) {
            val itemJson = JsonObject()
            val realPotion = potion.value()
            itemJson.addProperty("id", Registries.STATUS_EFFECT.getRawId(realPotion))
            itemJson.addProperty("category", realPotion.category.toString())
            itemJson.addProperty("color", realPotion.color)
            if (realPotion.fadeInTicks != 0 || realPotion.fadeOutTicks != 0 || realPotion.fadeOutThresholdTicks != 0) {
                itemJson.addProperty("fade_in_ticks", realPotion.fadeInTicks)
                itemJson.addProperty("fade_out_ticks", realPotion.fadeOutTicks)
                itemJson.addProperty("fade_out_threshold_ticks", realPotion.fadeOutThresholdTicks)
            }
            itemJson.addProperty("translation_key", realPotion.translationKey)

            val t3 = StatusEffect::class.java.getDeclaredField("applySound")
            t3.isAccessible = true
            val applySound = t3.get(realPotion) as? Optional<SoundEvent>
            applySound?.ifPresent { soundEvent -> itemJson.addProperty("apply_sound", Registries.SOUND_EVENT.getId(soundEvent)!!.path)}

            val t2 = StatusEffect::class.java.getDeclaredField("attributeModifiers")
            t2.isAccessible = true
            val attributeModifiers = t2.get(realPotion) as? Map<RegistryEntry<EntityAttribute>, *>
            if (attributeModifiers != null) {
                val arr = JsonArray()
                for ((key, value) in attributeModifiers) {
                    val obj = JsonObject()
                    obj.addProperty("attribute", Registries.ATTRIBUTE.getId(key.value())!!.path)
                    val clazz = value?.javaClass
                    clazz?.let {
                        for (field in it.declaredFields) {
                            field.isAccessible = true
                            val value = field.get(value)
                            if (value is Identifier) {
                                obj.addProperty(field.name, value.toString())
                            } else if (value is Double) {
                                obj.addProperty(field.name, value)
                            } else if (value is EntityAttributeModifier.Operation) {
                                obj.addProperty(field.name, value.toString())
                            }
                        }
                    }
                    arr.add(obj)
                }
                itemJson.add("attribute_modifiers", arr)
            }


            Registries.STATUS_EFFECT.getId(realPotion)?.let { json.add(it.path, itemJson) }
        }
        return json
    }
}
