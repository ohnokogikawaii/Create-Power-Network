
package com.github.ohnokogikawaii.registry;

import com.github.ohnokogikawaii.PowerNetwork;
import com.github.ohnokogikawaii.hub_connector.HubConnectorBlock;
import com.github.ohnokogikawaii.insulator.InsulatorBlock;
import com.github.ohnokogikawaii.terminal.TerminalBlock;
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

    public static final DeferredBlock<HubConnectorBlock> HUB_CONNECTOR =
            BLOCKS.registerBlock(
                    "hub_connector",
                    HubConnectorBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 6.0F)
                            .requiresCorrectToolForDrops()
            );

    public static final DeferredBlock<InsulatorBlock> LOW_VOLTAGE_INSULATOR =
            BLOCKS.registerBlock(
                    "low_voltage_insulator",
                    p -> new InsulatorBlock(p, InsulatorBlock.Type.LOW_VOLTAGE),
                    insulatorProperties()
            );

    public static final DeferredBlock<InsulatorBlock> HIGH_VOLTAGE_INSULATOR =
            BLOCKS.registerBlock(
                    "high_voltage_insulator",
                    p -> new InsulatorBlock(p, InsulatorBlock.Type.HIGH_VOLTAGE),
                    insulatorProperties()
            );

    public static final DeferredBlock<InsulatorBlock> EXTRA_HIGH_VOLTAGE_INSULATOR =
            BLOCKS.registerBlock(
                    "extra_high_voltage_insulator",
                    p -> new InsulatorBlock(p, InsulatorBlock.Type.EXTRA_HIGH_VOLTAGE),
                    insulatorProperties()
            );

    public static final DeferredBlock<InsulatorBlock> TRANSMISSION_LINE_INSULATOR =
            BLOCKS.registerBlock(
                    "transmission_line_insulator",
                    p -> new InsulatorBlock(p, InsulatorBlock.Type.TRANSMISSION_LINE),
                    insulatorProperties()
            );

    private static BlockBehaviour.Properties insulatorProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.QUARTZ)
                .strength(1.5F, 3.0F)
                .noOcclusion();
    }

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}