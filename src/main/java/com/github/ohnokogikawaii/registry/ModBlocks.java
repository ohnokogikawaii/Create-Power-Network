package com.github.ohnokogikawaii.registry;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.wire.TerminalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(PowerNetwork.MODID);

    public static final DeferredBlock<TerminalBlock> TERMINAL =
            BLOCKS.registerBlock(
                    "terminal",
                    TerminalBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 6.0F)
                            .requiresCorrectToolForDrops()
            );

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}