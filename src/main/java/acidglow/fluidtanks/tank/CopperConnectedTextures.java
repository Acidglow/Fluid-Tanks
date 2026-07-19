package acidglow.fluidtanks.tank;

import acidglow.fluidtanks.AcidglowsFluidTanks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class CopperConnectedTextures {
    public static final Identifier COPPER_SIDE_SOLO = texture("copper_side_solo");
    public static final Identifier COPPER_TOP_BOTTOM_SOLO = texture("copper_top_bottom_solo");
    public static final Identifier IRON_SIDE_SOLO = texture("iron_solo");
    public static final Identifier IRON_TOP_BOTTOM_SOLO = texture("iron_top_bottom_solo");
    public static final Identifier GOLD_SIDE_SOLO = texture("gold_solo");
    public static final Identifier GOLD_TOP_BOTTOM_SOLO = texture("gold_top_bottom_solo");
    public static final Identifier DIAMOND_SIDE_SOLO = texture("diamond_solo");
    public static final Identifier DIAMOND_TOP_BOTTOM_SOLO = texture("diamond_top_bottom_solo");

    public static final int U = 1;
    public static final int D = 1 << 1;
    public static final int L = 1 << 2;
    public static final int R = 1 << 3;

    public static final int MISSING_UL = 1;
    public static final int MISSING_UR = 1 << 1;
    public static final int MISSING_DL = 1 << 2;
    public static final int MISSING_DR = 1 << 3;

    private static final List<TextureState> TEXTURE_STATES = createTextureStates(FluidTankTier.COPPER);
    private static final Map<TextureKey, Identifier> SIDE_TEXTURES = createLookup(FluidTankTier.COPPER, TEXTURE_STATES);
    private static final Set<Identifier> REGISTERED_SIDE_TEXTURES = createRegisteredTextures(FluidTankTier.COPPER, TEXTURE_STATES);
    private static final Map<FluidTankTier, TierTextures> TIER_TEXTURES = Map.of(
            FluidTankTier.COPPER, createTierTextures(FluidTankTier.COPPER),
            FluidTankTier.IRON, createTierTextures(FluidTankTier.IRON),
            FluidTankTier.GOLD, createTierTextures(FluidTankTier.GOLD),
            FluidTankTier.DIAMOND, createTierTextures(FluidTankTier.DIAMOND)
    );

    private CopperConnectedTextures() {
    }

    public static Identifier selectCopperSideTexture(Connections connections) {
        return selectSideTexture(FluidTankTier.COPPER, connections);
    }

    public static Identifier selectSideTexture(FluidTankTier tier, Connections connections) {
        TierTextures textures = tierTextures(tier);
        Connections normalized = connections.normalized();
        Identifier texture = textures.sideTextures().get(new TextureKey(normalized.cardinalMask(), normalized.missingCornerMask()));
        if (texture == null) {
            throw new IllegalArgumentException("No " + tier + " connected texture for cardinal mask "
                    + normalized.cardinalMask() + " and missing-corner mask " + normalized.missingCornerMask());
        }
        return texture;
    }

    public static Connections readConnections(BlockGetter world, BlockPos position, Direction face) {
        BlockState currentState = world.getBlockState(position);
        if (!(currentState.getBlock() instanceof FluidTankBlock tankBlock)) {
            return new Connections(false, false, false, false, false, false, false, false);
        }
        return readConnections(world, position, face, tankBlock.tier());
    }

    public static Connections readConnections(BlockGetter world, BlockPos position, Direction face, FluidTankTier tier) {
        FaceOffsets offsets = getFaceLocalOffsets(face);
        BlockState currentState = world.getBlockState(position);

        boolean u = canConnect(tier, currentState, world.getBlockState(position.relative(Direction.UP)), position, position.relative(Direction.UP));
        boolean d = canConnect(tier, currentState, world.getBlockState(position.relative(Direction.DOWN)), position, position.relative(Direction.DOWN));
        boolean l = canConnect(tier, currentState, world.getBlockState(position.relative(offsets.left())), position, position.relative(offsets.left()));
        boolean r = canConnect(tier, currentState, world.getBlockState(position.relative(offsets.right())), position, position.relative(offsets.right()));

        boolean ul = canConnect(tier, currentState, world.getBlockState(position.relative(Direction.UP).relative(offsets.left())), position, position.relative(Direction.UP).relative(offsets.left()));
        boolean ur = canConnect(tier, currentState, world.getBlockState(position.relative(Direction.UP).relative(offsets.right())), position, position.relative(Direction.UP).relative(offsets.right()));
        boolean dl = canConnect(tier, currentState, world.getBlockState(position.relative(Direction.DOWN).relative(offsets.left())), position, position.relative(Direction.DOWN).relative(offsets.left()));
        boolean dr = canConnect(tier, currentState, world.getBlockState(position.relative(Direction.DOWN).relative(offsets.right())), position, position.relative(Direction.DOWN).relative(offsets.right()));

        return fromRawConnections(u, d, l, r, ul, ur, dl, dr);
    }

    public static boolean canConnect(BlockState currentState, BlockState neighborState, BlockPos currentPos, BlockPos neighborPos) {
        if (!(currentState.getBlock() instanceof FluidTankBlock currentTank)) {
            return false;
        }
        return canConnect(currentTank.tier(), currentState, neighborState, currentPos, neighborPos);
    }

    public static boolean canConnect(FluidTankTier tier, BlockState currentState, BlockState neighborState, BlockPos currentPos, BlockPos neighborPos) {
        if (!(currentState.getBlock() instanceof FluidTankBlock currentTank)
                || !(neighborState.getBlock() instanceof FluidTankBlock neighborTank)) {
            return false;
        }
        return currentTank.tier() == tier && neighborTank.tier() == tier && supportsTier(tier);
    }

    public static FaceOffsets getFaceLocalOffsets(Direction face) {
        return switch (face) {
            case NORTH -> new FaceOffsets(Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST);
            case SOUTH -> new FaceOffsets(Direction.UP, Direction.DOWN, Direction.WEST, Direction.EAST);
            case EAST -> new FaceOffsets(Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH);
            case WEST -> new FaceOffsets(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH);
            default -> throw new IllegalArgumentException("Connected copper side textures only support vertical faces: " + face);
        };
    }

    public static Connections fromRawConnections(boolean u, boolean d, boolean l, boolean r, boolean ul, boolean ur, boolean dl, boolean dr) {
        return new Connections(
                u,
                d,
                l,
                r,
                u && l && !ul,
                u && r && !ur,
                d && l && !dl,
                d && r && !dr
        ).normalized();
    }

    public static List<TextureState> textureStates() {
        return TEXTURE_STATES;
    }

    public static List<TextureState> textureStates(FluidTankTier tier) {
        return tierTextures(tier).textureStates();
    }

    public static Set<Identifier> registeredSideTextures() {
        return REGISTERED_SIDE_TEXTURES;
    }

    public static Set<Identifier> registeredSideTextures(FluidTankTier tier) {
        return tierTextures(tier).registeredSideTextures();
    }

    public static Identifier sideSoloTexture(FluidTankTier tier) {
        return tierTextures(tier).sideSoloTexture();
    }

    public static Identifier topBottomTexture(FluidTankTier tier) {
        return tierTextures(tier).topBottomTexture();
    }

    public static boolean supportsTier(FluidTankTier tier) {
        return TIER_TEXTURES.containsKey(tier);
    }

    private static List<TextureState> createTextureStates(FluidTankTier tier) {
        List<TextureState> states = new ArrayList<>();
        String prefix = texturePrefix(tier);

        states.add(state(0, 0, sideSoloName(tier)));

        states.add(state(D, 0, prefix + "_01"));
        states.add(state(U, 0, prefix + "_02"));
        states.add(state(R, 0, prefix + "_04"));
        states.add(state(L, 0, prefix + "_05"));

        states.add(state(U | D, 0, prefix + "_03"));
        states.add(state(L | R, 0, prefix + "_06"));

        states.add(state(U | R, 0, prefix + "_07"));
        states.add(state(U | R, MISSING_UR, prefix + "_31"));
        states.add(state(U | L, 0, prefix + "_08"));
        states.add(state(U | L, MISSING_UL, prefix + "_29"));
        states.add(state(D | L, 0, prefix + "_09"));
        states.add(state(D | L, MISSING_DL, prefix + "_30"));
        states.add(state(D | R, 0, prefix + "_10"));
        states.add(state(D | R, MISSING_DR, prefix + "_32"));

        states.add(state(U | L | R, 0, prefix + "_11"));
        states.add(state(U | L | R, MISSING_UR, prefix + "_25"));
        states.add(state(U | L | R, MISSING_UL, prefix + "_26"));
        states.add(state(U | L | R, MISSING_UL | MISSING_UR, prefix + "_40"));

        states.add(state(U | D | L, 0, prefix + "_12"));
        states.add(state(U | D | L, MISSING_DL, prefix + "_27"));
        states.add(state(U | D | L, MISSING_UL, prefix + "_28"));
        states.add(state(U | D | L, MISSING_UL | MISSING_DL, prefix + "_39"));

        states.add(state(D | L | R, 0, prefix + "_13"));
        states.add(state(D | L | R, MISSING_DR, prefix + "_21"));
        states.add(state(D | L | R, MISSING_DL, prefix + "_22"));
        states.add(state(D | L | R, MISSING_DL | MISSING_DR, prefix + "_37"));

        states.add(state(U | D | R, 0, prefix + "_14"));
        states.add(state(U | D | R, MISSING_DR, prefix + "_23"));
        states.add(state(U | D | R, MISSING_UR, prefix + "_24"));
        states.add(state(U | D | R, MISSING_UR | MISSING_DR, prefix + "_38"));

        states.add(state(U | D | L | R, 0, prefix + "_15"));
        states.add(state(U | D | L | R, MISSING_DR, prefix + "_41"));
        states.add(state(U | D | L | R, MISSING_UL, prefix + "_42"));
        states.add(state(U | D | L | R, MISSING_DL, prefix + "_43"));
        states.add(state(U | D | L | R, MISSING_UR, prefix + "_44"));
        states.add(state(U | D | L | R, MISSING_DL | MISSING_DR, prefix + "_16"));
        states.add(state(U | D | L | R, MISSING_UR | MISSING_DR, prefix + "_17"));
        states.add(state(U | D | L | R, MISSING_UL | MISSING_UR, prefix + "_18"));
        states.add(state(U | D | L | R, MISSING_UL | MISSING_DL, prefix + "_19"));
        states.add(state(U | D | L | R, MISSING_UR | MISSING_DL, prefix + "_45"));
        states.add(state(U | D | L | R, MISSING_UL | MISSING_DR, prefix + "_46"));
        states.add(state(U | D | L | R, MISSING_UL | MISSING_DL | MISSING_DR, prefix + "_33"));
        states.add(state(U | D | L | R, MISSING_UR | MISSING_DL | MISSING_DR, prefix + "_34"));
        states.add(state(U | D | L | R, MISSING_UL | MISSING_UR | MISSING_DL, prefix + "_35"));
        states.add(state(U | D | L | R, MISSING_UL | MISSING_UR | MISSING_DR, prefix + "_36"));
        states.add(state(U | D | L | R, MISSING_UL | MISSING_UR | MISSING_DL | MISSING_DR, prefix + "_20"));

        return List.copyOf(states);
    }

    private static TextureState state(int cardinalMask, int missingCornerMask, String textureName) {
        Connections connections = Connections.fromMasks(cardinalMask, missingCornerMask).normalized();
        return new TextureState(connections, textureName + ".png", texture(textureName));
    }

    private static Map<TextureKey, Identifier> createLookup(FluidTankTier tier, List<TextureState> states) {
        Map<TextureKey, Identifier> lookup = new LinkedHashMap<>();
        for (TextureState state : states) {
            TextureKey key = new TextureKey(state.connections().cardinalMask(), state.connections().missingCornerMask());
            Identifier previous = lookup.put(key, state.texture());
            if (previous != null) {
                throw new IllegalStateException("Duplicate " + tier + " connected texture state for " + key);
            }
        }
        if (lookup.size() != 47) {
            throw new IllegalStateException("Expected 47 " + tier + " connected texture states, got " + lookup.size());
        }
        return Collections.unmodifiableMap(lookup);
    }

    private static Set<Identifier> createRegisteredTextures(FluidTankTier tier, List<TextureState> states) {
        Set<Identifier> textures = new LinkedHashSet<>();
        for (TextureState state : states) {
            textures.add(state.texture());
        }
        if (textures.size() != 47) {
            throw new IllegalStateException("Expected 47 " + tier + " connected textures, got " + textures.size());
        }
        return Collections.unmodifiableSet(textures);
    }

    private static TierTextures createTierTextures(FluidTankTier tier) {
        List<TextureState> states = createTextureStates(tier);
        return new TierTextures(
                texture(sideSoloName(tier)),
                texture(texturePrefix(tier) + "_top_bottom_solo"),
                states,
                createLookup(tier, states),
                createRegisteredTextures(tier, states)
        );
    }

    private static TierTextures tierTextures(FluidTankTier tier) {
        TierTextures textures = TIER_TEXTURES.get(tier);
        if (textures == null) {
            throw new IllegalArgumentException("Tank tier " + tier + " does not have connected textures");
        }
        return textures;
    }

    private static String sideSoloName(FluidTankTier tier) {
        return tier == FluidTankTier.COPPER ? "copper_side_solo" : texturePrefix(tier) + "_solo";
    }

    private static String texturePrefix(FluidTankTier tier) {
        return switch (tier) {
            case COPPER -> "copper";
            case IRON -> "iron";
            case GOLD -> "gold";
            case DIAMOND -> "diamond";
            default -> throw new IllegalArgumentException("Tank tier " + tier + " does not have connected textures");
        };
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(AcidglowsFluidTanks.MODID, "textures/block/" + name + ".png");
    }

    private record TextureKey(int cardinalMask, int missingCornerMask) {
    }

    public record FaceOffsets(Direction up, Direction down, Direction left, Direction right) {
    }

    public record Connections(
            boolean u,
            boolean d,
            boolean l,
            boolean r,
            boolean missingUL,
            boolean missingUR,
            boolean missingDL,
            boolean missingDR
    ) {
        public Connections normalized() {
            int cardinalMask = cardinalMask();
            int missingCornerMask = missingCornerMask();
            if ((cardinalMask & (U | L)) != (U | L)) {
                missingCornerMask &= ~MISSING_UL;
            }
            if ((cardinalMask & (U | R)) != (U | R)) {
                missingCornerMask &= ~MISSING_UR;
            }
            if ((cardinalMask & (D | L)) != (D | L)) {
                missingCornerMask &= ~MISSING_DL;
            }
            if ((cardinalMask & (D | R)) != (D | R)) {
                missingCornerMask &= ~MISSING_DR;
            }
            return fromMasks(cardinalMask, missingCornerMask);
        }

        public int cardinalMask() {
            int mask = 0;
            if (u) {
                mask |= U;
            }
            if (d) {
                mask |= D;
            }
            if (l) {
                mask |= L;
            }
            if (r) {
                mask |= R;
            }
            return mask;
        }

        public int missingCornerMask() {
            int mask = 0;
            if (missingUL) {
                mask |= MISSING_UL;
            }
            if (missingUR) {
                mask |= MISSING_UR;
            }
            if (missingDL) {
                mask |= MISSING_DL;
            }
            if (missingDR) {
                mask |= MISSING_DR;
            }
            return mask;
        }

        public static Connections fromMasks(int cardinalMask, int missingCornerMask) {
            return new Connections(
                    (cardinalMask & U) != 0,
                    (cardinalMask & D) != 0,
                    (cardinalMask & L) != 0,
                    (cardinalMask & R) != 0,
                    (missingCornerMask & MISSING_UL) != 0,
                    (missingCornerMask & MISSING_UR) != 0,
                    (missingCornerMask & MISSING_DL) != 0,
                    (missingCornerMask & MISSING_DR) != 0
            );
        }
    }

    public record TextureState(Connections connections, String fileName, Identifier texture) {
    }

    private record TierTextures(
            Identifier sideSoloTexture,
            Identifier topBottomTexture,
            List<TextureState> textureStates,
            Map<TextureKey, Identifier> sideTextures,
            Set<Identifier> registeredSideTextures
    ) {
    }
}
