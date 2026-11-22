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
    private static final double MAX_HIT_DISTANCE = 0.45;

    private static final double AIM_REACH = 6.0;
    private static final double AIM_DISTANCE = 0.80;
    private static final double TRACE_INTERVAL = 0.125;

    private record WireHit(
            WireEntity wire,
            double distanceAlongRay,
            double distanceToRay
    ) {
    }

    public record WireAimTarget(
            WireEntity wire,
            double distanceAlongRay,
            double distanceToRay,
            boolean canCut
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
        WireAimTarget target = findAimTarget(level, player);

        if (target == null || !target.canCut()) {
            return null;
        }

        return new WireHit(
                target.wire(),
                target.distanceAlongRay(),
                target.distanceToRay()
        );
    }

    /**
     * ワイヤーカッターの照準対象を取得する。
     *
     * 6ブロック以内にある配線を探し、
     * 3ブロック以内かつ判定幅以内なら切断可能とする。
     */
    public static WireAimTarget findAimTarget(
            Level level,
            Player player
    ) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();

        Vec3 rayEnd = eye.add(look.scale(AIM_REACH));

        AABB searchArea = new AABB(eye, rayEnd).inflate(64.0);

        WireAimTarget bestTarget = null;

        for (WireEntity wire : level.getEntitiesOfClass(
                WireEntity.class,
                searchArea,
                Entity::isAlive
        )) {
            List<Vec3> path = getAimPath(wire);

            for (Vec3 point : path) {
                Vec3 fromEye = point.subtract(eye);

                double alongRay = fromEye.dot(look);

                if (alongRay < 0.0 || alongRay > AIM_REACH) {
                    continue;
                }

                Vec3 closestOnRay = eye.add(
                        look.scale(alongRay)
                );

                double distance = point.distanceTo(closestOnRay);

                if (distance > AIM_DISTANCE) {
                    continue;
                }

                boolean canCut =
                        alongRay <= REACH
                                && distance <= MAX_HIT_DISTANCE;

                if (bestTarget == null
                        || alongRay < bestTarget.distanceAlongRay()
                        || (Math.abs(
                        alongRay - bestTarget.distanceAlongRay()
                ) < 1.0E-6
                        && distance < bestTarget.distanceToRay())) {

                    bestTarget = new WireAimTarget(
                            wire,
                            alongRay,
                            distance,
                            canCut
                    );
                }
            }
        }

        return bestTarget;
    }

    /**
     * 描画位置と合わせた配線の経路を取得する。
     *
     * ジャンパ線は両端を0.5ブロック上げた直線。
     * 通常の電線は既存の垂れ計算を使用する。
     */
    public static List<Vec3> getAimPath(WireEntity wire) {
        Vec3 a = wire.getEndpointAWorldPosition();
        Vec3 b = wire.getEndpointBWorldPosition();

        // ジャンパ線は描画と同じ高さの直線。
        if (wire.isJumperWire()) {
            return List.of(
                    a.add(0.0, 0.5, 0.0),
                    b.add(0.0, 0.5, 0.0)
            );
        }

        WireType wireType = wire.getWireType();

        boolean canSag = wireType != null
                && wireType.canSag();

        double totalLength = a.distanceTo(b);

        if (totalLength < 1.0E-8) {
            return List.of(a, b);
        }

        record PathAnchor(double t, Vec3 position) {
        }

        Vec3 direction = b.subtract(a);
        double lengthSquared = direction.lengthSqr();

        List<PathAnchor> anchors = new ArrayList<>();

        anchors.add(new PathAnchor(0.0, a));

        // 中間支持点を始点から終点への順番に並べる。
        for (WireEntity.SupportPoint support : wire.getSupportPoints()) {
            Vec3 position = support.position();

            double t = position.subtract(a)
                    .dot(direction) / lengthSquared;

            if (t > 0.0 && t < 1.0) {
                anchors.add(new PathAnchor(t, position));
            }
        }

        anchors.add(new PathAnchor(1.0, b));

        anchors.sort(
                java.util.Comparator.comparingDouble(PathAnchor::t)
        );

        List<Vec3> result = new ArrayList<>();

        // 支持点間を個別にサンプリングする。
        for (int spanIndex = 0; spanIndex < anchors.size() - 1; spanIndex++) {
            Vec3 start = anchors.get(spanIndex).position();
            Vec3 end = anchors.get(spanIndex + 1).position();

            double spanLength = start.distanceTo(end);

            int samples = Math.max(
                    2,
                    Math.min(
                            1024,
                            (int) Math.ceil(spanLength / TRACE_INTERVAL)
                    )
            );

            for (int i = 0; i <= samples; i++) {
                if (spanIndex > 0 && i == 0) {
                    continue;
                }

                double t = (double) i / samples;

                result.add(
                        WireBranchGeometry.pointOnWire(
                                start,
                                end,
                                t,
                                canSag
                        )
                );
            }
        }

        return result;
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