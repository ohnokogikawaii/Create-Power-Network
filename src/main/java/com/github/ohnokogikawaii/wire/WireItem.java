
package com.github.ohnokogikawaii.wire;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.github.ohnokogikawaii.insulator.InsulatorBlock;
import com.github.ohnokogikawaii.insulator.InsulatorBlockEntity;

public class WireItem extends Item {

    private static final double REACH = 6.0;
    private static final double PORT_HIT_DISTANCE = 0.38;
    private static final double MAX_GROOVE_ANGLE_DIFFERENCE = 5.0;


    private record EndpointSelection(
            BlockPos anchor,
            Vec3 position,
            UUID branchUuid,
            ResourceLocation wireTypeId
    ) {}

    public record BranchHit(
            WireBranchConnectorEntity branch,
            Vec3 position,
            double distanceToRay,
            double distanceAlongRay
    ) {}

    private record SupportCandidate(
            WireEntity wire,
            double t,
            float yaw,
            Vec3 tangent
    ) {}

    private static final Map<UUID, EndpointSelection> FIRST_ENDPOINTS =
            new ConcurrentHashMap<>();

    private final ResourceLocation wireTypeId;

    public WireItem(
            Properties properties,
            ResourceLocation wireTypeId
    ) {
        super(properties);
        this.wireTypeId = wireTypeId;
    }

    public ResourceLocation getWireTypeId() {
        return wireTypeId;
    }

    public WireType getWireType() {
        return WireTypeManager.get(wireTypeId);
    }

