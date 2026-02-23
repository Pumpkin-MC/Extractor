package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.dialog.type.Dialog
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer

class Dialog : Extractor.Extractor {
    override fun fileName(): String {
        return "dialog.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val finalJson = JsonObject()
        val registry =
            server.registryManager.getOrThrow(RegistryKeys.DIALOG)
        for (dialog in registry) {
            val sub = Dialog.CODEC.encodeStart(
                RegistryOps.of(JsonOps.INSTANCE, server.registryManager), dialog
            ).getOrThrow() as JsonObject
            sub.addProperty("id", registry.getRawId(dialog))
            finalJson.add(
                registry.getId(dialog)!!.toString(), sub
            )
        }
        return finalJson
    }
}