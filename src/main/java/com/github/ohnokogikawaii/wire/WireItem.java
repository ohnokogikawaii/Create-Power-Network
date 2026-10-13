
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
}