    /**
     * ブロックの接続点を選択する。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (player == null) {
            return InteractionResult.PASS;
        }
        // シフト右クリックで接続選択をキャンセル
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }

            boolean cancelled = FIRST_ENDPOINTS.remove(player.getUUID()) != null;

            player.displayClientMessage(
                    Component.literal(
                            cancelled
                                    ? "電線の接続選択をキャンセルしました。"
                                    : "キャンセルできる接続選択はありません。"
                    ).withStyle(
                            cancelled ? ChatFormatting.YELLOW : ChatFormatting.GRAY
                    ),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos().immutable();

        // 碍子の場合は通常の端子接続ではなく、支持点の操作を行う。
        if (level.getBlockState(clickedPos).getBlock()
                instanceof InsulatorBlock insulator) {

            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }

            return handleInsulatorSupport(
                    level,
                    player,
                    clickedPos,
                    insulator
            );
        }

        // 以下は従来の端子接続処理。
        if (!(level.getBlockState(clickedPos).getBlock()
                instanceof WireConnectionPointProvider provider)) {
            return InteractionResult.PASS;
        }

        var state = level.getBlockState(clickedPos);

        WireConnectionPointProvider.ConnectionPoint point =
                provider.getNearestConnectionPoint(
                        clickedPos,
                        context.getClickLocation(),
                        state
                );

        if (point == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        return selectEndpoint(
                level,
                player,
                new EndpointSelection(
                        clickedPos,
                        point.worldPosition(clickedPos),
                        null,
                        wireTypeId
                )
        );
    }

    /**
     * 固定式碍子への電線支持登録・解除。
     */
    private InteractionResult handleInsulatorSupport(
            Level level,
            Player player,
            BlockPos insulatorPos,
            InsulatorBlock insulator
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        if (!(level.getBlockEntity(insulatorPos)
                instanceof InsulatorBlockEntity insulatorEntity)) {
            player.displayClientMessage(
                    Component.literal("碍子の管理データを取得できません。")
                            .withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.FAIL;
        }

        /*
         * 既に登録されている電線があれば解除する。
         * 電線本体や電気的接続は削除しない。
         */
        for (UUID wireId :
                insulatorEntity.getSupportedWireIds()) {

            Entity entity = serverLevel.getEntity(wireId);

            if (entity instanceof WireEntity wire
                    && wire.isAlive()
                    && wire.removeSupportPoint(insulatorPos)) {

                insulatorEntity.removeSupportedWire(wireId);

                player.displayClientMessage(
                        Component.literal("碍子の電線支持を解除しました。")
                                .withStyle(ChatFormatting.YELLOW),
                        true
                );

                return InteractionResult.SUCCESS;
            }
        }

        // 支持可能本数を確認する。
        if (!insulatorEntity.canSupportMoreWires()) {
            player.displayClientMessage(
                    Component.literal("この碍子は既に2本の電線を支持しています。")
                            .withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        Vec3 supportPosition = insulator.getSupportPosition(
                level.getBlockState(insulatorPos),
                insulatorPos
        );

        SupportCandidate candidate = findWireForSupport(
                level,
                supportPosition
        );

        if (candidate == null) {
            player.displayClientMessage(
                    Component.literal("碍子の支持位置に電線がありません。")
                            .withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        WireEntity targetWire = candidate.wire();

// 既に溝の方向が決まっている場合、2本目の電線の角度を検証する。
        if (insulatorEntity.hasGrooveDirection()) {
            double angleDifference = axisAngleDifference(
                    insulatorEntity.getGrooveYaw(),
                    candidate.yaw()
            );

            if (angleDifference > MAX_GROOVE_ANGLE_DIFFERENCE) {
                player.displayClientMessage(
                        Component.literal(
                                String.format(
                                        "電線の角度が溝と合いません。差: %.1f度（許容: %.1f度）",
                                        angleDifference,
                                        MAX_GROOVE_ANGLE_DIFFERENCE
                                )
                        ).withStyle(ChatFormatting.RED),
                        true
                );

                return InteractionResult.SUCCESS;
            }
        }

        if (targetWire == null) {
            player.displayClientMessage(
                    Component.literal("碍子の支持位置に電線がありません。")
                            .withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        UUID wireId = targetWire.getUUID();

        // 電線側に支持点を追加する。
        if (!targetWire.addSupportPoint(
                insulatorPos,
                supportPosition
        )) {
            player.displayClientMessage(
                    Component.literal("この電線には支持点を追加できません。")
                            .withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 碍子側にも電線UUIDを保存する。
        if (!insulatorEntity.addSupportedWire(wireId)) {
            targetWire.removeSupportPoint(insulatorPos);

            player.displayClientMessage(
                    Component.literal("碍子への電線登録に失敗しました。")
                            .withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 最初の電線なら、その方向を溝の基準角度にする。
        if (!insulatorEntity.hasGrooveDirection()) {
            insulatorEntity.setGrooveYaw(candidate.yaw());
        }

        player.displayClientMessage(
                Component.literal("碍子に電線を固定しました。")
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        return InteractionResult.SUCCESS;
    }

    private static double axisAngleDifference(
            double angleA,
            double angleB
    ) {
        double difference = Math.abs(angleA - angleB);

        difference %= 180.0;

        return Math.min(
                difference,
                180.0 - difference
        );
    }

    /**
     * 碍子の支持位置を通過している電線を検索する。
     *
     * 電線のA-B間の曲線に対して最も近い位置を調べる。
     * 端点付近は支持点として使用しない。
     */

    private SupportCandidate findWireForSupport(
            Level level,
            Vec3 supportPosition
    ) {
        // 支持位置と電線の最大許容距離
        final double MAX_DISTANCE = 0.75;

        // 電線の端点付近は支持点にしない
        final double MIN_T = 0.02;
        final double MAX_T = 0.98;

        final double SEARCH_RADIUS = 64.0;
        final int SAMPLES = 256;

        AABB searchArea = new AABB(
                supportPosition,
                supportPosition
        ).inflate(SEARCH_RADIUS);

        SupportCandidate bestCandidate = null;
        double bestDistance = MAX_DISTANCE;

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

            WireType type = wire.getWireType();
            boolean canSag = type != null && type.canSag();

            /*
             * 電線の曲線を直接調べる。
             * A-Bの直線投影ではなく、実際の曲線上で
             * 支持位置に最も近い場所を検索する。
             */
            double bestT = -1.0;
            double localBestDistance = Double.MAX_VALUE;

            for (int i = 1; i < SAMPLES; i++) {
                double t = (double) i / SAMPLES;

                Vec3 point = WireBranchGeometry.pointOnWire(
                        a,
                        b,
                        t,
                        canSag
                );

                double distance = supportPosition.distanceTo(point);

                if (distance < localBestDistance) {
                    localBestDistance = distance;
                    bestT = t;
                }
            }

            if (bestT < 0.0) {
                continue;
            }

            /*
             * 最も近かった位置の周辺をさらに細かく検索する。
             */
            double range = 1.0 / SAMPLES;
            double low = Math.max(MIN_T, bestT - range);
            double high = Math.min(MAX_T, bestT + range);

            for (int i = 0; i < 24; i++) {
                double t1 = low + (high - low) / 3.0;
                double t2 = high - (high - low) / 3.0;

                Vec3 p1 = WireBranchGeometry.pointOnWire(
                        a, b, t1, canSag
                );

                Vec3 p2 = WireBranchGeometry.pointOnWire(
                        a, b, t2, canSag
                );

                double d1 = supportPosition.distanceToSqr(p1);
                double d2 = supportPosition.distanceToSqr(p2);

                if (d1 < d2) {
                    high = t2;
                } else {
                    low = t1;
                }
            }

            double t = (low + high) * 0.5;

            if (t < MIN_T || t > MAX_T) {
                continue;
            }

            Vec3 nearestPoint = WireBranchGeometry.pointOnWire(
                    a,
                    b,
                    t,
                    canSag
            );

            double distance = supportPosition.distanceTo(nearestPoint);

            if (distance > bestDistance) {
                continue;
            }

            /*
             * 支持位置における電線の接線方向を取得する。
             */
            Vec3 tangent = WireBranchGeometry.tangent(
                    a,
                    b,
                    t,
                    canSag
            );

            Vec3 horizontalTangent = new Vec3(
                    tangent.x,
                    0.0,
                    tangent.z
            );

            if (horizontalTangent.lengthSqr() < 1.0E-8) {
                continue;
            }

            horizontalTangent = horizontalTangent.normalize();

            float yaw = (float) Math.toDegrees(
                    Math.atan2(
                            horizontalTangent.z,
                            horizontalTangent.x
                    )
            );

            bestDistance = distance;

            bestCandidate = new SupportCandidate(
                    wire,
                    t,
                    yaw,
                    horizontalTangent
            );
        }

        return bestCandidate;
    }


    /**
     * ブロックを直接クリックしなかった場合は、
     * 視線上にある分岐コネクタの C を探す。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        // シフト右クリックで接続選択をキャンセル
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            boolean cancelled = FIRST_ENDPOINTS.remove(player.getUUID()) != null;

            player.displayClientMessage(
                    Component.literal(
                            cancelled
                                    ? "電線の接続選択をキャンセルしました。"
                                    : "キャンセルできる接続選択はありません。"
                    ).withStyle(
                            cancelled ? ChatFormatting.YELLOW : ChatFormatting.GRAY
                    ),
                    true
            );

            return InteractionResultHolder.success(stack);
        }

        BranchHit hit = findBranchPortHit(level, player);

        if (hit == null) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        InteractionResult result = selectEndpoint(
                level,
                player,
                new EndpointSelection(
                        BlockPos.containing(hit.position()),
                        hit.position(),
                        hit.branch().getUUID(),
                        wireTypeId
                )
        );

        return new InteractionResultHolder<>(result, stack);
    }

    /**
     * 視線と分岐ポート C の距離を計算し、
     * 最も狙いやすいポートを取得する。
     */
    public static BranchHit findBranchPortHit(
            Level level,
            Player player
    ) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 rayEnd = eye.add(look.scale(REACH));

        // ポートはコネクタ本体の中心から少し離れているため、
        // エンティティの検索範囲を広めにする。
        AABB searchArea = new AABB(eye, rayEnd).inflate(1.0);

        BranchHit bestHit = null;

        for (WireBranchConnectorEntity branch :
                level.getEntitiesOfClass(
                        WireBranchConnectorEntity.class,
                        searchArea,
                        Entity::isAlive
                )) {

            Vec3 port = branch.getBranchPortWorldPosition();
            Vec3 fromEye = port.subtract(eye);

            double alongRay = fromEye.dot(look);

            if (alongRay < 0.0 || alongRay > REACH) {
                continue;
            }

            Vec3 closestOnRay = eye.add(look.scale(alongRay));
            double distance = port.distanceTo(closestOnRay);

            if (distance > PORT_HIT_DISTANCE) {
                continue;
            }

            if (bestHit == null
                    || distance < bestHit.distanceToRay()
                    || (Math.abs(distance - bestHit.distanceToRay()) < 1.0E-6
                    && alongRay < bestHit.distanceAlongRay())) {

                bestHit = new BranchHit(
                        branch,
                        port,
                        distance,
                        alongRay
                );
            }
        }

        return bestHit;
    }

    private static InteractionResult selectEndpoint(
            Level level,
            Player player,
            EndpointSelection second
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        UUID playerId = player.getUUID();
        EndpointSelection first = FIRST_ENDPOINTS.get(playerId);

        if (first == null) {
            FIRST_ENDPOINTS.put(playerId, second);

            player.displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.first_endpoint"
                    ).withStyle(ChatFormatting.YELLOW),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        if (first.position().distanceToSqr(second.position()) < 1.0E-8) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.same_endpoint"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        FIRST_ENDPOINTS.remove(playerId);

        double distance = first.position().distanceTo(second.position());
        WireType wireType = WireTypeManager.get(first.wireTypeId());

        if (wireType == null) {
            player.displayClientMessage(
                    Component.literal("Wire type not found.")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        if (distance < wireType.getMinimumLength()) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.too_close"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        if (distance > wireType.getMaximumLength()) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.too_long"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        AABB searchArea = new AABB(
                first.position(),
                second.position()
        ).inflate(1.0);

        for (WireEntity existing : level.getEntitiesOfClass(
                WireEntity.class,
                searchArea,
                Entity::isAlive
        )) {
            Vec3 existingA = existing.getEndpointAWorldPosition();
            Vec3 existingB = existing.getEndpointBWorldPosition();

            boolean sameDirection =
                    existingA.distanceToSqr(first.position()) < 1.0E-8
                            && existingB.distanceToSqr(second.position()) < 1.0E-8;

            boolean reverseDirection =
                    existingA.distanceToSqr(second.position()) < 1.0E-8
                            && existingB.distanceToSqr(first.position()) < 1.0E-8;

            if (sameDirection || reverseDirection) {
                player.displayClientMessage(
                        Component.literal(
                                "These terminals are already connected."
                        ).withStyle(ChatFormatting.RED),
                        true
                );

                return InteractionResult.SUCCESS;
            }
        }

        WireEntity wire = new WireEntity(
                level,
                first.position(),
                second.position(),
                first.anchor(),
                second.anchor(),
                first.branchUuid() != null,
                second.branchUuid() != null,
                first.wireTypeId()
        );

        if (!level.addFreshEntity(wire)) {
            player.displayClientMessage(
                    Component.literal("Failed to create the wire.")
                            .withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 分岐コネクタ C と電線の端点を関連付ける。
        if (level instanceof ServerLevel serverLevel) {
            if (first.branchUuid() != null) {
                Entity entity = serverLevel.getEntity(first.branchUuid());

                if (entity instanceof WireBranchConnectorEntity branch) {
                    wire.attachEndpointAToBranch(branch);
                }
            }

            if (second.branchUuid() != null) {
                Entity entity = serverLevel.getEntity(second.branchUuid());

                if (entity instanceof WireBranchConnectorEntity branch) {
                    wire.attachEndpointBToBranch(branch);
                }
            }
        }

        player.displayClientMessage(
                Component.translatable(
                        "message.powernetwork.wire.connected"
                ).withStyle(ChatFormatting.GREEN),
                true
        );

        PowerNetwork.LOGGER.debug(
                "Created wire between {} at {} and {} at {}",
                first.anchor(),
                first.position(),
                second.anchor(),
                second.position()
        );

        return InteractionResult.SUCCESS;
    }

    public static void clearSelection(UUID playerId) {
        FIRST_ENDPOINTS.remove(playerId);
    }


    public static InteractionResult selectBranchEndpoint(
            Player player,
            InteractionHand hand,
            WireBranchConnectorEntity branch
    ) {
        if (!(player.getItemInHand(hand).getItem() instanceof WireItem item)) {
            return InteractionResult.PASS;
        }

        Level level = player.level();

// シフト右クリックで接続選択をキャンセル
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }

            boolean cancelled = FIRST_ENDPOINTS.remove(player.getUUID()) != null;

            player.displayClientMessage(
                    Component.literal(
                            cancelled
                                    ? "電線の接続選択をキャンセルしました。"
                                    : "キャンセルできる接続選択はありません。"
                    ).withStyle(
                            cancelled ? ChatFormatting.YELLOW : ChatFormatting.GRAY
                    ),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Vec3 portPosition = branch.getBranchPortWorldPosition();

        return selectEndpoint(
                level,
                player,
                new EndpointSelection(
                        BlockPos.containing(portPosition),
                        portPosition,
                        branch.getUUID(),
                        item.getWireTypeId()
                )
        );
    }

}
