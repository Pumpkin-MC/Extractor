package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.enchantment.Enchantment
import net.minecraft.network.message.MessageType
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer

class Enchantments : Extractor.Extractor {
    override fun fileName(): String {
        return "enchantments.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val finalJson = JsonObject()
        val registry =
            server.registryManager.getOrThrow(RegistryKeys.ENCHANTMENT)
        for (enchantment in registry) {
            finalJson.add(
                registry.getId(enchantment)!!.toString(), Enchantment.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager), enchantment
                ).getOrThrow()
            )
        }
        return finalJson
    }
}