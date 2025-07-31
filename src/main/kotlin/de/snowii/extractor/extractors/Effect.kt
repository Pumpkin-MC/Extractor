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

            val attributesRegistry =
                server.registryManager.getOrThrow(RegistryKeys.ATTRIBUTE)

            val attributeModifiersJson = JsonArray()
            realPotion.forEachAttributeModifier(0) { reg, mod ->
                val potionJson = JsonObject()
                potionJson.addProperty("attribute", attributesRegistry.getId(reg.value())!!.path)
                potionJson.addProperty("operation", mod.operation.toString())
                potionJson.addProperty("id", mod.id.toString())
                potionJson.addProperty("baseValue", mod.value)
                attributeModifiersJson.add(potionJson)
            }
            itemJson.add("attribute_modifiers", attributeModifiersJson)

            Registries.STATUS_EFFECT.getId(realPotion)?.let { json.add(it.path, itemJson) }
        }
        return json
    }
}
