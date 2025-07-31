package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer


class Potion : Extractor.Extractor {
    override fun fileName(): String {
        return "potion.json"
    }


    override fun extract(server: MinecraftServer): JsonElement {
        val json = JsonObject()
        for (potion in server.registryManager.getOrThrow(RegistryKeys.POTION).streamEntries().toList()) {
            val itemJson = JsonObject()
            val realPotion = potion.value()
            val array = JsonArray()
            itemJson.addProperty("id", Registries.POTION.getRawId(realPotion))
            itemJson.addProperty("base_name", realPotion.baseName)
            for (effect in realPotion.effects) {
                val obj = JsonObject()
                obj.addProperty("effect_type", effect.effectType.key.get().value.toString())
                obj.addProperty("duration", effect.duration)
                obj.addProperty("amplifier", effect.amplifier)
                obj.addProperty("ambient", effect.isAmbient)
                obj.addProperty("show_particles", effect.shouldShowParticles())
                obj.addProperty("show_icon", effect.shouldShowIcon())
                array.add(obj)
            }
            itemJson.add("effects", array)
            Registries.POTION.getId(realPotion)?.let { json.add(it.path, itemJson) }
        }
        return json
    }
}
