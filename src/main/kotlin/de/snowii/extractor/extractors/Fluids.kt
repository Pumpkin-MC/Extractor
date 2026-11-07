package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.Fluid

class Fluids : Extractor.Extractor {
    override fun fileName(): String {
        return "fluids.json"
    }

    // Spread tuning in a level
    private fun flowJson(fluid: Fluid, level: LevelReader): JsonObject {
        val flowJson = JsonObject()
        flowJson.addProperty("tick_delay", fluid.getTickDelay(level))
        if (fluid is FlowingFluid) {
            flowJson.addProperty("drop_off", fluid.getDropOff(level))
            flowJson.addProperty("slope_find_distance", fluid.getSlopeFindDistance(level))
        }
        return flowJson
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonArray()
        // LavaFluid reads the FAST_LAVA dimension attribute
        val byFastLava = server.allLevels.groupBy {
            it.environmentAttributes().getDimensionValue(EnvironmentAttributes.FAST_LAVA)
        }
        val normalLevel = byFastLava.getValue(false).first()
        val fastLavaLevel = byFastLava.getValue(true).first()

        for (fluid in BuiltInRegistries.FLUID) {
            val fluidJson = JsonObject()
            fluidJson.addProperty("id", BuiltInRegistries.FLUID.getId(fluid))
            fluidJson.addProperty("name", BuiltInRegistries.FLUID.getKey(fluid).path)

            val propsJson = JsonArray()
            for (prop in fluid.stateDefinition.properties) {
                val propJson = JsonObject()
                propJson.addProperty("name", prop.name)
                val valuesJson = JsonArray()
                for (value in prop.possibleValues) {
                    valuesJson.add(value.toString().lowercase())
                }
                propJson.add("values", valuesJson)
                propsJson.add(propJson)
            }
            fluidJson.add("properties", propsJson)
            fluidJson.add("flow", flowJson(fluid, normalLevel))
            fluidJson.add("fast_lava_flow", flowJson(fluid, fastLavaLevel))

            val statesJson = JsonArray()
            for ((index, state) in fluid.stateDefinition.possibleStates.withIndex()) {
                val stateJson = JsonObject()
                stateJson.addProperty("height", state.ownHeight)
                stateJson.addProperty("level", state.amount)
                stateJson.addProperty("is_empty", state.isEmpty)
                stateJson.addProperty("blast_resistance", state.explosionResistance)
                stateJson.addProperty("block_state_id", Block.getId(state.createLegacyBlock()))
                stateJson.addProperty("is_still", state.isSource)

                if (fluid.defaultFluidState() == state) {
                    fluidJson.addProperty("default_state_index", index)
                }

                statesJson.add(stateJson)
            }
            fluidJson.add("states", statesJson)

            topLevelJson.add(fluidJson)
        }

        return topLevelJson
    }
}