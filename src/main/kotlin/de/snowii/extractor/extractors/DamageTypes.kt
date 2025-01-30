package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryOps
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.entity.damage.DamageType

class DamageTypes : Extractor.Extractor {
    override fun fileName(): String {
        return "damage_type.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val damageTypesJson = JsonObject()
        val damageTypeRegistry = server.registryManager.getOrThrow(RegistryKeys.DAMAGE_TYPE)
        for (type in damageTypeRegistry) {
            val json = JsonObject()
            json.addProperty("id", damageTypeRegistry.getRawId(type)!!)
            json.add(
                    "components",
                    DamageType.CODEC
                            .encodeStart(
                                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                                    type
                            )
                            .getOrThrow()
            )
            damageTypesJson.add(damageTypeRegistry.getId(type)!!.path, json)
        }

        return damageTypesJson
    }
}
