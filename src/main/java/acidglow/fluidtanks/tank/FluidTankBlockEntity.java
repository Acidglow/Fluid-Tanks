package acidglow.fluidtanks.tank;

import acidglow.fluidtanks.AcidglowsFluidTanks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private FluidStack fluid = FluidStack.EMPTY;
    private FluidStack removalDropFluid = FluidStack.EMPTY;
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
        return resource.toStack(network(resource).amount());
    }

    public float networkFillRatio() {
        FluidResource resource = detectNetworkResource();
        if (resource.isEmpty()) {
            return 0.0F;
        }
        TankNetwork network = network(resource);
        return network.capacity() <= 0 ? 0.0F : (float) network.amount() / (float) network.capacity();
    }

    public void consolidateNetwork() {
        FluidResource resource = detectNetworkResource();
        if (!resource.isEmpty()) {
            TankNetwork network = network(resource);
            storeOnController(network.tanks(), resource, network.amount());
        }
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
            return;
        }

        TankNetwork network = network(resource);
        if (network.tanks().isEmpty() || network.capacity() <= 0 || network.amount() <= 0) {
            return;
        }

        int removedCapacity = capacity();
        int removedAmount = Math.min(removedCapacity, (int) ((long) network.amount() * removedCapacity / network.capacity()));
        int remainingAmount = network.amount() - removedAmount;
        int remainingCapacity = network.capacity() - removedCapacity;

        removalDropFluid = removedAmount > 0 ? resource.toStack(removedAmount) : FluidStack.EMPTY;

        List<FluidTankBlockEntity> remainingTanks = network.tanks().stream()
                .filter(tank -> tank != this)
                .toList();
        for (FluidTankBlockEntity tank : network.tanks()) {
            tank.setFluidDirect(FluidStack.EMPTY);
        }

        if (remainingAmount <= 0 || remainingCapacity <= 0 || remainingTanks.isEmpty()) {
            return;
        }

        List<TankNetwork> groups = remainingGroups(remainingTanks);
        int assigned = 0;
        for (int i = 0; i < groups.size(); i++) {
            TankNetwork group = groups.get(i);
            int groupAmount = i == groups.size() - 1
                    ? remainingAmount - assigned
                    : (int) ((long) remainingAmount * group.capacity() / remainingCapacity);
            groupAmount = Math.min(group.capacity(), Math.max(0, groupAmount));
            assigned += groupAmount;
            storeOnController(group.tanks(), resource, groupAmount);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fluid = input.read("fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("fluid", FluidStack.OPTIONAL_CODEC, fluid);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    private FluidResource detectNetworkResource() {
        if (!fluid.isEmpty()) {
            return FluidResource.of(fluid);
        }

        Set<FluidResource> resources = new HashSet<>();
        if (level == null) {
            return FluidResource.EMPTY;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(worldPosition);
        seen.add(worldPosition);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            BlockEntity blockEntity = level.getBlockEntity(current);
            if (!(blockEntity instanceof FluidTankBlockEntity tank)) {
                continue;
            }

            if (!tank.fluid.isEmpty()) {
                resources.add(FluidResource.of(tank.fluid));
                if (resources.size() > 1) {
                    return FluidResource.EMPTY;
                }
            }

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }

        return resources.size() == 1 ? resources.iterator().next() : FluidResource.EMPTY;
    }

    private TankNetwork network(FluidResource target) {
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
            if (!(blockEntity instanceof FluidTankBlockEntity tank) || !tank.isCompatibleWith(target)) {
                continue;
            }

            tanks.add(tank);
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
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
        if (level == null || !fluid.isEmpty()) {
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
            if (!(blockEntity instanceof FluidTankBlockEntity tank) || !tank.fluid.isEmpty()) {
                continue;
            }

            tanks.add(tank);
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }

        tanks.sort(Comparator.comparing(FluidTankBlockEntity::getBlockPos));
        int capacity = tanks.stream().mapToInt(FluidTankBlockEntity::capacity).sum();
        return new TankNetwork(tanks, 0, capacity);
    }

    private boolean isCompatibleWith(FluidResource target) {
        return fluid.isEmpty() || target.matches(fluid);
    }

    private static void storeOnController(List<FluidTankBlockEntity> tanks, FluidResource resource, int amount) {
        if (tanks.isEmpty()) {
            return;
        }

        int storedAmount = Math.max(0, Math.min(amount, tanks.stream().mapToInt(FluidTankBlockEntity::capacity).sum()));
        FluidTankBlockEntity controller = tanks.stream().min(Comparator.comparing(FluidTankBlockEntity::getBlockPos)).orElseThrow();
        for (FluidTankBlockEntity tank : tanks) {
            FluidStack next = tank == controller && storedAmount > 0 ? resource.toStack(storedAmount) : FluidStack.EMPTY;
            tank.setFluidDirect(next);
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
                for (Direction direction : Direction.values()) {
                    BlockPos next = current.relative(direction);
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

    private void setFluidDirect(FluidStack next) {
        if (!FluidStack.matches(fluid, next)) {
            fluid = next.copy();
            syncChanged();
        }
    }

    private void syncChanged() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private record TankNetwork(List<FluidTankBlockEntity> tanks, int amount, int capacity) {
        private static final TankNetwork EMPTY = new TankNetwork(List.of(), 0, 0);
    }

    private record TankSnapshot(Map<BlockPos, FluidStack> fluids) {
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
            return owner.network(target).capacity();
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            checkIndex(index);
            return !resource.isEmpty() && (owner.fluid.isEmpty() || resource.matches(owner.fluid));
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            checkIndex(index);
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0) {
                return 0;
            }

            TankNetwork network = owner.network(resource);
            if (network.tanks().isEmpty()) {
                return 0;
            }

            int inserted = Math.min(amount, Math.max(0, network.capacity() - network.amount()));
            if (inserted <= 0) {
                return 0;
            }

            snapshotTarget = resource;
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
            if (target.isEmpty()) {
                fluids.put(owner.worldPosition, owner.fluid.copy());
            } else {
                for (FluidTankBlockEntity tank : owner.network(target).tanks()) {
                    fluids.put(tank.worldPosition, tank.fluid.copy());
                }
            }
            return new TankSnapshot(fluids);
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
