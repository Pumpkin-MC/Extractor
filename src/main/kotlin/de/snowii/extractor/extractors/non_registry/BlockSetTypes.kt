package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.properties.BlockSetType
import java.lang.reflect.Modifier

class BlockSetTypes : Extractor.Extractor {
    override fun fileName(): String {
        return "block_set_types.json"
    }

    // Blocks reference sound types by identity, so use the same constant names as blocks.json.
    private val soundTypeNames: Map<SoundType, String> = SoundType::class.java.fields
        .filter { Modifier.isStatic(it.modifiers) && it.type == SoundType::class.java }
        .associate { it.get(null) as SoundType to it.name }

    private fun soundTypeName(soundType: SoundType): String =
        soundTypeNames[soundType] ?: error("Unregistered block sound type ${soundType.breakSound.location()}")

    private fun soundName(sound: SoundEvent): String =
        BuiltInRegistries.SOUND_EVENT.getKey(sound)?.path ?: error("Unregistered block set sound ${sound.location()}")

    override fun extract(server: MinecraftServer): JsonElement {
        val array = JsonArray()
        for (type in BlockSetType.values()) {
            val json = JsonObject()
            json.addProperty("name", type.name())
            json.addProperty("can_open_by_hand", type.canOpenByHand())
            json.addProperty("can_open_by_wind_charge", type.canOpenByWindCharge())
            json.addProperty("can_button_be_activated_by_arrows", type.canButtonBeActivatedByArrows())
            json.addProperty("pressure_plate_sensitivity", type.pressurePlateSensitivity().name.lowercase())
            json.addProperty("sound_type", soundTypeName(type.soundType()))
            json.addProperty("door_close", soundName(type.doorClose()))
            json.addProperty("door_open", soundName(type.doorOpen()))
            json.addProperty("trapdoor_close", soundName(type.trapdoorClose()))
            json.addProperty("trapdoor_open", soundName(type.trapdoorOpen()))
            json.addProperty("pressure_plate_click_off", soundName(type.pressurePlateClickOff()))
            json.addProperty("pressure_plate_click_on", soundName(type.pressurePlateClickOn()))
            json.addProperty("button_click_off", soundName(type.buttonClickOff()))
            json.addProperty("button_click_on", soundName(type.buttonClickOn()))
            array.add(json)
        }
        return array
    }
}
