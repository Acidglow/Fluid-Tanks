package acidglow.fluidtanks.tank;

import acidglow.fluidtanks.AcidglowsFluidTanks;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

public class FluidTankBlockEntity extends BlockEntity {
    private static final Codec<List<BlockPos>> LINKED_TANKS_CODEC = BlockPos.CODEC.listOf();
    private static final String LINKED_TANKS_TAG = "linkedTanks";
    private static final String BLOCKED_TANKS_TAG = "blockedTanks";
    private static final String WRENCH_OUTLINE_COLOR_TAG = "wrenchOutlineColor";
    private static final String WRENCH_OUTLINE_EXPIRES_AT_TAG = "wrenchOutlineExpiresAt";
    private static final int WRENCH_OUTLINE_SELECTED_COLOR = 0xFFFFFF00;
    private static final int WRENCH_OUTLINE_VALID_COLOR = 0xFF35E060;
    private static final int WRENCH_OUTLINE_INVALID_COLOR = 0xFFFF4040;
    private static final int WRENCH_OUTLINE_DURATION_TICKS = 20;

    private FluidStack fluid = FluidStack.EMPTY;
    private FluidStack networkFluid = FluidStack.EMPTY;
    private FluidStack removalDropFluid = FluidStack.EMPTY;
    private final Set<BlockPos> linkedTanks = new HashSet<>();
    private final Set<BlockPos> blockedTanks = new HashSet<>();
    private int wrenchOutlineColor;
    private long wrenchOutlineExpiresAt;
    private final NetworkFluidHandler fluidHandler = new NetworkFluidHandler(this);

    public FluidTankBlockEntity(BlockPos pos, BlockState blockState) {
        super(AcidglowsFluidTanks.FLUID_TANK_BLOCK_ENTITY.get(), pos, blockState);
    }

    public ResourceHandler<FluidResource> fluidHandler() {
        return fluidHandler;
    }

    public FluidStack storedFluid() {
        return fluid.copy();
    }

    public int capacity() {
        if (getBlockState().getBlock() instanceof FluidTankBlock tankBlock) {
            return tankBlock.tier().capacity();
        }
        return 0;
    }

    public FluidStack networkFluid() {
        FluidResource resource = detectNetworkResource();
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        TankNetwork network = network(resource);
        return network.amount() <= 0 ? FluidStack.EMPTY : resource.toStack(network.amount());
    }

    public FluidResource networkResource() {
        FluidResource resource = detectNetworkResource();
        if (resource.isEmpty()) {
            return FluidResource.EMPTY;
        }

        return network(resource).amount() <= 0 ? FluidResource.EMPTY : resource;
    }

    public boolean connectsTo(FluidTankBlockEntity other) {
        if (!linkedTanks.contains(other.worldPosition)) {
            return false;
        }
        if (isBlockedFrom(other.worldPosition) || other.isBlockedFrom(worldPosition)) {
            return false;
        }

        FluidResource resource = detectNetworkResource();
        if (resource.isEmpty()) {
            return isUnclaimedEmpty() && other.isUnclaimedEmpty();
        }
        return Objects.equals(resource, other.detectNetworkResource());
    }

    public boolean isDirectlyConnectedTo(FluidTankBlockEntity other) {
        return isAdjacentTo(other.worldPosition) && connectsTo(other);
    }

    public boolean canWrenchConnectTo(FluidTankBlockEntity other) {
        if (other == this || level == null || other.level != level || !isAdjacentTo(other.worldPosition)) {
            return false;
        }

        FluidResource resource = networkResource();
        FluidResource otherResource = other.networkResource();
        return resource.isEmpty() || otherResource.isEmpty() || Objects.equals(resource, otherResource);
    }

