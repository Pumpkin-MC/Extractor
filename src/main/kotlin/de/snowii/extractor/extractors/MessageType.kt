package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.IExtractor
import net.minecraft.network.message.MessageType
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer

class MessageType : IExtractor {
    override fun fileName(): String {
        return "message_type.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val messagesJson = JsonObject()
        val messageTypeRegistry =
            server.registryManager.getOrThrow(RegistryKeys.MESSAGE_TYPE)
        for (type in messageTypeRegistry) {
            val json = JsonObject()
            json.addProperty("id", messageTypeRegistry.getRawId(type))
            json.add(
                "components", MessageType.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager), type
                ).getOrThrow()
            )
            messagesJson.add(
                messageTypeRegistry.getId(type)!!.path,
                json
            )
        }

        return messagesJson
    }
}