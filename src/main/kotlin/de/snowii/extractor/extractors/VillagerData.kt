package de.snowii.extractor.extractors

import com.google.gson.*
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.npc.villager.VillagerType
import net.minecraft.world.item.trading.TradeSet
import net.minecraft.world.item.trading.VillagerTrade

class VillagerData : Extractor.Extractor {
    override fun fileName(): String {
        return "villager_data.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val root = JsonObject()
        val registryAccess = server.registryAccess()
        val ops = registryAccess.createSerializationContext(JsonOps.INSTANCE)

        // Extract Villager Professions
        val professionRegistry = registryAccess.lookupOrThrow(Registries.VILLAGER_PROFESSION)
        val professionsJson = JsonObject()
        professionRegistry.listElements().forEach { holder ->
            val profession = holder.value()
            val profJson = JsonObject()
            
            // Manual extraction for VillagerProfession as it has no codec
            profJson.add("name", ComponentSerialization.CODEC.encodeStart(ops, profession.name()).getOrThrow())
            
            val requestedItems = JsonArray()
            profession.requestedItems().forEach { item ->
                requestedItems.add(BuiltInRegistries.ITEM.getKey(item).toString())
            }
            profJson.add("requested_items", requestedItems)
            
            val secondaryPoi = JsonArray()
            profession.secondaryPoi().forEach { block ->
                secondaryPoi.add(BuiltInRegistries.BLOCK.getKey(block).toString())
            }
            profJson.add("secondary_poi", secondaryPoi)
            
            val workSound = profession.workSound()
            if (workSound != null) {
                profJson.addProperty("work_sound", BuiltInRegistries.SOUND_EVENT.getKey(workSound).toString())
            }
            
            val tradeSetsJson = JsonObject()
            for (entry in profession.tradeSetsByLevel().int2ObjectEntrySet()) {
                tradeSetsJson.addProperty(entry.intKey.toString(), entry.value.identifier().toString())
            }
            profJson.add("trade_sets", tradeSetsJson)

            professionsJson.add(holder.key().identifier().path, profJson)
        }
        root.add("professions", professionsJson)

        // Extract Villager Types
        val typeRegistry = registryAccess.lookupOrThrow(Registries.VILLAGER_TYPE)
        val typesJson = JsonObject()
        typeRegistry.listElements().forEach { holder ->
            typesJson.add(
                holder.key().identifier().path,
                VillagerType.CODEC.encodeStart(ops, holder).getOrThrow()
            )
        }
        root.add("types", typesJson)

        // Extract Trade Sets
        val tradeSetRegistry = registryAccess.lookupOrThrow(Registries.TRADE_SET)
        val tradeSetsJson = JsonObject()
        tradeSetRegistry.listElements().forEach { holder ->
            tradeSetsJson.add(
                holder.key().identifier().path,
                TradeSet.CODEC.encodeStart(ops, holder.value()).getOrThrow()
            )
        }
        root.add("trade_sets", tradeSetsJson)

        // Extract Villager Trades
        val villagerTradeRegistry = registryAccess.lookupOrThrow(Registries.VILLAGER_TRADE)
        val villagerTradesJson = JsonObject()
        villagerTradeRegistry.listElements().forEach { holder ->
            villagerTradesJson.add(
                holder.key().identifier().path,
                VillagerTrade.CODEC.encodeStart(ops, holder.value()).getOrThrow()
            )
        }
        root.add("villager_trades", villagerTradesJson)

        return root
    }
}