    public boolean connectByWrench(FluidTankBlockEntity other) {
        if (!canWrenchConnectTo(other)) {
            return false;
        }

        FluidResource resource = networkResource();
        if (resource.isEmpty()) {
            resource = other.networkResource();
        }

        linkTo(other.worldPosition);
        other.linkTo(worldPosition);
        unblockFrom(other.worldPosition);
        other.unblockFrom(worldPosition);

        if (resource.isEmpty()) {
            for (FluidTankBlockEntity tank : emptyNetwork().tanks()) {
                tank.syncChanged();
            }
            return true;
        }

        TankNetwork network = collectNetwork(resource, true);
        storeOnController(network.tanks(), resource, network.amount());
        for (FluidTankBlockEntity tank : network.tanks()) {
            tank.syncChanged();
        }
        return true;
    }

    public boolean disconnectByWrench(FluidTankBlockEntity other) {
        if (!isDirectlyConnectedTo(other)) {
            return false;
        }

        FluidResource resource = detectNetworkResource();
        if (resource.isEmpty()) {
            blockFrom(other.worldPosition);
            other.blockFrom(worldPosition);
            return true;
        }

        TankNetwork network = network(resource);
        Map<FluidTankBlockEntity, Integer> localAmounts = layeredAmounts(network);
        List<FluidTankBlockEntity> networkTanks = network.tanks();

        unlinkFrom(other.worldPosition);
        other.unlinkFrom(worldPosition);
        blockFrom(other.worldPosition);
        other.blockFrom(worldPosition);
        for (FluidTankBlockEntity tank : networkTanks) {
            tank.setFluidDirect(FluidStack.EMPTY, FluidResource.EMPTY);
        }

        for (TankNetwork group : remainingGroups(networkTanks)) {
            int groupAmount = group.tanks().stream()
                    .mapToInt(tank -> localAmounts.getOrDefault(tank, 0))
                    .sum();
            if (groupAmount > 0) {
                storeOnController(group.tanks(), resource, groupAmount);
            }
        }
        return true;
    }

    public void showWrenchSelectionOutline() {
        showWrenchOutline(WRENCH_OUTLINE_SELECTED_COLOR, -1L);
    }

    public void showWrenchResultOutline(boolean valid) {
        long expiresAt = level == null ? 0L : level.getGameTime() + WRENCH_OUTLINE_DURATION_TICKS;
        showWrenchOutline(valid ? WRENCH_OUTLINE_VALID_COLOR : WRENCH_OUTLINE_INVALID_COLOR, expiresAt);
    }

    public void clearWrenchOutline() {
        showWrenchOutline(0, 0L);
    }

    public int wrenchOutlineColor() {
        if (wrenchOutlineColor == 0) {
            return 0;
        }
        if (wrenchOutlineExpiresAt >= 0L && level != null && level.getGameTime() >= wrenchOutlineExpiresAt) {
            return 0;
        }
        return wrenchOutlineColor;
    }

    public boolean isUnclaimedEmpty() {
        return fluid.isEmpty() && networkFluid.isEmpty();
    }

    public float networkFillRatio() {
        FluidResource resource = detectNetworkResource();
        if (resource.isEmpty()) {
            return 0.0F;
        }
        TankNetwork network = network(resource);
        return network.capacity() <= 0 ? 0.0F : (float) network.amount() / (float) network.capacity();
    }

    public float blockFillRatio() {
        FluidResource resource = detectNetworkResource();
        if (resource.isEmpty()) {
            return 0.0F;
        }

        TankNetwork network = network(resource);
        int tankCapacity = capacity();
        if (network.amount() <= 0 || tankCapacity <= 0) {
            return 0.0F;
        }

        int localAmount = layeredAmounts(network).getOrDefault(this, 0);
        return (float) localAmount / (float) tankCapacity;
    }

