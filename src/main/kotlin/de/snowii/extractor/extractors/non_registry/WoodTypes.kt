package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.properties.WoodType
import java.lang.reflect.Modifier

class WoodTypes : Extractor.Extractor {
    override fun fileName(): String {
        return "wood_types.json"
    }

    // Blocks reference sound types by identity, so use the same constant names as blocks.json.
    private val soundTypeNames: Map<SoundType, String> = SoundType::class.java.fields
        .filter { Modifier.isStatic(it.modifiers) && it.type == SoundType::class.java }
        .associate { it.get(null) as SoundType to it.name }

    private fun soundTypeName(soundType: SoundType): String =
        soundTypeNames[soundType] ?: error("Unregistered block sound type ${soundType.breakSound.location()}")

    private fun soundName(sound: SoundEvent): String =
        BuiltInRegistries.SOUND_EVENT.getKey(sound)?.path ?: error("Unregistered wood sound ${sound.location()}")

    override fun extract(server: MinecraftServer): JsonElement {
        val array = JsonArray()
        for (type in WoodType.values()) {
            val json = JsonObject()
            json.addProperty("name", type.name())
            json.addProperty("set_type", type.setType().name())
            json.addProperty("sound_type", soundTypeName(type.soundType()))
            json.addProperty("hanging_sign_sound_type", soundTypeName(type.hangingSignSoundType()))
            json.addProperty("fence_gate_close", soundName(type.fenceGateClose()))
            json.addProperty("fence_gate_open", soundName(type.fenceGateOpen()))
            array.add(json)
        }
        return array
    }
}
