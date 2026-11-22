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
     * 制御点の順番変更要求。
     */
    public record ReorderControlPointPayload(
            int wireEntityId,
            int fromIndex,
            int toIndex
    ) implements CustomPacketPayload {

        public static final Type<ReorderControlPointPayload> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(
                                PowerNetwork.MODID,
                                "reorder_jumper_control_point"
                        )
                );

        public static final StreamCodec<
                RegistryFriendlyByteBuf,
                ReorderControlPointPayload
                > STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                ReorderControlPointPayload::wireEntityId,
                ByteBufCodecs.VAR_INT,
                ReorderControlPointPayload::fromIndex,
                ByteBufCodecs.VAR_INT,
                ReorderControlPointPayload::toIndex,
                ReorderControlPointPayload::new
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
        event.registrar("1").playToServer(
                ReorderControlPointPayload.TYPE,
                ReorderControlPointPayload.STREAM_CODEC,
                JumperControlPointNetwork::handleReorderControlPoint
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

    /**
     * 制御点の順番変更要求をサーバー側で処理する。
     */
    private static void handleReorderControlPoint(
            ReorderControlPointPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }

        if (!(level.getEntity(payload.wireEntityId())
                instanceof WireEntity wire)) {
            return;
        }

        if (!wire.isAlive() || !wire.isJumperWire()) {
            return;
        }

        // 順番変更の対象が存在するか確認する。
        int count = wire.getJumperControlPoints().size();

        if (payload.fromIndex() < 0
                || payload.fromIndex() >= count
                || payload.toIndex() < 0
                || payload.toIndex() >= count) {
            return;
        }

        // 電線の端点または制御点のいずれかに
        // プレイヤーが8ブロック以内にいることを確認する。
        boolean nearWire =
                player.position().distanceToSqr(
                        wire.getEndpointAWorldPosition()
                ) <= 64.0
                        || player.position().distanceToSqr(
                        wire.getEndpointBWorldPosition()
                ) <= 64.0;

        if (!nearWire) {
            for (Vec3 point : wire.getJumperControlPoints()) {
                if (player.position().distanceToSqr(point) <= 64.0) {
                    nearWire = true;
                    break;
                }
            }
        }

        if (!nearWire) {
            return;
        }

        wire.reorderJumperControlPoint(
                payload.fromIndex(),
                payload.toIndex()
        );
    }

    /**
     * クライアントから制御点の順番変更を要求する。
     */
    public static void requestReorderControlPoint(
            int wireEntityId,
            int fromIndex,
            int toIndex
    ) {
        PacketDistributor.sendToServer(
                new ReorderControlPointPayload(
                        wireEntityId,
                        fromIndex,
                        toIndex
                )
        );
    }
}