    public FluidConnections fluidConnections() {
        FluidResource resource = detectNetworkResource();
        if (resource.isEmpty()) {
            return FluidConnections.NONE;
        }

        TankNetwork network = network(resource);
        Map<FluidTankBlockEntity, Integer> localAmounts = layeredAmounts(network);
        if (localAmounts.getOrDefault(this, 0) <= 0) {
            return FluidConnections.NONE;
        }

        Set<BlockPos> filledPositions = new HashSet<>();
        for (Map.Entry<FluidTankBlockEntity, Integer> entry : localAmounts.entrySet()) {
            if (entry.getValue() > 0) {
                filledPositions.add(entry.getKey().worldPosition);
            }
        }

        BlockPos north = worldPosition.relative(Direction.NORTH);
        BlockPos south = worldPosition.relative(Direction.SOUTH);
        BlockPos east = worldPosition.relative(Direction.EAST);
        BlockPos west = worldPosition.relative(Direction.WEST);
        return new FluidConnections(
                filledPositions.contains(north),
                filledPositions.contains(south),
                filledPositions.contains(east),
                filledPositions.contains(west),
                filledPositions.contains(north.relative(Direction.EAST)),
                filledPositions.contains(north.relative(Direction.WEST)),
                filledPositions.contains(south.relative(Direction.EAST)),
                filledPositions.contains(south.relative(Direction.WEST))
        );
    }

    public void consolidateNetwork() {
        FluidResource resource = detectNetworkResource();
        if (!resource.isEmpty()) {
            TankNetwork network = network(resource);
            storeOnController(network.tanks(), resource, network.amount());
        }
        updateLitState();
    }

    public void refreshLitState() {
        updateLitState();
    }

    public void applyFluidToItem(ItemStack stack) {
        FluidStack stackFluid = removalDropFluid.isEmpty() ? fluid : removalDropFluid;
        if (stackFluid.isEmpty() || level == null) {
            return;
        }

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        output.store("fluid", FluidStack.OPTIONAL_CODEC, stackFluid.copy());
        BlockItem.setBlockEntityData(stack, AcidglowsFluidTanks.FLUID_TANK_BLOCK_ENTITY.get(), output);
    }

