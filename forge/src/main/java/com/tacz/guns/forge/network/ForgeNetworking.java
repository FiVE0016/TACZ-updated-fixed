package com.tacz.guns.forge.network;

import com.tacz.guns.GunMod;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.platform.NetworkPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadFlow;
import net.minecraftforge.network.payload.PayloadProtocol;

/**
 * TACZ's play-phase payload channel on Forge, built from {@link NetworkHandler#payloads()}.
 */
public final class ForgeNetworking {
    private static final int PROTOCOL_VERSION = 1;
    private static Channel<CustomPacketPayload> channel;

    private ForgeNetworking() {
    }

    public static void init() {
        PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> protocol = ChannelBuilder
                .named(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "network"))
                .networkProtocolVersion(PROTOCOL_VERSION)
                .payloadChannel()
                .play();
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow = null;
        for (NetworkPlatform.PayloadSpec<?> spec : NetworkHandler.payloads()) {
            PacketFlow direction = spec.direction() == NetworkPlatform.Direction.SERVERBOUND ? PacketFlow.SERVERBOUND : PacketFlow.CLIENTBOUND;
            flow = addPayload(flow == null ? protocol.flow(direction) : flow.flow(direction), spec);
        }
        if (flow == null) {
            throw new IllegalStateException("TACZ declares no network payloads");
        }
        channel = flow.build();
    }

    public static Channel<CustomPacketPayload> channel() {
        if (channel == null) {
            throw new IllegalStateException("TACZ's network channel is used before mod construction finished");
        }
        return channel;
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> addPayload(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow, NetworkPlatform.PayloadSpec<T> spec) {
        // Every payload codec accepts a RegistryFriendlyByteBuf, which is what the play protocol hands out.
        StreamCodec<RegistryFriendlyByteBuf, T> codec = (StreamCodec<RegistryFriendlyByteBuf, T>) spec.codec();
        boolean serverbound = spec.direction() == NetworkPlatform.Direction.SERVERBOUND;
        return flow.add(spec.type(), codec, (payload, context) -> {
            PacketListener listener = context.getConnection().getPacketListener();
            if (serverbound) {
                ServerPlayer sender = context.getSender();
                schedule(sender.level().getServer().packetProcessor(), listener, PacketFlow.SERVERBOUND, spec, payload, sender);
            } else {
                Client.schedule(listener, spec, payload);
            }
            context.setPacketHandled(true);
        });
    }

    /**
     * Runs a payload handler on the game thread in the order the payload arrived among vanilla packets.
     *
     * <p>Forge calls channel handlers on the network thread, and {@code context.enqueueWork} moves the work to the game's
     * task queue. Vanilla game packets wait in a separate {@link PacketProcessor} queue, which is drained inside the
     * tick, while the task queue runs between ticks. A payload could therefore overtake the vanilla packets sent just
     * before it: a gun draw sent right after a hotbar change saw the old selected slot, so the server shot with the
     * wrong item and spawned nothing. Fabric API and NeoForge put payloads in the packet processor queue too.</p>
     */
    private static <T extends CustomPacketPayload, L extends PacketListener> void schedule(
            PacketProcessor processor, L listener, PacketFlow flow, NetworkPlatform.PayloadSpec<T> spec, T payload, ServerPlayer sender) {
        Runnable task = () -> handle(spec, payload, sender);
        if (processor.isSameThread()) {
            // Payloads inside a bundle packet are already handled on the game thread, in bundle order.
            task.run();
        } else {
            processor.scheduleIfPossible(listener, new QueuedPayload<L>(new PacketType<>(flow, spec.type().id()), task));
        }
    }

    private static <T extends CustomPacketPayload> void handle(NetworkPlatform.PayloadSpec<T> spec, T payload, ServerPlayer sender) {
        try {
            spec.handler().handle(payload, sender);
        } catch (RuntimeException e) {
            // An escaping error would reach vanilla's packet error handling, which disconnects a client.
            GunMod.LOGGER.error("Failed to handle TACZ payload {}", spec.type().id(), e);
        }
    }

    /**
     * A decoded payload waiting in a {@link PacketProcessor} queue. It is never encoded; the type only names it in
     * logs and crash reports.
     */
    private record QueuedPayload<L extends PacketListener>(PacketType<QueuedPayload<L>> type, Runnable task) implements Packet<L> {
        @Override
        public void handle(L listener) {
            task.run();
        }
    }

    private static final class Client {
        private static <T extends CustomPacketPayload> void schedule(PacketListener listener, NetworkPlatform.PayloadSpec<T> spec, T payload) {
            ForgeNetworking.schedule(Minecraft.getInstance().packetProcessor(), listener, PacketFlow.CLIENTBOUND, spec, payload, null);
        }
    }
}
