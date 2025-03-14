package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.registry.*
import net.minecraft.server.MinecraftServer
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList.Preset
import net.minecraft.world.biome.source.util.MultiNoiseUtil

/**
 * An extractor for MultiNoiseBiomeSourceParameterList that fully serializes NoiseHypercube and ParameterRange data.
 */
class MultiNoise : Extractor.Extractor {

    override fun fileName(): String {
        return "multi_noise.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val registryManager: DynamicRegistryManager.Immutable = server.registryManager
        val multiNoiseRegistry: Registry<MultiNoiseBiomeSourceParameterList> =
            registryManager.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
        val BIOME_KEY_CODEC = RegistryKey.createCodec(RegistryKeys.BIOME).fieldOf("biome")
        val BIOME_ENTRY_CODEC: Codec<MultiNoiseUtil.Entries<RegistryKey<Biome>>> =
            (MultiNoiseUtil.Entries.createCodec<RegistryKey<Biome>>(BIOME_KEY_CODEC)
                .fieldOf("biomes")).codec()

        val rootJson = JsonObject()
        MultiNoiseBiomeSourceParameterList.getPresetToEntriesMap()
            .forEach { (preset: Preset, entries: MultiNoiseUtil.Entries<RegistryKey<Biome>>) ->
                rootJson.add(
                    preset.id.path, BIOME_ENTRY_CODEC.encodeStart(
                        RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                        entries
                    ).getOrThrow()
                )
            }


        return rootJson
    }


}
