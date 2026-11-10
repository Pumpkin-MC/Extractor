package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.*
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext
import org.slf4j.LoggerFactory

/**
 * Extracts structure starts and references from generated chunks.
 *
 * Uses placement math (pure RNG, no terrain gen) to find which chunks should
 * have structures, then generates only those chunks to STRUCTURE_REFERENCES
 * status (no noise/terrain placement, just the start + reference scan).
 *
 * Per piece, dumps typed placement data. For jigsaw structures, serializes
 * template pool element, rotation, junctions, position, and ground level delta.
 *
 * Per chunk, dumps the `allReferences` map to validate cross-chunk reference indexing.
 */
class StructureStarts(
    private val halfRange: Int = 100,
    private val maxGenerate: Int = 2000
) : Extractor.Extractor {
    private val logger = LoggerFactory.getLogger("extractor-structure-starts")

    override fun fileName(): String = "structure_starts.json"

    override fun isTest(): Boolean = true

    override fun extract(server: MinecraftServer): JsonElement {
        val json = JsonObject()
        val seed = server.overworld().seed
        json.addProperty("seed", seed)
        json.addProperty("scan_range", halfRange)
        json.addProperty("max_generate", maxGenerate)

        val structureRegistry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
        val structureSetRegistry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)

        val dimensions = listOfNotNull(
            "overworld" to server.overworld(),
            server.getLevel(Level.NETHER)?.let { "the_nether" to it },
            server.getLevel(Level.END)?.let { "the_end" to it }
        ).toMap()

        for ((name, level) in dimensions) {
            json.add(name, extractDimension(level, name, structureRegistry, structureSetRegistry))
        }

        return json
    }

    private fun extractDimension(
        level: ServerLevel,
        name: String,
        structureRegistry: net.minecraft.core.Registry<Structure>,
        structureSetRegistry: net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.StructureSet>
    ): JsonObject {
        val dimJson = JsonObject()

        val generatorState = level.chunkSource.generatorState
        val pieceContext = StructurePieceSerializationContext.fromLevel(level)

        // Phase 1: Use placement math to find candidate chunks (no chunk gen needed)
        val candidateChunks = mutableSetOf<ChunkPos>()

        for (set in structureSetRegistry) {
            val placement = set.placement()

            for (x in -halfRange until halfRange) {
                for (z in -halfRange until halfRange) {
                    if (placement.isStructureChunk(generatorState, x, z)) {
                        candidateChunks.add(ChunkPos(x, z))
                    }
                }
            }
        }

        logger.info("$name: Found ${candidateChunks.size} candidate structure chunks in ${halfRange * 2}x${halfRange * 2} area")

        // Phase 2: Generate candidate chunks to STRUCTURE_REFERENCES (no terrain gen).
        val sorted = candidateChunks.sortedWith(compareBy({ it.x }, { it.z }))
        val toGenerate = if (sorted.size > maxGenerate) {
            logger.warn("$name: Capping from ${sorted.size} to $maxGenerate candidates")
            sorted.take(maxGenerate)
        } else {
            sorted
        }

        val chunksJson = JsonArray()
        var totalStarts = 0
        var totalPieces = 0
        var chunksWithStarts = 0
        var chunksWithReferences = 0
        var totalReferences = 0

        for ((idx, pos) in toGenerate.withIndex()) {
            if (idx > 0 && idx % 500 == 0) {
                logger.info("$name: Generated $idx/${toGenerate.size} chunks, found $totalStarts starts so far...")
            }

            val chunk = try {
                level.getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_REFERENCES, true) ?: continue
            } catch (e: Exception) {
                logger.warn("Failed to generate chunk ${pos.x},${pos.z}: ${e.message}")
                continue
            }

            val starts = chunk.allStarts
            val validStarts = starts.filter { (_, start) -> start.isValid }
            val references = chunk.allReferences.filter { (_, set) -> !set.isEmpty() }

            if (validStarts.isEmpty() && references.isEmpty()) continue

            val chunkJson = JsonObject()
            chunkJson.addProperty("x", pos.x)
            chunkJson.addProperty("z", pos.z)

            val startsArray = JsonArray()
            for ((structure, start) in validStarts) {
                val startJson = JsonObject()
                val structureKey = structureRegistry.getKey(structure)
                startJson.addProperty("structure", structureKey?.toString() ?: "unknown")
                startJson.addProperty("chunk_x", start.chunkPos.x)
                startJson.addProperty("chunk_z", start.chunkPos.z)
                startJson.addProperty("references", start.references)

                val bb = start.boundingBox
                startJson.add("bounding_box", serializeBoundingBox(bb))

                val piecesArray = JsonArray()
                for (piece in start.pieces) {
                    val pieceJson = JsonObject()

                    val pieceTypeKey = BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.type)
                    pieceJson.addProperty("type", pieceTypeKey?.toString() ?: "unknown")
                    pieceJson.addProperty("gen_depth", piece.genDepth)

                    val orientation = piece.orientation
                    pieceJson.addProperty("orientation", orientation?.get2DDataValue() ?: -1)

                    pieceJson.add("bounding_box", serializeBoundingBox(piece.boundingBox))

                    val tag = piece.createTag(pieceContext)
                    val pieceData = typedPieceData(pieceTypeKey?.toString(), tag)
                    if (pieceData != null) {
                        pieceJson.add("piece_data", pieceData)
                    }

                    piecesArray.add(pieceJson)
                    totalPieces++
                }
                startJson.add("pieces", piecesArray)

                startsArray.add(startJson)
                totalStarts++
            }
            chunkJson.add("starts", startsArray)

            if (validStarts.isNotEmpty()) chunksWithStarts++

            // Cross-chunk references: which structures (by id) in nearby chunks
            // have a BB that intersects this chunk, plus the source chunk positions.
            val referencesArray = JsonArray()
            val sortedRefs = references.entries.sortedBy {
                structureRegistry.getKey(it.key)?.toString() ?: "unknown"
            }
            for ((structure, sourceLongs) in sortedRefs) {
                val refJson = JsonObject()
                val structureKey = structureRegistry.getKey(structure)
                refJson.addProperty("structure", structureKey?.toString() ?: "unknown")

                val sourceChunksArr = JsonArray()
                val sortedSources = sourceLongs.toLongArray().also { it.sort() }
                for (packed in sortedSources) {
                    val pair = JsonArray()
                    pair.add(ChunkPos.getX(packed))
                    pair.add(ChunkPos.getZ(packed))
                    sourceChunksArr.add(pair)
                }
                refJson.add("source_chunks", sourceChunksArr)
                referencesArray.add(refJson)
                totalReferences += sourceLongs.size
            }
            if (references.isNotEmpty()) {
                chunkJson.add("references", referencesArray)
                chunksWithReferences++
            }

            chunksJson.add(chunkJson)
        }

        dimJson.addProperty("candidate_chunks", candidateChunks.size)
        dimJson.addProperty("generated_chunks", toGenerate.size)
        dimJson.addProperty("chunks_with_starts", chunksWithStarts)
        dimJson.addProperty("chunks_with_references", chunksWithReferences)
        dimJson.addProperty("total_starts", totalStarts)
        dimJson.addProperty("total_pieces", totalPieces)
        dimJson.addProperty("total_references", totalReferences)
        dimJson.add("chunks", chunksJson)

        logger.info(
            "$name: Extracted $totalStarts starts ($totalPieces pieces) " +
                "and $totalReferences references across $chunksWithReferences chunks"
        )
        return dimJson
    }

    private fun serializeBoundingBox(bb: net.minecraft.world.level.levelgen.structure.BoundingBox): JsonObject {
        val json = JsonObject()
        json.addProperty("min_x", bb.minX())
        json.addProperty("min_y", bb.minY())
        json.addProperty("min_z", bb.minZ())
        json.addProperty("max_x", bb.maxX())
        json.addProperty("max_y", bb.maxY())
        json.addProperty("max_z", bb.maxZ())
        return json
    }

    private fun typedPieceData(pieceType: String?, tag: CompoundTag): JsonObject? = when (pieceType) {
        "minecraft:jigsaw" -> serializeJigsawPieceData(tag)
        else -> null
    }

    private fun serializeJigsawPieceData(tag: CompoundTag): JsonObject {
        val json = JsonObject()
        val position = JsonArray()
        position.add(tag.getIntOr("PosX", 0))
        position.add(tag.getIntOr("PosY", 0))
        position.add(tag.getIntOr("PosZ", 0))
        json.add("position", position)
        json.addProperty("ground_level_delta", tag.getIntOr("ground_level_delta", 0))
        json.add("junctions", tag.get("junctions")?.let(::nbtToJson) ?: JsonArray())
        tag.get("pool_element")?.let { json.add("pool_element", nbtToJson(it)) }
        tag.get("rotation")?.let { json.add("rotation", nbtToJson(it)) }
        json.addProperty("liquid_settings", tag.getStringOr("liquid_settings", "apply_waterlogging"))
        return json
    }

    private fun nbtToJson(tag: Tag): JsonElement = when (tag) {
        is CompoundTag -> {
            val obj = JsonObject()
            for (key in tag.keySet().sorted()) {
                val child = tag.get(key) ?: continue
                obj.add(key, nbtToJson(child))
            }
            obj
        }
        is ListTag -> {
            val arr = JsonArray()
            for (i in 0 until tag.size) {
                arr.add(nbtToJson(tag.get(i)))
            }
            arr
        }
        is ByteTag -> JsonPrimitive(tag.value)
        is ShortTag -> JsonPrimitive(tag.value)
        is IntTag -> JsonPrimitive(tag.value)
        is LongTag -> JsonPrimitive(tag.value)
        is FloatTag -> JsonPrimitive(tag.value)
        is DoubleTag -> JsonPrimitive(tag.value)
        is StringTag -> JsonPrimitive(tag.value)
        is ByteArrayTag -> {
            val arr = JsonArray()
            for (b in tag.asByteArray) arr.add(b)
            arr
        }
        is IntArrayTag -> {
            val arr = JsonArray()
            for (v in tag.asIntArray) arr.add(v)
            arr
        }
        is LongArrayTag -> {
            val arr = JsonArray()
            for (v in tag.asLongArray) arr.add(v)
            arr
        }
        is EndTag -> JsonNull.INSTANCE
    }
}
