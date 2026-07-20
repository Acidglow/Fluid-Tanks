package acidglow.fluidtanks.tank;

import acidglow.fluidtanks.AcidglowsFluidTanks;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

public class FluidTankBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 1.0, 0.9375);
    private static final ThreadLocal<BlockPos> PLACEMENT_TARGET = new ThreadLocal<>();
    private static final Map<UUID, SelectedTank> WRENCH_SELECTIONS = new HashMap<>();

    private final FluidTankTier tier;

    public FluidTankBlock(FluidTankTier tier, BlockBehaviour.Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new FluidTankBlock(tier, properties));
    }

    public FluidTankTier tier() {
        return tier;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos placedPos = context.getClickedPos();
        BlockPos clickedPos = context.replacingClickedOnBlock()
                ? placedPos
                : placedPos.relative(context.getClickedFace().getOpposite());
        if (!context.replacingClickedOnBlock() && context.getLevel().getBlockEntity(clickedPos) instanceof FluidTankBlockEntity) {
            PLACEMENT_TARGET.set(clickedPos);
        } else {
            PLACEMENT_TARGET.remove();
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new FluidTankBlockEntity(worldPosition, blockState);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return neighborState.getBlock() instanceof FluidTankBlock;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return CopperConnectedTextures.supportsTier(tier) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(LIT) ? 15 : 0;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank)) {
            return InteractionResult.PASS;
        }

        if (hand == InteractionHand.MAIN_HAND && itemStack.getItem() == AcidglowsFluidTanks.WRENCH.get()) {
            return useWrenchOnTank(level, pos, player, tank);
        }

        if (itemStack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        boolean handled = FluidUtil.interactWithFluidHandler(player, hand, pos, tank.fluidHandler(), null);
        if (handled) {
            return InteractionResult.SUCCESS;
        }
        return isFilledFluidContainer(itemStack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(state.getBlock())) {
            consolidate(level, pos);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (!level.isClientSide()) {
            BlockPos placementTarget = PLACEMENT_TARGET.get();
            if (placementTarget != null
                    && level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank
                    && level.getBlockEntity(placementTarget) instanceof FluidTankBlockEntity target) {
                tank.connectByWrench(target);
            }
            consolidate(level, pos);
            if (level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) {
                tank.refreshLitState();
            }
        }
        PLACEMENT_TARGET.remove();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) {
            tank.prepareForRemoval();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack destroyedWith) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);

        if (!level.isClientSide() && !player.getAbilities().instabuild) {
            ItemStack drop = new ItemStack(this);
            if (blockEntity instanceof FluidTankBlockEntity tank) {
                tank.applyFluidToItem(drop);
            }
            popResource(level, pos, drop);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide()) {
            consolidate(level, pos);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (level instanceof Level realLevel && !realLevel.isClientSide()) {
            consolidate(realLevel, pos);
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        for (Direction direction : Direction.values()) {
            consolidate(level, pos.relative(direction));
        }
    }

    private static void consolidate(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank && tank.networkFluid().getFluid() != Fluids.EMPTY) {
            tank.consolidateNetwork();
        }
    }

    private static boolean isFilledFluidContainer(ItemStack itemStack) {
        return !FluidUtil.getFirstStackContained(itemStack).isEmpty();
    }

    private static InteractionResult useWrenchOnTank(Level level, BlockPos pos, Player player, FluidTankBlockEntity tank) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        UUID playerId = player.getUUID();
        ResourceKey<Level> dimension = level.dimension();
        SelectedTank selected = WRENCH_SELECTIONS.get(playerId);
        if (selected == null || selected.dimension() != dimension) {
            selectTank(playerId, dimension, pos, tank);
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(selected.pos()) instanceof FluidTankBlockEntity selectedTank)) {
            WRENCH_SELECTIONS.remove(playerId);
            selectTank(playerId, dimension, pos, tank);
            return InteractionResult.SUCCESS;
        }

        if (selected.pos().equals(pos)) {
            selectedTank.clearWrenchOutline();
            WRENCH_SELECTIONS.remove(playerId);
            return InteractionResult.SUCCESS;
        }

        boolean valid = false;
        if (isAdjacent(selected.pos(), pos)) {
            valid = selectedTank.isDirectlyConnectedTo(tank)
                    ? selectedTank.disconnectByWrench(tank)
                    : selectedTank.connectByWrench(tank);
        }

        selectedTank.showWrenchResultOutline(valid);
        tank.showWrenchResultOutline(valid);
        WRENCH_SELECTIONS.remove(playerId);
        return InteractionResult.SUCCESS;
    }

    private static void selectTank(UUID playerId, ResourceKey<Level> dimension, BlockPos pos, FluidTankBlockEntity tank) {
        WRENCH_SELECTIONS.put(playerId, new SelectedTank(dimension, pos.immutable()));
        tank.showWrenchSelectionOutline();
    }

    private static boolean isAdjacent(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ()) == 1;
    }

    private record SelectedTank(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
