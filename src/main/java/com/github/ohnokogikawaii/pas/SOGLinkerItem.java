
package com.github.ohnokogikawaii.pas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SOGLinkerItem extends Item {

    private static final String SELECTED_SOG_POS = "SelectedSOGPos";
    private static final String SELECTED_SOG_DIMENSION = "SelectedSOGDimension";

    public SOGLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockEntity clickedEntity = level.getBlockEntity(clickedPos);

        if (clickedEntity instanceof SOGBlockEntity) {
            selectSog(context, clickedPos);
            return InteractionResult.SUCCESS;
        }

        if (clickedEntity instanceof PASBlockEntity pas) {
            return connectPas(context, pas);
        }

        return InteractionResult.PASS;
    }

    private void selectSog(UseOnContext context, BlockPos sogPos) {
        var stack = context.getItemInHand();

        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> {
                    tag.putLong(SELECTED_SOG_POS, sogPos.asLong());
                    tag.putString(
                            SELECTED_SOG_DIMENSION,
                            context.getLevel().dimension().location().toString()
                    );
                }
        );

        context.getPlayer().displayClientMessage(
                Component.literal(
                        "SOGを選択しました。接続先のPASを右クリックしてください。"
                ),
                false
        );
    }

    private InteractionResult connectPas(
            UseOnContext context,
            PASBlockEntity pas
    ) {
        var stack = context.getItemInHand();

        CompoundTag tag = stack
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag();

        if (!tag.contains(SELECTED_SOG_POS)
                || !tag.contains(SELECTED_SOG_DIMENSION)) {
            context.getPlayer().displayClientMessage(
                    Component.literal("先にSOGを選択してください。"),
                    false
            );
            return InteractionResult.FAIL;
        }

        String selectedDimension =
                tag.getString(SELECTED_SOG_DIMENSION);

        String currentDimension =
                context.getLevel().dimension().location().toString();

        if (!selectedDimension.equals(currentDimension)) {
            context.getPlayer().displayClientMessage(
                    Component.literal("別のディメンションにあるSOGは接続できません。"),
                    false
            );
            return InteractionResult.FAIL;
        }

        BlockPos sogPos = BlockPos.of(tag.getLong(SELECTED_SOG_POS));

        if (!context.getLevel().hasChunkAt(sogPos)) {
            context.getPlayer().displayClientMessage(
                    Component.literal("選択したSOGのチャンクが読み込まれていません。"),
                    false
            );
            return InteractionResult.FAIL;
        }

        BlockEntity sogEntity =
                context.getLevel().getBlockEntity(sogPos);

        if (!(sogEntity instanceof SOGBlockEntity sog)) {
            context.getPlayer().displayClientMessage(
                    Component.literal("選択した位置にSOGがありません。"),
                    false
            );
            return InteractionResult.FAIL;
        }

        if (!sog.linkToPas(pas)) {
            context.getPlayer().displayClientMessage(
                    Component.literal(
                            "接続できませんでした。既存の接続先や読み込み状態を確認してください。"
                    ),
                    false
            );
            return InteractionResult.FAIL;
        }

        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                data -> {
                    data.remove(SELECTED_SOG_POS);
                    data.remove(SELECTED_SOG_DIMENSION);
                }
        );

        context.getPlayer().displayClientMessage(
                Component.literal("SOGとPASを接続しました。"),
                false
        );

        return InteractionResult.SUCCESS;
    }
}
