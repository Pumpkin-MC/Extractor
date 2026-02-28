package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.item.Instrument
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer

class Instruments : Extractor.Extractor {
    override fun fileName(): String {
        return "instruments.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val instruments = JsonObject()

        for (instrument in server.registryManager.getOrThrow(RegistryKeys.INSTRUMENT).streamEntries().toList()) {
            instruments.add(
                instrument.key.get().value.toString(),
                Instrument.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                    instrument.value()
                ).getOrThrow()
            )
        }

        return instruments
    }
}