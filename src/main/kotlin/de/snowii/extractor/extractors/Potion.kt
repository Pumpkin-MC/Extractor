package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.effect.MobEffectInstance


class Potion : Extractor.Extractor {
    override fun fileName(): String {
        return "potion.json"
    }


    override fun extract(server: MinecraftServer): JsonElement {
        val json = JsonObject()
        val registryAccess = server.registryAccess()
        val ops = registryAccess.createSerializationContext(JsonOps.INSTANCE)
        val registry = registryAccess.lookupOrThrow(Registries.POTION)

        for (realPotion in registry) {
            val itemJson = JsonObject()
            val array = JsonArray()
            itemJson.addProperty("id", registry.getId(realPotion))
            itemJson.addProperty("base_name", realPotion.name())
            for (effect in realPotion.effects) {
                array.add(MobEffectInstance.CODEC.encodeStart(ops, effect).getOrThrow())
            }
            itemJson.add("effects", array)
            registry.getKey(realPotion)?.let { json.add(it.path, itemJson) }
        }
        return json
    }
}
