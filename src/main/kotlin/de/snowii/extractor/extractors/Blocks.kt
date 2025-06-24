package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.block.Block
import net.minecraft.block.ExperienceDroppingBlock
import net.minecraft.block.SideShapeType
import net.minecraft.loot.LootTable
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction;
import net.minecraft.world.EmptyBlockView
import java.util.*

class Blocks : Extractor.Extractor {

    companion object {
        private const val AIR: Int = 0b00000001
        private const val BURNABLE: Int = 0b00000010
        private const val TOOL_REQUIRED: Int = 0b00000100
        private const val SIDED_TRANSPARENCY: Int = 0b00001000
        private const val REPLACEABLE: Int = 0b00010000
        private const val IS_LIQUID: Int = 0b00100000
        private const val IS_SOLID: Int = 0b01000000
        private const val IS_FULL_CUBE: Int = 0b10000000

        private const val DOWN_SIDE_SOLID: Int = 0b00000001;
        private const val UP_SIDE_SOLID: Int = 0b00000010;
        private const val NORTH_SIDE_SOLID: Int = 0b00000100;
        private const val SOUTH_SIDE_SOLID: Int = 0b00001000;
        private const val WEST_SIDE_SOLID: Int = 0b00010000;
        private const val EAST_SIDE_SOLID: Int = 0b00100000;
        private const val DOWN_CENTER_SOLID: Int = 0b01000000;
        private const val UP_CENTER_SOLID: Int = 0b10000000;
    }

    override fun fileName(): String {
        return "blocks.json"
    }

