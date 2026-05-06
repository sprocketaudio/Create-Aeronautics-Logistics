package net.sprocketgames.create_aeronautics_automated_logistics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class AutopilotSeatBlock extends Block {
    public static final MapCodec<AutopilotSeatBlock> CODEC = simpleCodec(AutopilotSeatBlock::new);

    public AutopilotSeatBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
