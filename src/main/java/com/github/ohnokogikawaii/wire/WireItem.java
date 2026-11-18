
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

        WireEntity targetWire = findWireForSupport(
                level,
                supportPosition
        );

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

        player.displayClientMessage(
                Component.literal("碍子に電線を固定しました。")
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        return InteractionResult.SUCCESS;
    }

    /**
     * 碍子の支持位置を通過している電線を検索する。
     *
     * 電線のA-B間の曲線に対して最も近い位置を調べる。
     * 端点付近は支持点として使用しない。
     */
    private WireEntity findWireForSupport(
            Level level,
            Vec3 supportPosition
    ) {
        final double MAX_DISTANCE = 0.45;
        final double MIN_T = 0.02;
        final double MAX_T = 0.98;
        final double SEARCH_RADIUS = 64.0;

        AABB searchArea = new AABB(
                supportPosition,
                supportPosition
        ).inflate(SEARCH_RADIUS);

        WireEntity bestWire = null;
        double bestDistance = MAX_DISTANCE;

        for (WireEntity wire : level.getEntitiesOfClass(
                WireEntity.class,
                searchArea,
                Entity::isAlive
        )) {
            Vec3 a = wire.getEndpointAWorldPosition();
            Vec3 b = wire.getEndpointBWorldPosition();

            Vec3 direction = b.subtract(a);
            double lengthSquared = direction.lengthSqr();

            if (lengthSquared < 1.0E-8) {
                continue;
            }

            double t = supportPosition.subtract(a)
                    .dot(direction) / lengthSquared;

            if (t < MIN_T || t > MAX_T) {
                continue;
            }

            WireType type = wire.getWireType();

            boolean canSag = type != null && type.canSag();

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

            bestDistance = distance;
            bestWire = wire;
        }

        return bestWire;
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
