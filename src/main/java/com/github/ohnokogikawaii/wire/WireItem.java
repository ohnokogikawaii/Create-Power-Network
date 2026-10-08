package com.github.ohnokogikawaii.wire;

import com.github.ohnokogikawaii.PowerNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First-stage physical wire installation item.
 *
 * Right-click terminal A, then terminal B to create a wire.
 */
public class WireItem extends Item {

    private static final Map<UUID, BlockPos> FIRST_ENDPOINTS = new ConcurrentHashMap<>();

    public WireItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (!(level.getBlockState(context.getClickedPos()).getBlock() instanceof TerminalBlock)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }

        UUID playerId = context.getPlayer().getUUID();
        BlockPos clickedPos = context.getClickedPos().immutable();
        BlockPos first = FIRST_ENDPOINTS.get(playerId);

        if (first == null) {
            FIRST_ENDPOINTS.put(playerId, clickedPos);

            context.getPlayer().displayClientMessage(
                    Component.translatable("message.powernetwork.wire.first_endpoint")
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );

            return InteractionResult.SUCCESS;
        }

        if (first.equals(clickedPos)) {
            context.getPlayer().displayClientMessage(
                    Component.translatable("message.powernetwork.wire.same_endpoint")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        FIRST_ENDPOINTS.remove(playerId);

        double distance = Math.sqrt(first.distSqr(clickedPos));

        if (distance < 1.0D) {
            context.getPlayer().displayClientMessage(
                    Component.translatable("message.powernetwork.wire.too_close")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        WireEntity wire = new WireEntity(level, first, clickedPos);
        level.addFreshEntity(wire);

        context.getPlayer().displayClientMessage(
                Component.translatable("message.powernetwork.wire.connected")
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        PowerNetwork.LOGGER.debug(
                "Created physical wire between {} and {} (distance {} blocks)",
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