    public void prepareForRemoval() {
        removalDropFluid = FluidStack.EMPTY;

        FluidResource resource = detectNetworkResource();
        if (resource.isEmpty()) {
            unlinkFromAllTanks();
            return;
        }

        TankNetwork network = network(resource);
        if (network.tanks().isEmpty() || network.capacity() <= 0 || network.amount() <= 0) {
            unlinkFromAllTanks();
            return;
        }

        Map<FluidTankBlockEntity, Integer> localAmounts = layeredAmounts(network);
        int removedAmount = localAmounts.getOrDefault(this, 0);
        int remainingAmount = network.amount() - removedAmount;

        removalDropFluid = removedAmount > 0 ? resource.toStack(removedAmount) : FluidStack.EMPTY;
        unlinkFromAllTanks();

        List<FluidTankBlockEntity> remainingTanks = network.tanks().stream()
                .filter(tank -> tank != this)
                .toList();
        for (FluidTankBlockEntity tank : network.tanks()) {
            tank.setFluidDirect(FluidStack.EMPTY, FluidResource.EMPTY);
        }

        if (remainingAmount <= 0 || remainingTanks.isEmpty()) {
            return;
        }

        List<TankNetwork> groups = remainingGroups(remainingTanks);
        for (TankNetwork group : groups) {
            int groupAmount = group.tanks().stream()
                    .mapToInt(tank -> localAmounts.getOrDefault(tank, 0))
                    .sum();
            if (groupAmount > 0) {
                storeOnController(group.tanks(), resource, groupAmount);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fluid = input.read("fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        networkFluid = input.read("networkFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        linkedTanks.clear();
        input.read(LINKED_TANKS_TAG, LINKED_TANKS_CODEC).ifPresent(tanks -> tanks.stream()
                .filter(this::isAdjacentTo)
                .forEach(linkedTanks::add));
        blockedTanks.clear();
        input.read(BLOCKED_TANKS_TAG, LINKED_TANKS_CODEC).ifPresent(tanks -> tanks.stream()
                .filter(this::isAdjacentTo)
                .forEach(blockedTanks::add));
        wrenchOutlineColor = input.getIntOr(WRENCH_OUTLINE_COLOR_TAG, 0);
        wrenchOutlineExpiresAt = input.getLongOr(WRENCH_OUTLINE_EXPIRES_AT_TAG, 0L);
        if (!fluid.isEmpty()) {
            networkFluid = FluidResource.of(fluid).toStack(1);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("fluid", FluidStack.OPTIONAL_CODEC, fluid);
        if (!networkFluid.isEmpty()) {
            output.store("networkFluid", FluidStack.OPTIONAL_CODEC, networkFluid);
        }
        if (!linkedTanks.isEmpty()) {
            output.store(LINKED_TANKS_TAG, LINKED_TANKS_CODEC, linkedTanks.stream().sorted().toList());
        }
        if (!blockedTanks.isEmpty()) {
            output.store(BLOCKED_TANKS_TAG, LINKED_TANKS_CODEC, blockedTanks.stream().sorted().toList());
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        if (wrenchOutlineColor != 0) {
            tag.putInt(WRENCH_OUTLINE_COLOR_TAG, wrenchOutlineColor);
            tag.putLong(WRENCH_OUTLINE_EXPIRES_AT_TAG, wrenchOutlineExpiresAt);
        }
        return tag;
    }

    private FluidResource detectNetworkResource() {
        if (!fluid.isEmpty()) {
            return FluidResource.of(fluid);
        }
        return networkFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(networkFluid);
    }

    private TankNetwork network(FluidResource target) {
        return collectNetwork(target, false);
    }

    private TankNetwork collectNetwork(FluidResource target, boolean includeUnclaimedEmpty) {
        if (level == null || target.isEmpty()) {
            return TankNetwork.EMPTY;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        List<FluidTankBlockEntity> tanks = new ArrayList<>();

        queue.add(worldPosition);
        seen.add(worldPosition);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            BlockEntity blockEntity = level.getBlockEntity(current);
            if (!(blockEntity instanceof FluidTankBlockEntity tank) || !tank.canJoinNetwork(target, includeUnclaimedEmpty)) {
                continue;
            }

            tanks.add(tank);
            for (BlockPos next : tank.connectedPositions()) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }

        tanks.sort(Comparator.comparing(FluidTankBlockEntity::getBlockPos));
        int amount = tanks.stream().mapToInt(tank -> tank.fluid.isEmpty() ? 0 : tank.fluid.getAmount()).sum();
        int capacity = tanks.stream().mapToInt(FluidTankBlockEntity::capacity).sum();
        return new TankNetwork(tanks, amount, capacity);
    }

    private TankNetwork emptyNetwork() {
        if (level == null || !fluid.isEmpty() || !networkFluid.isEmpty()) {
            return TankNetwork.EMPTY;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        List<FluidTankBlockEntity> tanks = new ArrayList<>();

        queue.add(worldPosition);
        seen.add(worldPosition);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            BlockEntity blockEntity = level.getBlockEntity(current);
            if (!(blockEntity instanceof FluidTankBlockEntity tank) || !tank.fluid.isEmpty() || !tank.networkFluid.isEmpty()) {
                continue;
            }

            tanks.add(tank);
            for (BlockPos next : tank.connectedPositions()) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }

        tanks.sort(Comparator.comparing(FluidTankBlockEntity::getBlockPos));
        int capacity = tanks.stream().mapToInt(FluidTankBlockEntity::capacity).sum();
        return new TankNetwork(tanks, 0, capacity);
    }

    private boolean canJoinNetwork(FluidResource target, boolean includeUnclaimedEmpty) {
        FluidResource resource = detectNetworkResource();
        if (!resource.isEmpty()) {
            return Objects.equals(resource, target);
        }
        return includeUnclaimedEmpty && isUnclaimedEmpty();
    }

    private List<BlockPos> connectedPositions() {
        List<BlockPos> positions = new ArrayList<>(linkedTanks.size());
        linkedTanks.stream()
                .filter(this::isAdjacentTo)
                .filter(pos -> !blockedTanks.contains(pos))
                .forEach(positions::add);
        return positions;
    }

    private static Map<FluidTankBlockEntity, Integer> layeredAmounts(TankNetwork network) {
        Map<FluidTankBlockEntity, Integer> amounts = new HashMap<>();
        if (network.tanks().isEmpty() || network.amount() <= 0) {
            return amounts;
        }

        Map<Integer, List<FluidTankBlockEntity>> layers = new TreeMap<>();
        for (FluidTankBlockEntity tank : network.tanks()) {
            layers.computeIfAbsent(tank.getBlockPos().getY(), y -> new ArrayList<>()).add(tank);
        }

        int remaining = network.amount();
        for (List<FluidTankBlockEntity> layer : layers.values()) {
            layer.sort(Comparator.comparing(FluidTankBlockEntity::getBlockPos));
            int layerCapacity = layer.stream().mapToInt(FluidTankBlockEntity::capacity).sum();
            int layerAmount = Math.min(remaining, layerCapacity);
            if (layerAmount <= 0 || layerCapacity <= 0) {
                break;
            }

            assignLayerAmounts(layer, layerCapacity, layerAmount, amounts);
            remaining -= layerAmount;
            if (remaining <= 0) {
                break;
            }
        }

        return amounts;
    }

    private static void assignLayerAmounts(List<FluidTankBlockEntity> layer, int layerCapacity, int layerAmount, Map<FluidTankBlockEntity, Integer> amounts) {
        int assigned = 0;
        for (int i = 0; i < layer.size(); i++) {
            FluidTankBlockEntity tank = layer.get(i);
            int tankCapacity = tank.capacity();
            int tankAmount = i == layer.size() - 1
                    ? layerAmount - assigned
                    : (int) ((long) layerAmount * tankCapacity / layerCapacity);
            tankAmount = Math.min(tankCapacity, Math.max(0, tankAmount));
            assigned += tankAmount;
            amounts.put(tank, tankAmount);
        }
    }

    private static void storeOnController(List<FluidTankBlockEntity> tanks, FluidResource resource, int amount) {
        if (tanks.isEmpty()) {
            return;
        }

        int storedAmount = Math.max(0, Math.min(amount, tanks.stream().mapToInt(FluidTankBlockEntity::capacity).sum()));
        FluidTankBlockEntity controller = tanks.stream().min(Comparator.comparing(FluidTankBlockEntity::getBlockPos)).orElseThrow();
        for (FluidTankBlockEntity tank : tanks) {
            FluidStack next = tank == controller && storedAmount > 0 ? resource.toStack(storedAmount) : FluidStack.EMPTY;
            tank.setFluidDirect(next, storedAmount > 0 ? resource : FluidResource.EMPTY);
        }
    }

    private static List<TankNetwork> remainingGroups(List<FluidTankBlockEntity> tanks) {
        Map<BlockPos, FluidTankBlockEntity> byPos = new HashMap<>();
        for (FluidTankBlockEntity tank : tanks) {
            byPos.put(tank.worldPosition, tank);
        }

        List<TankNetwork> groups = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (FluidTankBlockEntity start : tanks) {
            if (!seen.add(start.worldPosition)) {
                continue;
            }

            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            List<FluidTankBlockEntity> groupTanks = new ArrayList<>();
            queue.add(start.worldPosition);

            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                FluidTankBlockEntity tank = byPos.get(current);
                if (tank == null) {
                    continue;
                }

                groupTanks.add(tank);
                for (BlockPos next : tank.connectedPositions()) {
                    if (byPos.containsKey(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }

            groupTanks.sort(Comparator.comparing(FluidTankBlockEntity::getBlockPos));
            int capacity = groupTanks.stream().mapToInt(FluidTankBlockEntity::capacity).sum();
            groups.add(new TankNetwork(groupTanks, 0, capacity));
        }

        groups.sort(Comparator.comparing(group -> group.tanks().getFirst().getBlockPos()));
        return groups;
    }

    private void setFluidDirect(FluidStack next, FluidResource networkResource) {
        FluidStack nextNetworkFluid = networkResource.isEmpty() ? FluidStack.EMPTY : networkResource.toStack(1);
        if (!FluidStack.matches(fluid, next) || !FluidStack.matches(networkFluid, nextNetworkFluid)) {
            fluid = next.copy();
            networkFluid = nextNetworkFluid.copy();
            syncChanged();
        }
    }

    private void setNetworkFluidDirect(FluidResource networkResource) {
        setFluidDirect(fluid, networkResource);
    }

    private void linkTo(BlockPos pos) {
        if (isAdjacentTo(pos) && linkedTanks.add(pos.immutable())) {
            syncChanged();
        }
    }

    private void unlinkFrom(BlockPos pos) {
        if (linkedTanks.remove(pos)) {
            syncChanged();
        }
    }

    private void unlinkFromAllTanks() {
        if (level == null) {
            linkedTanks.clear();
            blockedTanks.clear();
            return;
        }

        Set<BlockPos> links = Set.copyOf(linkedTanks);
        Set<BlockPos> blocked = Set.copyOf(blockedTanks);
        linkedTanks.clear();
        blockedTanks.clear();
        clearWrenchOutline();
        syncChanged();
        for (BlockPos linkedPos : links) {
            if (level.getBlockEntity(linkedPos) instanceof FluidTankBlockEntity linkedTank) {
                linkedTank.unlinkFrom(worldPosition);
            }
        }
        for (BlockPos blockedPos : blocked) {
            if (level.getBlockEntity(blockedPos) instanceof FluidTankBlockEntity blockedTank) {
                blockedTank.unblockFrom(worldPosition);
            }
        }
    }

    private void blockFrom(BlockPos pos) {
        if (isAdjacentTo(pos) && blockedTanks.add(pos.immutable())) {
            syncChanged();
        }
    }

    private void unblockFrom(BlockPos pos) {
        if (blockedTanks.remove(pos)) {
            syncChanged();
        }
    }

    private boolean isBlockedFrom(BlockPos pos) {
        return blockedTanks.contains(pos);
    }

    private boolean isAdjacentTo(BlockPos pos) {
        return Math.abs(worldPosition.getX() - pos.getX())
                + Math.abs(worldPosition.getY() - pos.getY())
                + Math.abs(worldPosition.getZ() - pos.getZ()) == 1;
    }

    private void showWrenchOutline(int color, long expiresAt) {
        if (wrenchOutlineColor != color || wrenchOutlineExpiresAt != expiresAt) {
            wrenchOutlineColor = color;
            wrenchOutlineExpiresAt = expiresAt;
            syncChanged();
        }
    }

    private void syncChanged() {
        setChanged();
        if (level != null) {
            BlockState state = updateLitState();
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private BlockState updateLitState() {
        if (level == null) {
            return getBlockState();
        }

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FluidTankBlock)) {
            return state;
        }

        boolean lit = isLava(networkFluid);
        if (state.getValue(FluidTankBlock.LIT) == lit) {
            return state;
        }

        BlockState updatedState = state.setValue(FluidTankBlock.LIT, lit);
        level.setBlock(worldPosition, updatedState, Block.UPDATE_ALL);
        return updatedState;
    }

    private static boolean isLava(FluidStack stack) {
        return !stack.isEmpty() && (stack.getFluid() == Fluids.LAVA || stack.getFluid() == Fluids.FLOWING_LAVA);
    }

    private record TankNetwork(List<FluidTankBlockEntity> tanks, int amount, int capacity) {
        private static final TankNetwork EMPTY = new TankNetwork(List.of(), 0, 0);
    }

    public record FluidConnections(
            boolean north,
            boolean south,
            boolean east,
            boolean west,
            boolean northEast,
            boolean northWest,
            boolean southEast,
            boolean southWest
    ) {
        public static final FluidConnections NONE = new FluidConnections(false, false, false, false, false, false, false, false);
    }

    private record TankSnapshot(Map<BlockPos, FluidStack> fluids, Map<BlockPos, FluidStack> networkFluids) {
    }

    private static class NetworkFluidHandler extends SnapshotJournal<TankSnapshot> implements ResourceHandler<FluidResource> {
        private final FluidTankBlockEntity owner;
        private @Nullable FluidResource snapshotTarget;

        private NetworkFluidHandler(FluidTankBlockEntity owner) {
            this.owner = owner;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            checkIndex(index);
            return owner.detectNetworkResource();
        }

        @Override
        public long getAmountAsLong(int index) {
            checkIndex(index);
            FluidResource resource = owner.detectNetworkResource();
            return resource.isEmpty() ? 0 : owner.network(resource).amount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            checkIndex(index);
            FluidResource target = resource.isEmpty() ? owner.detectNetworkResource() : resource;
            if (target.isEmpty()) {
                return owner.emptyNetwork().capacity();
            }
            FluidResource current = owner.detectNetworkResource();
            if (current.isEmpty()) {
                return owner.emptyNetwork().capacity();
            }
            if (!Objects.equals(current, target)) {
                return 0;
            }
            return owner.network(target).capacity();
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            checkIndex(index);
            FluidResource current = owner.detectNetworkResource();
            return !resource.isEmpty() && (current.isEmpty() || Objects.equals(current, resource));
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            checkIndex(index);
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0) {
                return 0;
            }

            FluidResource current = owner.detectNetworkResource();
            TankNetwork network = current.isEmpty() ? owner.emptyNetwork() : owner.network(resource);
            if (network.tanks().isEmpty()) {
                return 0;
            }

            int inserted = Math.min(amount, Math.max(0, network.capacity() - network.amount()));
            if (inserted <= 0) {
                return 0;
            }

            snapshotTarget = current.isEmpty() ? FluidResource.EMPTY : resource;
            updateSnapshots(transaction);
            storeOnController(network.tanks(), resource, network.amount() + inserted);
            snapshotTarget = null;
            return inserted;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            checkIndex(index);
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || owner.fluid.isEmpty() && owner.detectNetworkResource().isEmpty()) {
                return 0;
            }

            FluidResource current = owner.detectNetworkResource();
            if (current.isEmpty() || !Objects.equals(current, resource)) {
                return 0;
            }

            TankNetwork network = owner.network(current);
            int extracted = Math.min(amount, network.amount());
            if (extracted <= 0) {
                return 0;
            }

            snapshotTarget = current;
            updateSnapshots(transaction);
            storeOnController(network.tanks(), current, network.amount() - extracted);
            snapshotTarget = null;
            return extracted;
        }

        @Override
        protected TankSnapshot createSnapshot() {
            FluidResource target = snapshotTarget == null ? owner.detectNetworkResource() : snapshotTarget;
            Map<BlockPos, FluidStack> fluids = new HashMap<>();
            Map<BlockPos, FluidStack> networkFluids = new HashMap<>();
            if (target.isEmpty()) {
                for (FluidTankBlockEntity tank : owner.emptyNetwork().tanks()) {
                    fluids.put(tank.worldPosition, tank.fluid.copy());
                    networkFluids.put(tank.worldPosition, tank.networkFluid.copy());
                }
            } else {
                for (FluidTankBlockEntity tank : owner.network(target).tanks()) {
                    fluids.put(tank.worldPosition, tank.fluid.copy());
                    networkFluids.put(tank.worldPosition, tank.networkFluid.copy());
                }
            }
            return new TankSnapshot(fluids, networkFluids);
        }

        @Override
        protected void revertToSnapshot(TankSnapshot snapshot) {
            Level level = owner.level;
            if (level == null) {
                return;
            }

            for (Map.Entry<BlockPos, FluidStack> entry : snapshot.fluids().entrySet()) {
                if (level.getBlockEntity(entry.getKey()) instanceof FluidTankBlockEntity tank) {
                    tank.fluid = entry.getValue().copy();
                    tank.networkFluid = snapshot.networkFluids().getOrDefault(entry.getKey(), FluidStack.EMPTY).copy();
                    tank.syncChanged();
                }
            }
        }

        @Override
        protected void onRootCommit(TankSnapshot originalState) {
            Level level = owner.level;
            if (level == null) {
                return;
            }

            for (BlockPos pos : originalState.fluids().keySet()) {
                if (level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) {
                    tank.syncChanged();
                }
            }
        }

        private static void checkIndex(int index) {
            if (index != 0) {
                throw new IndexOutOfBoundsException("Fluid tank index " + index + " is outside the single tank network slot");
            }
        }
    }
}
