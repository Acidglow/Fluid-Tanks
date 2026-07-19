package acidglow.fluidtanks.tank;

import acidglow.fluidtanks.AcidglowsFluidTanks;
import com.mojang.brigadier.Command;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class CopperConnectedTextureDebug {
    private static final int COLUMNS = 8;
    private static final int SPACING = 5;

    private CopperConnectedTextureDebug() {
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(AcidglowsFluidTanks.MODID)
                .then(Commands.literal("debug_connected_textures")
                        .executes(context -> placeDebugFormations(context.getSource()))));
    }

    private static int placeDebugFormations(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition()).offset(0, 1, 3);

        int index = 0;
        for (CopperConnectedTextures.TextureState state : CopperConnectedTextures.textureStates()) {
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            BlockPos center = origin.offset(column * SPACING, 0, row * SPACING);
            clearNorthFacePattern(level, center);
            placeNorthFacePattern(level, center, state.connections());
            index++;
        }

        int placed = index;
        source.sendSuccess(
                () -> Component.literal("Placed " + placed + " copper connected-texture debug formations from " + origin.toShortString()),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static void clearNorthFacePattern(ServerLevel level, BlockPos center) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                level.setBlock(center.offset(x, y, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void placeNorthFacePattern(ServerLevel level, BlockPos center, CopperConnectedTextures.Connections connections) {
        BlockState copper = AcidglowsFluidTanks.TANK_BLOCKS.get(FluidTankTier.COPPER).get().defaultBlockState();
        CopperConnectedTextures.FaceOffsets offsets = CopperConnectedTextures.getFaceLocalOffsets(Direction.NORTH);

        place(level, center, copper);
        if (connections.u()) {
            place(level, center.relative(offsets.up()), copper);
        }
        if (connections.d()) {
            place(level, center.relative(offsets.down()), copper);
        }
        if (connections.l()) {
            place(level, center.relative(offsets.left()), copper);
        }
        if (connections.r()) {
            place(level, center.relative(offsets.right()), copper);
        }
        if (connections.u() && connections.l() && !connections.missingUL()) {
            place(level, center.relative(offsets.up()).relative(offsets.left()), copper);
        }
        if (connections.u() && connections.r() && !connections.missingUR()) {
            place(level, center.relative(offsets.up()).relative(offsets.right()), copper);
        }
        if (connections.d() && connections.l() && !connections.missingDL()) {
            place(level, center.relative(offsets.down()).relative(offsets.left()), copper);
        }
        if (connections.d() && connections.r() && !connections.missingDR()) {
            place(level, center.relative(offsets.down()).relative(offsets.right()), copper);
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }
}
