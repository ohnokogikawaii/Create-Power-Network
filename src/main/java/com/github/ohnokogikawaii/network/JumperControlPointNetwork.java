package com.github.ohnokogikawaii.network;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.wire.WireEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(
        modid = PowerNetwork.MODID,
        bus = EventBusSubscriber.Bus.MOD
)
public final class JumperControlPointNetwork {

    private JumperControlPointNetwork() {
    }

    /**
     * クライアントからサーバーへ制御点の確定座標を送るパケット。
     */
    public record AddControlPointPayload(
            int wireEntityId,
            double x,
            double y,
            double z
    ) implements CustomPacketPayload {

        public static final Type<AddControlPointPayload> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(
                                PowerNetwork.MODID,
                                "add_jumper_control_point"
                        )
                );

        public static final StreamCodec<
                RegistryFriendlyByteBuf,
                AddControlPointPayload
                > STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                AddControlPointPayload::wireEntityId,
                ByteBufCodecs.DOUBLE,
                AddControlPointPayload::x,
                ByteBufCodecs.DOUBLE,
                AddControlPointPayload::y,
                ByteBufCodecs.DOUBLE,
                AddControlPointPayload::z,
                AddControlPointPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * パケット登録。
     */
    @SubscribeEvent
    public static void registerPayloads(
            RegisterPayloadHandlersEvent event
    ) {
        event.registrar("1").playToServer(
                AddControlPointPayload.TYPE,
                AddControlPointPayload.STREAM_CODEC,
                JumperControlPointNetwork::handleAddControlPoint
        );
    }

    /**
     * 制御点追加要求をサーバー側で処理する。
     */
    private static void handleAddControlPoint(
            AddControlPointPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        if (!Double.isFinite(payload.x())
                || !Double.isFinite(payload.y())
                || !Double.isFinite(payload.z())) {
            return;
        }

        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }

        if (!(level.getEntity(payload.wireEntityId())
                instanceof WireEntity wire)) {
            return;
        }

        // ジャンパ線以外には追加しない。
        if (!wire.isAlive() || !wire.isJumperWire()) {
            return;
        }

        Vec3 position = new Vec3(
                payload.x(),
                payload.y(),
                payload.z()
        );

        // プレイヤーから離れすぎた位置への追加を防止する。
        if (player.position().distanceToSqr(position) > 64.0) {
            return;
        }

        // ワールド外の座標を拒否する。
        BlockPos blockPos = BlockPos.containing(position);

        if (!level.isInWorldBounds(blockPos)
                || !level.getWorldBorder().isWithinBounds(blockPos)) {
            return;
        }

        // Geometry側でYを0.5加算するため、
        // 保存座標は描画位置から0.5引いた値にする。
        Vec3 storedPosition = position.add(0.0, -0.5, 0.0);

        if (!wire.addJumperControlPoint(storedPosition)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "制御点を追加できません。最大数に達している可能性があります。"
                    ),
                    true
            );
        }
    }

    /**
     * クライアントから制御点追加を要求する。
     */
    public static void requestAddControlPoint(
            int wireEntityId,
            Vec3 position
    ) {
        PacketDistributor.sendToServer(
                new AddControlPointPayload(
                        wireEntityId,
                        position.x,
                        position.y,
                        position.z
                )
        );
    }
}