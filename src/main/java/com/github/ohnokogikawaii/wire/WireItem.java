
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
import java.util.ArrayList;
import java.util.List;
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

    private record InsulatorSelection(
            BlockPos pos,
            Vec3 position
    ) {}

    private static final class RouteState {

        private final EndpointSelection start;

        private final List<InsulatorSelection> supports =
                new ArrayList<>();

        private BlockPos lastInsulatorPos;
        private Vec3 lastInsulatorPosition;

        private RouteState(EndpointSelection start) {
            this.start = start;
        }
    }

    private static final Map<UUID, RouteState> ROUTES =
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

        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }

            boolean cancelled =
                    ROUTES.remove(player.getUUID()) != null;

            player.displayClientMessage(
                    Component.literal(
                            cancelled
                                    ? "電線の敷設をキャンセルしました。"
                                    : "キャンセルできる敷設はありません。"
                    ).withStyle(
                            cancelled
                                    ? ChatFormatting.YELLOW
                                    : ChatFormatting.GRAY
                    ),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos =
                context.getClickedPos().immutable();

        var state = level.getBlockState(clickedPos);

        // 碍子は敷設ルートの始点・中間点・終点として扱う。
        if (state.getBlock() instanceof InsulatorBlock insulator) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }

            Vec3 position = insulator.getSupportPosition(
                    state,
                    clickedPos
            );

            return selectInsulator(
                    level,
                    player,
                    clickedPos,
                    position
            );
        }

        // 通常の接続端子
        if (!(state.getBlock()
                instanceof WireConnectionPointProvider provider)) {
            return InteractionResult.PASS;
        }

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

    private static InteractionResult selectInsulator(
            Level level,
            Player player,
            BlockPos pos,
            Vec3 position
    ) {
        UUID playerId = player.getUUID();

        RouteState route = ROUTES.get(playerId);

        EndpointSelection selection =
                new EndpointSelection(
                        pos,
                        position,
                        null,
                        ((WireItem) player.getMainHandItem().getItem())
                                .getWireTypeId()
                );

        // まだ敷設を開始していない場合
        if (route == null) {
            ROUTES.put(
                    playerId,
                    new RouteState(selection)
            );

            player.displayClientMessage(
                    Component.literal(
                            "碍子を始点に設定しました。次の接続先を選択してください。"
                    ).withStyle(ChatFormatting.YELLOW),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 最後に選択した碍子をもう一度クリックした場合は終端
        if (pos.equals(route.lastInsulatorPos)) {
            return finishAtLastInsulator(
                    level,
                    player,
                    route
            );
        }

        // 始点と同じ碍子を選択した場合
        if (pos.equals(route.start.anchor())) {
            player.displayClientMessage(
                    Component.literal(
                            "始点と同じ碍子は終点にできません。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 既に経由した碍子を再選択した場合
        for (InsulatorSelection support : route.supports) {
            if (support.pos().equals(pos)) {
                player.displayClientMessage(
                        Component.literal(
                                "この碍子は既に経由点として登録されています。"
                        ).withStyle(ChatFormatting.RED),
                        true
                );

                return InteractionResult.SUCCESS;
            }
        }

        if (!(level.getBlockEntity(pos)
                instanceof InsulatorBlockEntity insulatorEntity)) {
            player.displayClientMessage(
                    Component.literal(
                            "碍子の管理データを取得できません。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        if (!insulatorEntity.canSupportMoreWires()) {
            player.displayClientMessage(
                    Component.literal(
                            "この碍子は既に2本の電線を支持しています。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        route.supports.add(
                new InsulatorSelection(
                        pos.immutable(),
                        position
                )
        );

        route.lastInsulatorPos = pos.immutable();
        route.lastInsulatorPosition = position;

        player.displayClientMessage(
                Component.literal(
                        "中間支持点を追加しました。もう一度クリックすると、ここで終端します。"
                ).withStyle(ChatFormatting.GREEN),
                true
        );

        return InteractionResult.SUCCESS;
    }

    private static InteractionResult finishAtLastInsulator(
            Level level,
            Player player,
            RouteState route
    ) {
        if (route.lastInsulatorPos == null
                || route.lastInsulatorPosition == null) {
            return InteractionResult.PASS;
        }

        route.supports.removeIf(
                support -> support.pos().equals(
                        route.lastInsulatorPos
                )
        );

        EndpointSelection end =
                new EndpointSelection(
                        route.lastInsulatorPos,
                        route.lastInsulatorPosition,
                        null,
                        route.start.wireTypeId()
                );

        return finishRoute(
                level,
                player,
                route,
                end,
                true
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

        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return InteractionResultHolder.sidedSuccess(
                        stack,
                        true
                );
            }

            boolean cancelled =
                    ROUTES.remove(player.getUUID()) != null;

            player.displayClientMessage(
                    Component.literal(
                            cancelled
                                    ? "電線の敷設をキャンセルしました。"
                                    : "キャンセルできる敷設はありません。"
                    ).withStyle(
                            cancelled
                                    ? ChatFormatting.YELLOW
                                    : ChatFormatting.GRAY
                    ),
                    true
            );

            return InteractionResultHolder.success(stack);
        }

        BranchHit hit = findBranchPortHit(level, player);

        if (hit != null) {
            if (level.isClientSide) {
                return InteractionResultHolder.sidedSuccess(
                        stack,
                        true
                );
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

        // 空中右クリック：最後に選択した碍子で終端
        RouteState route = ROUTES.get(player.getUUID());

        if (route != null
                && route.lastInsulatorPos != null) {

            if (level.isClientSide) {
                return InteractionResultHolder.sidedSuccess(
                        stack,
                        true
                );
            }

            InteractionResult result = finishAtLastInsulator(
                    level,
                    player,
                    route
            );

            return new InteractionResultHolder<>(result, stack);
        }

        return InteractionResultHolder.pass(stack);
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
        UUID playerId = player.getUUID();

        RouteState route = ROUTES.get(playerId);

        // 最初の端子を始点に設定
        if (route == null) {
            ROUTES.put(
                    playerId,
                    new RouteState(second)
            );

            player.displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.first_endpoint"
                    ).withStyle(ChatFormatting.YELLOW),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        return finishRoute(
                level,
                player,
                route,
                second,
                false
        );
    }

    private static InteractionResult finishRoute(
            Level level,
            Player player,
            RouteState route,
            EndpointSelection end,
            boolean endIsInsulator
    ) {
        EndpointSelection start = route.start;

        if (start.position().distanceToSqr(end.position())
                < 1.0E-8) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.same_endpoint"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        WireType wireType =
                WireTypeManager.get(start.wireTypeId());

        if (wireType == null) {
            player.displayClientMessage(
                    Component.literal("Wire type not found.")
                            .withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        double distance =
                start.position().distanceTo(end.position());

        if (distance < wireType.getMinimumLength()
                || distance > wireType.getMaximumLength()) {
            player.displayClientMessage(
                    Component.literal(
                            "電線の長さが設定範囲外です。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 始点・終点の碍子の容量を事前確認
        for (BlockPos endpoint : List.of(
                start.anchor(),
                end.anchor()
        )) {
            if (level.getBlockState(endpoint).getBlock()
                    instanceof InsulatorBlock) {

                if (!(level.getBlockEntity(endpoint)
                        instanceof InsulatorBlockEntity be)
                        || !be.canSupportMoreWires()) {

                    player.displayClientMessage(
                            Component.literal(
                                    "始点または終点の碍子は、これ以上電線を支持できません。"
                            ).withStyle(ChatFormatting.RED),
                            true
                    );

                    return InteractionResult.SUCCESS;
                }
            }
        }

// 中間支持点の存在と容量を事前確認
        for (InsulatorSelection support : route.supports) {
            if (!(level.getBlockEntity(support.pos())
                    instanceof InsulatorBlockEntity be)
                    || !be.canSupportMoreWires()) {

                player.displayClientMessage(
                        Component.literal(
                                "中間碍子の支持登録に失敗する可能性があります。"
                        ).withStyle(ChatFormatting.RED),
                        true
                );

                return InteractionResult.SUCCESS;
            }
        }

        AABB searchArea = new AABB(
                start.position(),
                end.position()
        ).inflate(1.0);

        for (WireEntity existing : level.getEntitiesOfClass(
                WireEntity.class,
                searchArea,
                Entity::isAlive
        )) {
            Vec3 a = existing.getEndpointAWorldPosition();
            Vec3 b = existing.getEndpointBWorldPosition();

            boolean same =
                    a.distanceToSqr(start.position()) < 1.0E-8
                            && b.distanceToSqr(end.position()) < 1.0E-8;

            boolean reverse =
                    a.distanceToSqr(end.position()) < 1.0E-8
                            && b.distanceToSqr(start.position()) < 1.0E-8;

            if (same || reverse) {
                player.displayClientMessage(
                        Component.literal(
                                "この端子間には既に電線があります。"
                        ).withStyle(ChatFormatting.RED),
                        true
                );

                return InteractionResult.SUCCESS;
            }
        }

        WireEntity wire = new WireEntity(
                level,
                start.position(),
                end.position(),
                start.anchor(),
                end.anchor(),
                start.branchUuid() != null,
                end.branchUuid() != null,
                start.wireTypeId()
        );

        // 碍子を端点として使用した場合、端末状態を設定
        if (level.getBlockState(start.anchor()).getBlock()
                instanceof InsulatorBlock) {
            wire.setTerminalA(true);
        }

        if (endIsInsulator) {
            wire.setTerminalB(true);
        }

        if (!level.addFreshEntity(wire)) {
            player.displayClientMessage(
                    Component.literal("電線の生成に失敗しました。")
                            .withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 始点・終点・中間碍子を支持登録する
        List<InsulatorBlockEntity> registered =
                new ArrayList<>();

        boolean supportFailed = false;

// 始点・終点の碍子を登録
        for (BlockPos endpoint : List.of(
                start.anchor(),
                end.anchor()
        )) {
            if (!(level.getBlockState(endpoint).getBlock()
                    instanceof InsulatorBlock)) {
                continue;
            }

            if (!(level.getBlockEntity(endpoint)
                    instanceof InsulatorBlockEntity be)) {
                supportFailed = true;
                break;
            }

            if (!be.addSupportedWire(wire.getUUID())) {
                supportFailed = true;
                break;
            }

            registered.add(be);
        }

// 中間碍子を支持点として登録
        if (!supportFailed) {
            for (InsulatorSelection support : route.supports) {
                if (!(level.getBlockEntity(support.pos())
                        instanceof InsulatorBlockEntity be)) {
                    supportFailed = true;
                    break;
                }

                if (!wire.addSupportPoint(
                        support.pos(),
                        support.position()
                )) {
                    supportFailed = true;
                    break;
                }

                if (!be.addSupportedWire(wire.getUUID())) {
                    wire.removeSupportPoint(support.pos());
                    supportFailed = true;
                    break;
                }

                registered.add(be);
            }
        }

        if (supportFailed) {
            for (InsulatorBlockEntity be : registered) {
                be.removeSupportedWire(wire.getUUID());
            }

            wire.discard();

            player.displayClientMessage(
                    Component.literal(
                            "碍子への支持登録に失敗したため、電線を生成しませんでした。"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 分岐コネクタとの関連付け
        if (level instanceof ServerLevel serverLevel) {
            if (start.branchUuid() != null) {
                Entity entity =
                        serverLevel.getEntity(start.branchUuid());

                if (entity instanceof WireBranchConnectorEntity branch) {
                    wire.attachEndpointAToBranch(branch);
                }
            }

            if (end.branchUuid() != null) {
                Entity entity =
                        serverLevel.getEntity(end.branchUuid());

                if (entity instanceof WireBranchConnectorEntity branch) {
                    wire.attachEndpointBToBranch(branch);
                }
            }
        }

        ROUTES.remove(player.getUUID());

        player.displayClientMessage(
                Component.translatable(
                        "message.powernetwork.wire.connected"
                ).withStyle(ChatFormatting.GREEN),
                true
        );

        PowerNetwork.LOGGER.debug(
                "Created routed wire from {} to {} with {} support points",
                start.anchor(),
                end.anchor(),
                route.supports.size()
        );

        return InteractionResult.SUCCESS;
    }

    public static void clearSelection(UUID playerId) {
        ROUTES.remove(playerId);
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

            boolean cancelled = ROUTES.remove(player.getUUID()) != null;

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
