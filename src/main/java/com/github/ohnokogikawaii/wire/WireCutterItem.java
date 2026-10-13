package com.github.ohnokogikawaii.wire;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WireCutterItem extends Item {

    private static final double REACH = 3.0;
    private static final double MAX_HIT_DISTANCE = 0.30;
    private static final int TRACE_SAMPLES = 3;

    private record WireHit(
            WireEntity wire,
            double distanceAlongRay,
            double distanceToRay
    ) {
    }

    public WireCutterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        WireHit hit = findWireHit(level, player);

        if (hit == null) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        WireEntity wire = hit.wire();

        if (!wire.isAlive()) {
            return InteractionResultHolder.pass(stack);
        }

        // 切断前に UUID を保存する。
        UUID cutWireUuid = wire.getUUID();

        // 親ワイヤーに取り付けられた分岐コネクタを削除する。
        int removedConnectors = removeConnectorsOnWire(
                level,
                wire
        );

        // 対象ワイヤーを切断する。
        wire.discard();

        player.displayClientMessage(
                Component.translatable(
                        "message.powernetwork.wire.cut"
                ).withStyle(ChatFormatting.GREEN),
                true
        );

        return InteractionResultHolder.success(stack);
    }

    /**
     * 視線上にあるワイヤーを検索する。
     */
    private WireHit findWireHit(
            Level level,
            Player player
    ) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 rayEnd = eye.add(look.scale(REACH));

        /*
         * 長いワイヤーの Entity 自体が視線から離れていても
         * 候補として取得できるようにする。
         *
         * 実際に切断する距離は REACH で制限する。
         */
        AABB searchArea = new AABB(eye, rayEnd).inflate(64.0);

        WireHit bestHit = null;

        for (WireEntity wire : level.getEntitiesOfClass(
                WireEntity.class,
                searchArea,
                Entity::isAlive
        )) {
            Vec3 a = wire.getEndpointAWorldPosition();
            Vec3 b = wire.getEndpointBWorldPosition();

            if (a.distanceToSqr(b) < 1.0E-8) {
                continue;
            }

            WireType wireType = wire.getWireType();
            boolean canSag = wireType != null
                    && wireType.canSag();

            for (int i = 0; i <= TRACE_SAMPLES; i++) {
                double t = (double) i / TRACE_SAMPLES;

                Vec3 point = WireBranchGeometry.pointOnWire(
                        a,
                        b,
                        t,
                        canSag
                );

                Vec3 fromEye = point.subtract(eye);
                double alongRay = fromEye.dot(look);

                if (alongRay < 0.0 || alongRay > REACH) {
                    continue;
                }

                Vec3 closestOnRay = eye.add(
                        look.scale(alongRay)
                );

                double distance = point.distanceTo(closestOnRay);

                if (distance > MAX_HIT_DISTANCE) {
                    continue;
                }

                if (bestHit == null
                        || alongRay < bestHit.distanceAlongRay()
                        || (Math.abs(
                        alongRay - bestHit.distanceAlongRay()
                ) < 1.0E-6
                        && distance < bestHit.distanceToRay())) {

                    bestHit = new WireHit(
                            wire,
                            alongRay,
                            distance
                    );
                }
            }
        }

        return bestHit;
    }

    /**
     * 指定した親ワイヤーに設置されている分岐コネクタを削除する。
     *
     * コネクタに接続されている分岐ワイヤーも削除する。
     */
    private int removeConnectorsOnWire(
            Level level,
            WireEntity parentWire
    ) {
        UUID parentUuid = parentWire.getUUID();

        /*
         * 親ワイヤーの長さに関係なく、親 UUID が一致する
         * コネクタを探すため広い範囲を検索する。
         */
        AABB searchArea = parentWire.getBoundingBoxForCulling()
                .inflate(64.0);

        List<WireBranchConnectorEntity> connectors =
                new ArrayList<>(
                        level.getEntitiesOfClass(
                                WireBranchConnectorEntity.class,
                                searchArea,
                                branch -> branch.isAlive()
                                        && parentUuid.equals(
                                        branch.getParentWireUuid()
                                )
                        )
                );

        for (WireBranchConnectorEntity branch : connectors) {
            removeWiresConnectedToBranch(level, branch);
            branch.discard();
        }

        return connectors.size();
    }

    /**
     * 分岐コネクタの UUID を端点に持つワイヤーを削除する。
     *
     * コネクタだけを消すと、分岐先のワイヤーが宙に残るため、
     * 関連するワイヤーも一緒に削除する。
     */
    private void removeWiresConnectedToBranch(
            Level level,
            WireBranchConnectorEntity branch
    ) {
        UUID branchUuid = branch.getUUID();

        AABB searchArea = new AABB(
                branch.position(),
                branch.position()
        ).inflate(64.0);

        List<WireEntity> attachedWires = new ArrayList<>(
                level.getEntitiesOfClass(
                        WireEntity.class,
                        searchArea,
                        wire -> wire.isAlive()
                                && (branchUuid.equals(wire.getBranchAUuid())
                                || branchUuid.equals(wire.getBranchBUuid()))
                )
        );

        for (WireEntity attached : attachedWires) {
            attached.discard();
        }
    }
}