    private fun getFlammableData(): Map<Block, Pair<Int, Int>> {
        val flammableData = mutableMapOf<Block, Pair<Int, Int>>()
        val fireBlock = net.minecraft.block.Blocks.FIRE as net.minecraft.block.FireBlock;
        for (block in Registries.BLOCK) {
            val defaultState = block.defaultState
            val spreadChance = fireBlock.getSpreadChance(defaultState)
            val burnChance = fireBlock.getBurnChance(defaultState)
            if (spreadChance > 0 || burnChance > 0) {
                flammableData[block] = Pair(spreadChance, burnChance)
            }
        }

        return flammableData
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonObject()

        val blocksJson = JsonArray()

        val shapes: LinkedHashMap<Box, Int> = LinkedHashMap()

        val flammableData = getFlammableData()

        for (block in Registries.BLOCK) {
            val blockJson = JsonObject()
            blockJson.addProperty("id", Registries.BLOCK.getRawId(block))
            blockJson.addProperty("name", Registries.BLOCK.getId(block).path)
            blockJson.addProperty("translation_key", block.translationKey)
            blockJson.addProperty("slipperiness", block.slipperiness)
            blockJson.addProperty("velocity_multiplier", block.velocityMultiplier)
            blockJson.addProperty("jump_velocity_multiplier", block.jumpVelocityMultiplier)
            blockJson.addProperty("hardness", block.hardness)
            blockJson.addProperty("blast_resistance", block.blastResistance)
            blockJson.addProperty("item_id", Registries.ITEM.getRawId(block.asItem()))

            // Add flammable data if this block is flammable
            flammableData[block]?.let { (spreadChance, burnChance) ->
                val flammableJson = JsonObject()
                flammableJson.addProperty("spread_chance", spreadChance)
                flammableJson.addProperty("burn_chance", burnChance)
                blockJson.add("flammable", flammableJson)
            }

            if (block is ExperienceDroppingBlock) {
                blockJson.add(
                    "experience", ExperienceDroppingBlock.CODEC.codec().encodeStart(
                        RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                        block,
                    ).getOrThrow()
                )
            }
            if (block.lootTableKey.isPresent) {
                val table = server.reloadableRegistries
                    .getLootTable(block.lootTableKey.get() as RegistryKey<LootTable?>)
                blockJson.add(
                    "loot_table", LootTable::CODEC.get().encodeStart(
                        RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                        table
                    ).getOrThrow()
                )
            }
            val propsJson = JsonArray()
            for (prop in block.stateManager.properties) {
                // Use the hashcode to map to a property later; the property names are not unique
                propsJson.add(prop.hashCode())
            }
            blockJson.add("properties", propsJson)

            val statesJson = JsonArray()
            for (state in block.stateManager.states) {
                val stateJson = JsonObject()
                var stateFlags = 0
                var sideFlags = 0
                
                if (state.isAir) stateFlags = stateFlags or AIR
                if (state.isBurnable) stateFlags = stateFlags or BURNABLE
                if (state.isToolRequired) stateFlags = stateFlags or TOOL_REQUIRED
                if (state.hasSidedTransparency()) stateFlags = stateFlags or SIDED_TRANSPARENCY
                if (state.isReplaceable) stateFlags = stateFlags or REPLACEABLE
                if (state.isLiquid) stateFlags = stateFlags or IS_LIQUID
                if (state.isSolid) stateFlags = stateFlags or IS_SOLID
                if (state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) stateFlags = stateFlags or IS_FULL_CUBE

                if (state.isSideSolidFullSquare(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, Direction.DOWN)) sideFlags = sideFlags or DOWN_SIDE_SOLID
                if (state.isSideSolidFullSquare(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, Direction.UP)) sideFlags = sideFlags or UP_SIDE_SOLID
                if (state.isSideSolidFullSquare(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, Direction.NORTH)) sideFlags = sideFlags or NORTH_SIDE_SOLID
                if (state.isSideSolidFullSquare(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, Direction.SOUTH)) sideFlags = sideFlags or SOUTH_SIDE_SOLID
                if (state.isSideSolidFullSquare(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, Direction.WEST)) sideFlags = sideFlags or WEST_SIDE_SOLID
                if (state.isSideSolidFullSquare(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, Direction.EAST)) sideFlags = sideFlags or EAST_SIDE_SOLID
                if (state.isSideSolid(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, Direction.DOWN, SideShapeType.CENTER)) sideFlags = sideFlags or DOWN_CENTER_SOLID
                if (state.isSideSolid(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, Direction.UP, SideShapeType.CENTER)) sideFlags = sideFlags or UP_CENTER_SOLID
                
                stateJson.addProperty("id", Block.getRawIdFromState(state))
                stateJson.addProperty("state_flags", stateFlags and 0xFF)
                stateJson.addProperty("side_flags", sideFlags and 0xFF)
                stateJson.addProperty("instrument", state.instrument.name)
                stateJson.addProperty("luminance", state.luminance)
                stateJson.addProperty("piston_behavior", state.pistonBehavior.name)
                stateJson.addProperty("hardness", state.getHardness(null, null))
                if (state.isOpaque) {
                    stateJson.addProperty("opacity", state.opacity)
                }
 
                if (block.defaultState == state) {
                    blockJson.addProperty("default_state_id", Block.getRawIdFromState(state))
                }

                val collisionShapeIdxsJson = JsonArray()
                for (box in state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).boundingBoxes) {
                    val idx = shapes.putIfAbsent(box, shapes.size)
                    collisionShapeIdxsJson.add(Objects.requireNonNullElseGet(idx) { shapes.size - 1 })
                }

                stateJson.add("collision_shapes", collisionShapeIdxsJson)

                val outlineShapeIdxsJson = JsonArray()
                for (box in state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).boundingBoxes) {
                    val idx = shapes.putIfAbsent(box, shapes.size)
                    outlineShapeIdxsJson.add(Objects.requireNonNullElseGet(idx) { shapes.size - 1 })
                }

                stateJson.add("outline_shapes", outlineShapeIdxsJson)

                for (blockEntity in Registries.BLOCK_ENTITY_TYPE) {
                    if (blockEntity.supports(state)) {
                        stateJson.addProperty("block_entity_type", Registries.BLOCK_ENTITY_TYPE.getRawId(blockEntity))
                    }
                }

                statesJson.add(stateJson)
            }
            blockJson.add("states", statesJson)

            blocksJson.add(blockJson)
        }

        val blockEntitiesJson = JsonArray()
        for (blockEntity in Registries.BLOCK_ENTITY_TYPE) {
            blockEntitiesJson.add(Registries.BLOCK_ENTITY_TYPE.getId(blockEntity)!!.path)
        }

        val shapesJson = JsonArray()
        for (shape in shapes.keys) {
            val shapeJson = JsonObject()
            val min = JsonArray()
            min.add(shape.minX)
            min.add(shape.minY)
            min.add(shape.minZ)
            val max = JsonArray()
            max.add(shape.maxX)
            max.add(shape.maxY)
            max.add(shape.maxZ)
            shapeJson.add("min", min)
            shapeJson.add("max", max)
            shapesJson.add(shapeJson)
        }

        topLevelJson.add("block_entity_types", blockEntitiesJson)
        topLevelJson.add("shapes", shapesJson)
        topLevelJson.add("blocks", blocksJson)

        return topLevelJson
    }
}
