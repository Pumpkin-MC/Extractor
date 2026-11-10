package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.SharedConstants
import net.minecraft.network.ClientboundPacketListener
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.ProtocolInfo
import net.minecraft.network.protocol.PacketType
import net.minecraft.network.protocol.SimpleUnboundProtocol
import net.minecraft.network.protocol.configuration.ConfigurationProtocols
import net.minecraft.network.protocol.game.GameProtocols
import net.minecraft.network.protocol.handshake.HandshakeProtocols
import net.minecraft.network.protocol.login.LoginProtocols
import net.minecraft.network.protocol.status.StatusProtocols
import net.minecraft.server.MinecraftServer


class Packets : Extractor.Extractor {
    override fun fileName(): String {
        return "packets.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val packetsJson = JsonObject()

        val serverBound = arrayOf(
            HandshakeProtocols.SERVERBOUND_TEMPLATE,
            StatusProtocols.SERVERBOUND_TEMPLATE,
            LoginProtocols.SERVERBOUND_TEMPLATE,
            ConfigurationProtocols.SERVERBOUND_TEMPLATE,
            GameProtocols.SERVERBOUND_TEMPLATE
        )

        val clientBound = arrayOf(
            StatusProtocols.CLIENTBOUND_TEMPLATE,
            LoginProtocols.CLIENTBOUND_TEMPLATE,
            ConfigurationProtocols.CLIENTBOUND_TEMPLATE,
            GameProtocols.CLIENTBOUND_TEMPLATE
        )

        val serverBoundJson = serializeServerBound(serverBound)
        val clientBoundJson = serializeClientBound(clientBound)
        packetsJson.addProperty("version", SharedConstants.getCurrentVersion().protocolVersion())
        packetsJson.add("serverbound", serverBoundJson)
        packetsJson.add("clientbound", clientBoundJson)
        return packetsJson
    }

    private fun serializeServerBound(
        packets: Array<ProtocolInfo.DetailsProvider>
    ): JsonObject {
        val handshakeArray = JsonObject()
        val statusArray = JsonObject()
        val loginArray = JsonObject()
        val configArray = JsonObject()
        val playArray = JsonObject()

        for (provider in packets) {
            val details = provider.details()
            details.listPackets { type: PacketType<*>, id: Int ->
                when (details.id().id()) {
                    "handshake", "handshaking" -> handshakeArray.addProperty(type.id().path, id)
                    "play" -> playArray.addProperty(type.id().path, id)
                    "status" -> statusArray.addProperty(type.id().path, id)
                    "login" -> loginArray.addProperty(type.id().path, id)
                    "configuration" -> configArray.addProperty(type.id().path, id)
                }
            }
        }

        val finalJson = JsonObject()
        finalJson.add("handshake", handshakeArray)
        finalJson.add("status", statusArray)
        finalJson.add("login", loginArray)
        finalJson.add("config", configArray)
        finalJson.add("play", playArray)
        return finalJson
    }

    private fun serializeClientBound(
        packets: Array<SimpleUnboundProtocol<out ClientboundPacketListener, out FriendlyByteBuf>>
    ): JsonObject {
        val statusArray = JsonObject()
        val loginArray = JsonObject()
        val configArray = JsonObject()
        val playArray = JsonObject()

        for (provider in packets) {
            val details = provider.details()
            details.listPackets { type: PacketType<*>, id: Int ->
                when (details.id().id()) {
                    "handshaking" -> error("Clientbound packet should have no handshake")
                    "play" -> playArray.addProperty(type.id().path, id)
                    "status" -> statusArray.addProperty(type.id().path, id)
                    "login" -> loginArray.addProperty(type.id().path, id)
                    "configuration" -> configArray.addProperty(type.id().path, id)
                }
            }
        }

        val finalJson = JsonObject()
        finalJson.add("status", statusArray)
        finalJson.add("login", loginArray)
        finalJson.add("config", configArray)
        finalJson.add("play", playArray)
        return finalJson
    }
}