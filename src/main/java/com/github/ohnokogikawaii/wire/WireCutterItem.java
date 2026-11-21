package com.github.ohnokogikawaii.wire;

import com.github.ohnokogikawaii.pas.PASControlCableEntity;
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
import com.github.ohnokogikawaii.insulator.InsulatorBlockEntity;
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

    private record ControlCableHit(
            PASControlCableEntity cable,
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

        ControlCableHit controlCableHit =
                findControlCableHit(level, player);

        if (hit == null && controlCableHit == null) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

// 制御ケーブルと電力線の両方が候補になった場合、
// 視線に近い方を切断する。
        if (controlCableHit != null
                && (hit == null
                || controlCableHit.distanceAlongRay()
                <= hit.distanceAlongRay())) {

            PASControlCableEntity cable =
                    controlCableHit.cable();

            if (!cable.isAlive()) {
                return InteractionResultHolder.pass(stack);
            }

            cable.disconnect();

            player.displayClientMessage(
                    Component.literal("制御ケーブルを切断しました。")
                            .withStyle(ChatFormatting.GREEN),
                    true
            );

            return InteractionResultHolder.success(stack);
        }

        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        WireEntity wire = hit.wire();

        if (!wire.isAlive()) {
            return InteractionResultHolder.pass(stack);
        }

        // 親ワイヤーに取り付けられた分岐コネクタを削除する。
        removeConnectorsOnWire(level, wire);


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
     * 視線上にある制御ケーブルを検索する。
     */
    private ControlCableHit findControlCableHit(
            Level level,
            Player player
    ) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 rayEnd = eye.add(look.scale(REACH));

        AABB searchArea = new AABB(eye, rayEnd).inflate(64.0);

        ControlCableHit bestHit = null;

        for (PASControlCableEntity cable :
                level.getEntitiesOfClass(
                        PASControlCableEntity.class,
                        searchArea,
                        Entity::isAlive
                )) {

            Vec3 a = Vec3.atCenterOf(cable.getPASPos());
            Vec3 b = Vec3.atCenterOf(cable.getSOGPos());

            double length = a.distanceTo(b);

            if (length < 0.001) {
                continue;
            }

            double sag = Math.min(0.25, length * 0.025);

            for (int i = 0; i <= 32; i++) {
                double t = (double) i / 32.0;

                Vec3 point = a.lerp(b, t);

                point = point.add(
                        0,
                        -4.0 * sag * t * (1.0 - t),
                        0
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

                    bestHit = new ControlCableHit(
                            cable,
                            alongRay,
                            distance
                    );
                }
            }
        }

        return bestHit;
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