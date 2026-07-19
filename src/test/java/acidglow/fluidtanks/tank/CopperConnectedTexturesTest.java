package acidglow.fluidtanks.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CopperConnectedTexturesTest {
    @ParameterizedTest
    @MethodSource("textureStates")
    void selectsEveryExplicitTextureState(CopperConnectedTextures.Connections connections, String expectedFileName) {
        assertEquals(expectedIdentifier(expectedFileName), CopperConnectedTextures.selectCopperSideTexture(connections));
    }

    @ParameterizedTest
    @MethodSource("ironTextureStates")
    void selectsEveryExplicitIronTextureState(CopperConnectedTextures.Connections connections, String expectedFileName) {
        assertEquals(expectedIdentifier(expectedFileName), CopperConnectedTextures.selectSideTexture(FluidTankTier.IRON, connections));
    }

    @ParameterizedTest
    @MethodSource("goldTextureStates")
    void selectsEveryExplicitGoldTextureState(CopperConnectedTextures.Connections connections, String expectedFileName) {
        assertEquals(expectedIdentifier(expectedFileName), CopperConnectedTextures.selectSideTexture(FluidTankTier.GOLD, connections));
    }

    @ParameterizedTest
    @MethodSource("diamondTextureStates")
    void selectsEveryExplicitDiamondTextureState(CopperConnectedTextures.Connections connections, String expectedFileName) {
        assertEquals(expectedIdentifier(expectedFileName), CopperConnectedTextures.selectSideTexture(FluidTankTier.DIAMOND, connections));
    }

    @Test
    void allRawConnectionCombinationsNormalizeToRegisteredTexturesDeterministically() {
        Set<Identifier> reachable = new HashSet<>();

        for (int rawMask = 0; rawMask < 256; rawMask++) {
            CopperConnectedTextures.Connections connections = rawConnections(rawMask);
            Identifier first = CopperConnectedTextures.selectCopperSideTexture(connections);
            Identifier second = CopperConnectedTextures.selectCopperSideTexture(connections);

            assertEquals(first, second);
            assertTrue(CopperConnectedTextures.registeredSideTextures().contains(first));
            reachable.add(first);
        }

        assertEquals(CopperConnectedTextures.registeredSideTextures(), reachable);
    }

    @Test
    void allRawIronConnectionCombinationsNormalizeToRegisteredTexturesDeterministically() {
        Set<Identifier> reachable = new HashSet<>();

        for (int rawMask = 0; rawMask < 256; rawMask++) {
            CopperConnectedTextures.Connections connections = rawConnections(rawMask);
            Identifier first = CopperConnectedTextures.selectSideTexture(FluidTankTier.IRON, connections);
            Identifier second = CopperConnectedTextures.selectSideTexture(FluidTankTier.IRON, connections);

            assertEquals(first, second);
            assertTrue(CopperConnectedTextures.registeredSideTextures(FluidTankTier.IRON).contains(first));
            reachable.add(first);
        }

        assertEquals(CopperConnectedTextures.registeredSideTextures(FluidTankTier.IRON), reachable);
    }

    @Test
    void allRawGoldConnectionCombinationsNormalizeToRegisteredTexturesDeterministically() {
        Set<Identifier> reachable = new HashSet<>();

        for (int rawMask = 0; rawMask < 256; rawMask++) {
            CopperConnectedTextures.Connections connections = rawConnections(rawMask);
            Identifier first = CopperConnectedTextures.selectSideTexture(FluidTankTier.GOLD, connections);
            Identifier second = CopperConnectedTextures.selectSideTexture(FluidTankTier.GOLD, connections);

            assertEquals(first, second);
            assertTrue(CopperConnectedTextures.registeredSideTextures(FluidTankTier.GOLD).contains(first));
            reachable.add(first);
        }

        assertEquals(CopperConnectedTextures.registeredSideTextures(FluidTankTier.GOLD), reachable);
    }

    @Test
    void allRawDiamondConnectionCombinationsNormalizeToRegisteredTexturesDeterministically() {
        Set<Identifier> reachable = new HashSet<>();

        for (int rawMask = 0; rawMask < 256; rawMask++) {
            CopperConnectedTextures.Connections connections = rawConnections(rawMask);
            Identifier first = CopperConnectedTextures.selectSideTexture(FluidTankTier.DIAMOND, connections);
            Identifier second = CopperConnectedTextures.selectSideTexture(FluidTankTier.DIAMOND, connections);

            assertEquals(first, second);
            assertTrue(CopperConnectedTextures.registeredSideTextures(FluidTankTier.DIAMOND).contains(first));
            reachable.add(first);
        }

        assertEquals(CopperConnectedTextures.registeredSideTextures(FluidTankTier.DIAMOND), reachable);
    }

    @Test
    void irrelevantDiagonalChangesDoNotChangeSelectedTexture() {
        for (int rawMask = 0; rawMask < 256; rawMask++) {
            Identifier original = CopperConnectedTextures.selectCopperSideTexture(rawConnections(rawMask));

            for (int diagonalBit = 4; diagonalBit < 8; diagonalBit++) {
                if (isRelevant(rawMask, diagonalBit)) {
                    continue;
                }
                Identifier changed = CopperConnectedTextures.selectCopperSideTexture(rawConnections(rawMask ^ (1 << diagonalBit)));
                assertEquals(original, changed);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("requiredExamples")
    void selectsRequiredExamples(CopperConnectedTextures.Connections connections, String expectedFileName) {
        assertEquals(expectedIdentifier(expectedFileName), CopperConnectedTextures.selectCopperSideTexture(connections));
    }

    @Test
    void verticalFaceOffsetsPreserveVisualLeftAndRight() {
        assertOffsets(Direction.NORTH, Direction.EAST, Direction.WEST);
        assertOffsets(Direction.SOUTH, Direction.WEST, Direction.EAST);
        assertOffsets(Direction.EAST, Direction.SOUTH, Direction.NORTH);
        assertOffsets(Direction.WEST, Direction.NORTH, Direction.SOUTH);
    }

    @Test
    void topAndBottomUseDedicatedNonConnectedTexture() {
        assertEquals(expectedIdentifier("copper_top_bottom_solo.png"), CopperConnectedTextures.COPPER_TOP_BOTTOM_SOLO);
        assertEquals(expectedIdentifier("iron_top_bottom_solo.png"), CopperConnectedTextures.IRON_TOP_BOTTOM_SOLO);
        assertEquals(expectedIdentifier("gold_top_bottom_solo.png"), CopperConnectedTextures.GOLD_TOP_BOTTOM_SOLO);
        assertEquals(expectedIdentifier("diamond_top_bottom_solo.png"), CopperConnectedTextures.DIAMOND_TOP_BOTTOM_SOLO);
    }

    private static Stream<Arguments> textureStates() {
        return CopperConnectedTextures.textureStates().stream()
                .map(state -> Arguments.of(state.connections(), state.fileName()));
    }

    private static Stream<Arguments> ironTextureStates() {
        return CopperConnectedTextures.textureStates(FluidTankTier.IRON).stream()
                .map(state -> Arguments.of(state.connections(), state.fileName()));
    }

    private static Stream<Arguments> goldTextureStates() {
        return CopperConnectedTextures.textureStates(FluidTankTier.GOLD).stream()
                .map(state -> Arguments.of(state.connections(), state.fileName()));
    }

    private static Stream<Arguments> diamondTextureStates() {
        return CopperConnectedTextures.textureStates(FluidTankTier.DIAMOND).stream()
                .map(state -> Arguments.of(state.connections(), state.fileName()));
    }

    private static Stream<Arguments> requiredExamples() {
        return Stream.of(
                Arguments.of(connections(0, 0), "copper_side_solo.png"),
                Arguments.of(connections(CopperConnectedTextures.D, 0), "copper_01.png"),
                Arguments.of(raw(true, false, false, true, false, true, false, false), "copper_07.png"),
                Arguments.of(raw(true, false, false, true, false, false, false, false), "copper_31.png"),
                Arguments.of(connections(
                        CopperConnectedTextures.D | CopperConnectedTextures.L | CopperConnectedTextures.R,
                        CopperConnectedTextures.MISSING_DL | CopperConnectedTextures.MISSING_DR
                ), "copper_37.png"),
                Arguments.of(connections(
                        CopperConnectedTextures.U | CopperConnectedTextures.D | CopperConnectedTextures.R,
                        CopperConnectedTextures.MISSING_UR | CopperConnectedTextures.MISSING_DR
                ), "copper_38.png"),
                Arguments.of(connections(
                        CopperConnectedTextures.U | CopperConnectedTextures.D | CopperConnectedTextures.L,
                        CopperConnectedTextures.MISSING_UL | CopperConnectedTextures.MISSING_DL
                ), "copper_39.png"),
                Arguments.of(connections(
                        CopperConnectedTextures.U | CopperConnectedTextures.L | CopperConnectedTextures.R,
                        CopperConnectedTextures.MISSING_UL | CopperConnectedTextures.MISSING_UR
                ), "copper_40.png"),
                Arguments.of(connections(
                        CopperConnectedTextures.U | CopperConnectedTextures.D | CopperConnectedTextures.L | CopperConnectedTextures.R,
                        CopperConnectedTextures.MISSING_DR
                ), "copper_41.png"),
                Arguments.of(connections(
                        CopperConnectedTextures.U | CopperConnectedTextures.D | CopperConnectedTextures.L | CopperConnectedTextures.R,
                        CopperConnectedTextures.MISSING_UL
                ), "copper_42.png"),
                Arguments.of(connections(
                        CopperConnectedTextures.U | CopperConnectedTextures.D | CopperConnectedTextures.L | CopperConnectedTextures.R,
                        CopperConnectedTextures.MISSING_DL
                ), "copper_43.png"),
                Arguments.of(connections(
                        CopperConnectedTextures.U | CopperConnectedTextures.D | CopperConnectedTextures.L | CopperConnectedTextures.R,
                        CopperConnectedTextures.MISSING_UR
                ), "copper_44.png"),
                Arguments.of(connections(
                        CopperConnectedTextures.U | CopperConnectedTextures.D | CopperConnectedTextures.L | CopperConnectedTextures.R,
                        CopperConnectedTextures.MISSING_UR | CopperConnectedTextures.MISSING_DL
                ), "copper_45.png"),
                Arguments.of(connections(
                        CopperConnectedTextures.U | CopperConnectedTextures.D | CopperConnectedTextures.L | CopperConnectedTextures.R,
                        CopperConnectedTextures.MISSING_UL | CopperConnectedTextures.MISSING_DR
                ), "copper_46.png"),
                Arguments.of(connections(
                        CopperConnectedTextures.U | CopperConnectedTextures.D | CopperConnectedTextures.L | CopperConnectedTextures.R,
                        CopperConnectedTextures.MISSING_UL | CopperConnectedTextures.MISSING_UR | CopperConnectedTextures.MISSING_DL | CopperConnectedTextures.MISSING_DR
                ), "copper_20.png")
        );
    }

    private static CopperConnectedTextures.Connections rawConnections(int rawMask) {
        return raw(
                (rawMask & 1) != 0,
                (rawMask & 2) != 0,
                (rawMask & 4) != 0,
                (rawMask & 8) != 0,
                (rawMask & 16) != 0,
                (rawMask & 32) != 0,
                (rawMask & 64) != 0,
                (rawMask & 128) != 0
        );
    }

    private static CopperConnectedTextures.Connections raw(boolean u, boolean d, boolean l, boolean r, boolean ul, boolean ur, boolean dl, boolean dr) {
        return CopperConnectedTextures.fromRawConnections(u, d, l, r, ul, ur, dl, dr);
    }

    private static CopperConnectedTextures.Connections connections(int cardinalMask, int missingCornerMask) {
        return CopperConnectedTextures.Connections.fromMasks(cardinalMask, missingCornerMask).normalized();
    }

    private static boolean isRelevant(int rawMask, int diagonalBit) {
        boolean u = (rawMask & 1) != 0;
        boolean d = (rawMask & 2) != 0;
        boolean l = (rawMask & 4) != 0;
        boolean r = (rawMask & 8) != 0;
        return switch (diagonalBit) {
            case 4 -> u && l;
            case 5 -> u && r;
            case 6 -> d && l;
            case 7 -> d && r;
            default -> false;
        };
    }

    private static void assertOffsets(Direction face, Direction expectedLeft, Direction expectedRight) {
        CopperConnectedTextures.FaceOffsets offsets = CopperConnectedTextures.getFaceLocalOffsets(face);
        assertEquals(Direction.UP, offsets.up());
        assertEquals(Direction.DOWN, offsets.down());
        assertEquals(expectedLeft, offsets.left());
        assertEquals(expectedRight, offsets.right());
    }

    private static Identifier expectedIdentifier(String fileName) {
        String textureName = fileName.substring(0, fileName.length() - ".png".length());
        return Identifier.fromNamespaceAndPath("acidglowsfluidtanks", "textures/block/" + textureName + ".png");
    }
}
