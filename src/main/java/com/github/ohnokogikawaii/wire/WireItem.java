
package com.github.ohnokogikawaii.wire;

import com.github.ohnokogikawaii.PowerNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WireItem extends Item {

    private static final Map<UUID, BlockPos> FIRST_ENDPOINTS =
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

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos().immutable();

        if (!(level.getBlockState(clickedPos).getBlock()
                instanceof TerminalBlock)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }

        UUID playerId = context.getPlayer().getUUID();
        BlockPos first = FIRST_ENDPOINTS.get(playerId);

        // 1個目の端子を選択
        if (first == null) {
            FIRST_ENDPOINTS.put(playerId, clickedPos);

            context.getPlayer().displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.first_endpoint"
                    ).withStyle(ChatFormatting.YELLOW),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 同じ端子を2回選択することを禁止
        if (first.equals(clickedPos)) {
            context.getPlayer().displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.same_endpoint"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 2個目の端子が選択されたので、選択状態を解除
        FIRST_ENDPOINTS.remove(playerId);

        double distance = Math.sqrt(first.distSqr(clickedPos));
        WireType wireType = getWireType();

        if (distance < wireType.getMinimumLength()) {
            context.getPlayer().displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.too_close"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        if (distance > wireType.getMaximumLength()) {
            context.getPlayer().displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.too_long"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        // 端点間を覆う範囲を検索する。
        // ワイヤーのEntityは端点間の中央に配置されているため、
        // この範囲で既存の接続を検索できる。
        AABB searchArea = new AABB(
                Math.min(first.getX(), clickedPos.getX()),
                Math.min(first.getY(), clickedPos.getY()),
                Math.min(first.getZ(), clickedPos.getZ()),
                Math.max(first.getX(), clickedPos.getX()) + 1.0,
                Math.max(first.getY(), clickedPos.getY()) + 1.0,
                Math.max(first.getZ(), clickedPos.getZ()) + 1.0
        ).inflate(1.0);

        for (WireEntity existing : level.getEntitiesOfClass(
                WireEntity.class,
                searchArea,
                Entity::isAlive
        )) {
            boolean sameDirection =
                    existing.getEndpointA().equals(first)
                            && existing.getEndpointB().equals(clickedPos);

            boolean reverseDirection =
                    existing.getEndpointA().equals(clickedPos)
                            && existing.getEndpointB().equals(first);

            if (sameDirection || reverseDirection) {
                context.getPlayer().displayClientMessage(
                        Component.literal(
                                "These terminals are already connected."
                        ).withStyle(ChatFormatting.RED),
                        true
                );

                return InteractionResult.SUCCESS;
            }
        }

        // 重複がなければワイヤーを生成
        WireEntity wire = new WireEntity(
                level,
                first,
                clickedPos,
                wireTypeId
        );

        if (!level.addFreshEntity(wire)) {
            context.getPlayer().displayClientMessage(
                    Component.literal(
                            "Failed to create the wire."
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        context.getPlayer().displayClientMessage(
                Component.translatable(
                        "message.powernetwork.wire.connected"
                ).withStyle(ChatFormatting.GREEN),
                true
        );

        PowerNetwork.LOGGER.debug(
                "Created {} wire between {} and {} (distance {} blocks)",
                wireTypeId,
                first,
                clickedPos,
                distance
        );

        return InteractionResult.SUCCESS;
    }

    public static void clearSelection(UUID playerId) {
        FIRST_ENDPOINTS.remove(playerId);
    }
}