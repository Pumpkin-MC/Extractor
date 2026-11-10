package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.status.ChunkStatus
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class DimChunkPos(val pos: ChunkPos, val dimension: String)

data class StageHashEntry(
    val overallHash: String,
    val sectionHashes: List<Long>
)

object ChunkStageHashStorage {
    private val stageData = ConcurrentHashMap<Pair<DimChunkPos, String>, StageHashEntry>()
    private val trackedChunks = ConcurrentHashMap.newKeySet<DimChunkPos>()
    private val readyChunks = ConcurrentHashMap.newKeySet<DimChunkPos>()

    @Volatile
    private var readyLatch: CountDownLatch? = null

    @Volatile
    var currentDimension: String = "overworld"

    fun startTracking(chunks: Set<DimChunkPos>) {
        trackedChunks.addAll(chunks)
        readyLatch = CountDownLatch(chunks.size)
    }

    fun isTracking(pos: ChunkPos, dimension: String): Boolean {
        return trackedChunks.contains(DimChunkPos(pos, dimension))
    }

    fun markReady(pos: ChunkPos, dimension: String): Boolean {
        val key = DimChunkPos(pos, dimension)
        if (trackedChunks.contains(key) && readyChunks.add(key)) {
            readyLatch?.countDown()
            return true
        }
        return false
    }

    fun waitForAllReady(timeoutSeconds: Long): Boolean {
        return readyLatch?.await(timeoutSeconds, TimeUnit.SECONDS) ?: true
    }

    fun storeStageData(pos: ChunkPos, dimension: String, stageName: String, overallHash: String, sectionHashes: List<Long>) {
        stageData[Pair(DimChunkPos(pos, dimension), stageName)] = StageHashEntry(overallHash, sectionHashes)
    }

    fun getAllStageData(): Map<Pair<DimChunkPos, String>, StageHashEntry> = stageData.toMap()

    fun clear() {
        stageData.clear()
        trackedChunks.clear()
        readyChunks.clear()
        readyLatch = null
    }

    fun recordChunk(chunk: net.minecraft.world.level.chunk.ChunkAccess, stageName: String) {
        val dimension = currentDimension
        if (!isTracking(chunk.pos, dimension)) {
            return
        }
        val sections = chunk.sections
        val overallHash = computeBlockHash(sections.toList())
        val sectionHashes = sections.map { computeSectionChecksum(it) }
        storeStageData(chunk.pos, dimension, stageName, overallHash, sectionHashes)
    }

    fun computeSectionChecksum(section: LevelChunkSection): Long {
        val states = section.states
        var hash = 0xcbf29ce484222325uL.toLong() // FNV offset basis
        val fnvPrime = 0x00000100000001B3L

        for (y in 0 until 16) {
            for (z in 0 until 16) {
                for (x in 0 until 16) {
                    val stateId = Block.getId(states.get(x, y, z)).toLong()
                    hash = (hash xor stateId) * fnvPrime
                }
            }
        }
        return hash
    }

    fun computeBlockHash(sections: Iterable<LevelChunkSection>): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (section in sections) {
            val states = section.states
            for (y in 0 until 16) {
                for (z in 0 until 16) {
                    for (x in 0 until 16) {
                        val stateId = Block.getId(states.get(x, y, z))
                        md.update((stateId shr 24).toByte())
                        md.update((stateId shr 16).toByte())
                        md.update((stateId shr 8).toByte())
                        md.update(stateId.toByte())
                    }
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

class ChunkStageParityDump(
    private val dimension: String = "overworld",
    private val chunksToTrack: List<ChunkPos> = if (dimension == "overworld") listOf(
        ChunkPos(8, 8),
        ChunkPos(-13, -7),
        ChunkPos(14, 5),
        ChunkPos(-9, 12),
        ChunkPos(99, 70)
    ) else listOf(
        ChunkPos(8, 8),
        ChunkPos(-13, -7),
        ChunkPos(14, 5),
        ChunkPos(-9, 12)
    )
) : Extractor.Extractor {
    override fun fileName(): String = if (dimension == "overworld") "stage_hashes.json" else "${dimension}_stage_hashes.json"
    override fun isTest(): Boolean = true

    override fun extract(server: MinecraftServer): JsonElement {
        val level: ServerLevel = when (dimension) {
            "nether" -> server.getLevel(Level.NETHER)!!
            "end" -> server.getLevel(Level.END)!!
            else -> server.overworld()
        }

        ChunkStageHashStorage.clear()
        ChunkStageHashStorage.currentDimension = dimension

        val tracked = chunksToTrack.map { DimChunkPos(it, dimension) }.toSet()
        ChunkStageHashStorage.startTracking(tracked)

        for (chunkPos in chunksToTrack) {
            level.chunkSource.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FEATURES, true)
            ChunkStageHashStorage.markReady(chunkPos, dimension)
        }

        ChunkStageHashStorage.waitForAllReady(5)

        for (chunkPos in chunksToTrack) {
            val chunk = level.chunkSource.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FEATURES, false)
            if (chunk != null) {
                ChunkStageHashStorage.recordChunk(chunk, "minecraft:features")
            }
        }

        val topLevelJson = JsonArray()
        val allData = ChunkStageHashStorage.getAllStageData()

        for (chunkPos in chunksToTrack) {
            val key = DimChunkPos(chunkPos, dimension)
            val chunkObj = JsonObject()
            chunkObj.addProperty("x", chunkPos.x)
            chunkObj.addProperty("z", chunkPos.z)
            chunkObj.addProperty("dimension", dimension)

            val stagesObj = JsonObject()
            for (stage in listOf("minecraft:noise", "minecraft:surface", "minecraft:carvers", "minecraft:features")) {
                val entry = allData[Pair(key, stage)]
                if (entry != null) {
                    val stageEntryObj = JsonObject()
                    stageEntryObj.addProperty("hash", entry.overallHash)
                    val secArr = JsonArray()
                    for (secHash in entry.sectionHashes) {
                        secArr.add(java.lang.Long.toUnsignedString(secHash))
                    }
                    stageEntryObj.add("sections", secArr)
                    stagesObj.add(stage, stageEntryObj)
                }
            }
            if (stagesObj.size() > 0) {
                chunkObj.add("stages", stagesObj)
                topLevelJson.add(chunkObj)
            }
        }

        return topLevelJson
    }
}
