package com.minersdream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = MinersDreamMod.MOD_ID)
public final class Blast {
    private static final int TORCH_SPACING = 6;

    private record Tunnel(TagKey<Block> carve, Block floor, boolean torches) {}

    private static final Map<ResourceKey<Level>, Tunnel> TUNNELS = Map.of(
            Level.OVERWORLD, new Tunnel(tag("overworld_tunnel"), Blocks.COBBLESTONE, true),
            Level.NETHER, new Tunnel(tag("nether_tunnel"), Blocks.BLACKSTONE, false),
            Level.END, new Tunnel(tag("end_tunnel"), Blocks.END_STONE, true));

    // sensors/shriekers/catalysts are block entities, still want them gone
    private static final TagKey<Block> SCULK = tag("sculk");

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MinersDreamMod.MOD_ID, name));
    }

    // d = forward, w = sideways, h = up
    record Frame(BlockPos start, Direction forward, Direction side, Direction up, int hMin) {
        private static final float SHAFT_PITCH = 60.0F;

        BlockPos at(int d, int w, int h) {
            return start.relative(forward, d).relative(side, w).relative(up, h);
        }

        static Frame toward(BlockPos origin, Direction forward, Direction facing, int height) {
            if (forward.getAxis() != Direction.Axis.Y) return new Frame(origin, forward, forward.getClockWise(), Direction.UP, 0);
            return new Frame(forward == Direction.DOWN ? origin.below() : origin, forward, facing.getClockWise(), facing, -height / 2);
        }

        static Frame of(Player player, int height) {
            float pitch = player.getXRot();
            Direction forward = pitch > SHAFT_PITCH ? Direction.DOWN : pitch < -SHAFT_PITCH ? Direction.UP : player.getDirection();
            return toward(player.blockPosition(), forward, player.getDirection(), height);
        }
    }

    // TODO cap this? dispenser on a clock + netherite dream stacks them up
    private static final List<Blast> ACTIVE = new ArrayList<>();

    private final ServerLevel level;
    private final Player player;
    private final BlockPos origin;
    private final Frame f;
    private final MinersDreamItem.Size size;
    private final Tunnel tunnel;
    private final boolean shaft;
    private final int hMax;
    private int next = -1; // -1 = end cap behind
    private int ticks;

    private Blast(ServerLevel level, Player player, BlockPos origin, Frame f, MinersDreamItem.Size size) {
        this.level = level;
        this.player = player;
        this.origin = origin;
        this.f = f;
        this.size = size;
        this.tunnel = TUNNELS.get(level.dimension());
        this.shaft = f.forward().getAxis() == Direction.Axis.Y;
        this.hMax = f.hMin() + size.height() - 1;
    }

    static void start(ServerLevel level, Player player, BlockPos origin, Frame f, MinersDreamItem.Size size) {
        ACTIVE.add(new Blast(level, player, origin, f, size));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ACTIVE.removeIf(Blast::step);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private boolean step() {
        int end = Math.min(next + MinersDreamMod.LENGTH_PER_TICK.get(), size.length() + 1);
        for (; next < end; next++) layer(next);

        Vec3 c = Vec3.atCenterOf(f.at(Math.min(next, size.length() - 1), 0, (f.hMin() + hMax) / 2));
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 4, 2.0, 1.5, 2.0, 0.0);
        if (ticks++ % 5 == 0) level.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.0F);

        if (next <= size.length()) return false;
        // water to land in
        if (f.forward() == Direction.DOWN) level.setBlock(f.at(size.length() - 1, 0, 0), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    private void layer(int d) {
        int hw = size.halfWidth();
        boolean inside = d >= 0 && d < size.length();
        for (int w = -hw - 1; w <= hw + 1; w++)
            for (int h = f.hMin() - 1; h <= hMax + 1; h++) {
                BlockPos pos = f.at(d, w, h);
                if (pos.equals(origin) || pos.equals(origin.above())) continue;
                boolean inWidth = Math.abs(w) <= hw, inHeight = h >= f.hMin() && h <= hMax;
                BlockState state = level.getBlockState(pos);
                if (inside && inWidth && inHeight) {
                    if (carves(state)) {
                        // UPDATE_CLIENTS so nearby water/gravel doesn't move before the walls are sealed
                        if (allowed(pos, state)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else if (wet(state) && allowed(pos, state)) {
                        plug(pos, state, Block.UPDATE_CLIENTS);
                    }
                    continue;
                }
                boolean floor = shaft
                        ? f.forward() == Direction.DOWN && d == size.length() && inWidth && inHeight
                        : inside && h == f.hMin() - 1 && inWidth;
                boolean leak = wet(state) || state.getBlock() instanceof FallingBlock;
                if ((leak || floor && state.isAir()) && allowed(pos, state)) plug(pos, state, Block.UPDATE_ALL);
            }
        if (inside) {
            decorate(d);
            if (d + 1 < size.length()) dam(d + 1);
        }
    }

    // seal fluids one layer ahead so nothing flows in between ticks
    private void dam(int d) {
        int hw = size.halfWidth();
        for (int w = -hw; w <= hw; w++)
            for (int h = f.hMin(); h <= hMax; h++) {
                BlockPos pos = f.at(d, w, h);
                BlockState state = level.getBlockState(pos);
                if (wet(state) && !pos.equals(origin) && !pos.equals(origin.above()) && allowed(pos, state)) plug(pos, state, Block.UPDATE_CLIENTS);
            }
    }

    private void plug(BlockPos pos, BlockState state, int flags) {
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), flags);
        } else {
            level.setBlock(pos, tunnel.floor().defaultBlockState(), flags);
        }
    }

    private static boolean wet(BlockState state) {
        return !state.getFluidState().isEmpty();
    }

    private void decorate(int d) {
        if (shaft) {
            BlockPos wall = f.at(d, 0, f.hMin() - 1);
            BlockState wallState = level.getBlockState(wall);
            if (wallState.canBeReplaced() && allowed(wall, wallState)) level.setBlock(wall, tunnel.floor().defaultBlockState(), Block.UPDATE_ALL);
            place(f.at(d, 0, f.hMin()), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.up()));
            return;
        }
        boolean rails = MinersDreamMod.RAILS.get();
        if (rails) place(f.at(d, 0, 0), Blocks.RAIL.defaultBlockState());
        if (tunnel.torches() && MinersDreamMod.TORCHES.get() && d > 0 && d % TORCH_SPACING == 0) {
            place(f.at(d, rails ? 2 : 0, 0), Blocks.TORCH.defaultBlockState());
        }
    }

    // never ores or block entities, even if a pack tags them as stone
    private boolean carves(BlockState state) {
        if (state.is(Tags.Blocks.ORES) || state.hasBlockEntity() && !state.is(SCULK)) return false;
        return state.is(tunnel.carve()) || wet(state) && !state.hasProperty(BlockStateProperties.WATERLOGGED);
    }

    private void place(BlockPos pos, BlockState state) {
        if (level.isEmptyBlock(pos) && state.canSurvive(level, pos)) level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    // BreakEvent so claim mods can stop it
    private boolean allowed(BlockPos pos, BlockState state) {
        return level.mayInteract(player, pos) && !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled();
    }
}
