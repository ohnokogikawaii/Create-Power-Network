
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
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WireItem extends Item {

    private record EndpointSelection(
            BlockPos anchor,
            Vec3 position
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

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos().immutable();

        if (!(level.getBlockState(clickedPos).getBlock()
                instanceof WireConnectionPointProvider provider)) {
            return InteractionResult.PASS;
        }

        var state = level.getBlockState(clickedPos);

        WireConnectionPointProvider.ConnectionPoint connectionPoint =
                provider.getNearestConnectionPoint(
                        clickedPos,
                        context.getClickLocation(),
                        state
                );

        if (connectionPoint == null) {
            return InteractionResult.PASS;
        }

        Vec3 clickedPoint = connectionPoint.worldPosition(clickedPos);

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }

        UUID playerId = context.getPlayer().getUUID();

        EndpointSelection second = new EndpointSelection(
                clickedPos,
                clickedPoint
        );

        EndpointSelection first = FIRST_ENDPOINTS.get(playerId);

        if (first == null) {
            FIRST_ENDPOINTS.put(playerId, second);

            context.getPlayer().displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.first_endpoint"
                    ).withStyle(ChatFormatting.YELLOW),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        if (first.position().distanceToSqr(second.position()) < 1.0E-8) {
            context.getPlayer().displayClientMessage(
                    Component.translatable(
                            "message.powernetwork.wire.same_endpoint"
                    ).withStyle(ChatFormatting.RED),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        FIRST_ENDPOINTS.remove(playerId);

        double distance = first.position().distanceTo(second.position());
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

        AABB searchArea = new AABB(
                Math.min(first.anchor().getX(), second.anchor().getX()),
                Math.min(first.anchor().getY(), second.anchor().getY()),
                Math.min(first.anchor().getZ(), second.anchor().getZ()),
                Math.max(first.anchor().getX(), second.anchor().getX()) + 1.0,
                Math.max(first.anchor().getY(), second.anchor().getY()) + 1.0,
                Math.max(first.anchor().getZ(), second.anchor().getZ()) + 1.0
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
                context.getPlayer().displayClientMessage(
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
                false,
                false,
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
                "Created {} wire between {} at {} and {} at {} (distance {} blocks)",
                wireTypeId,
                first.anchor(),
                first.position(),
                second.anchor(),
                second.position(),
                distance
        );

        return InteractionResult.SUCCESS;
    }

    public static void clearSelection(UUID playerId) {
        FIRST_ENDPOINTS.remove(playerId);
    }
}