package com.minersdream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(MinersDreamMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MinersDreamTests {
    private MinersDreamTests() {}

    @GameTest(template = "gametest_platform", timeoutTicks = 600)
    public static void blastAndUpgrade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();

        // upgrades
        ItemStack worn = new ItemStack(MinersDreamMod.MINERS_DREAM.get());
        worn.setDamageValue(1);
        upgrade(problems, level, worn, Items.IRON_BLOCK, MinersDreamMod.IRON_MINERS_DREAM.get(), 1);
        upgrade(problems, level, new ItemStack(MinersDreamMod.IRON_MINERS_DREAM.get()), Items.GOLD_BLOCK, MinersDreamMod.GOLD_MINERS_DREAM.get(), 0);
        upgrade(problems, level, new ItemStack(MinersDreamMod.IRON_MINERS_DREAM.get()), Items.DIAMOND_BLOCK, Items.AIR, 0); // no skipping gold
        upgrade(problems, level, new ItemStack(MinersDreamMod.GOLD_MINERS_DREAM.get()), Items.DIAMOND_BLOCK, MinersDreamMod.DIAMOND_MINERS_DREAM.get(), 0);
        upgrade(problems, level, new ItemStack(MinersDreamMod.DIAMOND_MINERS_DREAM.get()), Items.NETHERITE_BLOCK, MinersDreamMod.NETHERITE_MINERS_DREAM.get(), 0);

        // main tunnel, facing south
        BlockPos start = helper.absolutePos(new BlockPos(1, 20, 1));
        Blast.Frame at = new Blast.Frame(start, Direction.SOUTH, Direction.WEST, Direction.UP, 0);
        fill(level, at, -1, 202, 9, -2, 10);
        clear(level, at);
        level.setBlock(at.at(20, 3, 2), Blocks.DIAMOND_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(at.at(30, 0, -1), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(at.at(15, 6, 2), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(at.at(25, 2, 7), Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(at.at(12, -2, 4), Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(at.at(13, 2, 0), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(at.at(17, 1, 0), Blocks.SCULK_SHRIEKER.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(at.at(17, -1, 0), Blocks.SCULK.defaultBlockState(), Block.UPDATE_CLIENTS);
        // fluids
        BlockState wetStairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        level.setBlock(at.at(18, 3, 0), wetStairs, Block.UPDATE_CLIENTS);
        level.setBlock(at.at(19, -6, 2), wetStairs, Block.UPDATE_CLIENTS);
        level.setBlock(at.at(21, 0, 4), Blocks.KELP.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int d = 35; d <= 45; d++) level.setBlock(at.at(d, d % 3 - 1, 2), Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
        Player player = player(helper, start, 0.0F);
        if (!Blast.Frame.of(player, 7).equals(at)) problems.add("looking south gave " + Blast.Frame.of(player, 7));

        // wrong dimensions
        refuses(problems, player, MinersDreamMod.MINERS_DREAM.get(), level.getServer().getLevel(Level.NETHER));
        refuses(problems, player, MinersDreamMod.IRON_MINERS_DREAM.get(), level.getServer().getLevel(Level.NETHER));
        refuses(problems, player, MinersDreamMod.GOLD_MINERS_DREAM.get(), level.getServer().getLevel(Level.END));

        // shaft down
        BlockPos top = helper.absolutePos(new BlockPos(40, 80, 1));
        Blast.Frame down = new Blast.Frame(top.below(), Direction.DOWN, Direction.WEST, Direction.SOUTH, -3);
        fill(level, down, 0, 53, 7, -5, 5);
        Player diver = player(helper, top, 90.0F);
        if (!Blast.Frame.of(diver, 7).equals(down)) problems.add("looking down gave " + Blast.Frame.of(diver, 7));

        // shaft up
        BlockPos bottom = helper.absolutePos(new BlockPos(80, 20, 1));
        Blast.Frame up = new Blast.Frame(bottom, Direction.UP, Direction.WEST, Direction.SOUTH, -3);
        fill(level, up, 0, 53, 7, -5, 5);
        for (int d = 0; d < 2; d++) level.setBlock(up.at(d, 0, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(up.at(51, 1, 1), Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_CLIENTS);
        Player climber = player(helper, bottom, -90.0F);
        if (!Blast.Frame.of(climber, 7).equals(up)) problems.add("looking up gave " + Blast.Frame.of(climber, 7));

        // reach III: 51 + 75% = 89
        BlockPos far = helper.absolutePos(new BlockPos(160, 20, 1));
        Blast.Frame reach = new Blast.Frame(far, Direction.SOUTH, Direction.WEST, Direction.UP, 0);
        fill(level, reach, -1, 92, 7, -2, 8);
        clear(level, reach);
        Player reacher = player(helper, far, 0.0F);
        ItemStack reached = new ItemStack(MinersDreamMod.MINERS_DREAM.get());
        reached.enchant(MinersDreamMod.REACH.get(), 3);

        // dispenser
        BlockPos dispenser = helper.absolutePos(new BlockPos(120, 20, 1));
        Blast.Frame fired = Blast.Frame.toward(dispenser, Direction.SOUTH, Direction.NORTH, 7);
        fill(level, fired, 0, 53, 7, -2, 8);
        level.setBlock(dispenser.north(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        if (level.getBlockEntity(dispenser) instanceof DispenserBlockEntity box) box.setItem(0, new ItemStack(MinersDreamMod.MINERS_DREAM.get()));

        ItemStack base = new ItemStack(MinersDreamMod.MINERS_DREAM.get());
        ItemStack iron = new ItemStack(MinersDreamMod.IRON_MINERS_DREAM.get());
        ItemStack gold = new ItemStack(MinersDreamMod.GOLD_MINERS_DREAM.get());
        ItemStack diamond = new ItemStack(MinersDreamMod.DIAMOND_MINERS_DREAM.get());
        ItemStack netherite = new ItemStack(MinersDreamMod.NETHERITE_MINERS_DREAM.get());
        helper.startSequence()
                .thenExecute(() -> {
                    use(player, base, start);
                    use(diver, new ItemStack(MinersDreamMod.MINERS_DREAM.get()), top);
                    use(climber, new ItemStack(MinersDreamMod.MINERS_DREAM.get()), bottom);
                    use(reacher, reached, far);
                    level.setBlock(dispenser.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    if (base.getDamageValue() != 1 || base.getMaxDamage() != 4) problems.add("base uses " + base.getDamageValue() + "/" + base.getMaxDamage());
                    if (!player.getCooldowns().isOnCooldown(base.getItem())) problems.add("no cooldown");
                })
                .thenExecuteAfter(ticksFor(51), () -> {
                    expect(problems, level, at.at(10, 0, 3), Blocks.AIR, "middle of tunnel");
                    expect(problems, level, at.at(50, 5, 6), Blocks.AIR, "far top corner");
                    expect(problems, level, at.at(10, 6, 3), Blocks.STONE, "wall");
                    expect(problems, level, at.at(20, 3, 2), Blocks.DIAMOND_ORE, "ore");
                    expect(problems, level, at.at(30, 0, -1), Blocks.COBBLESTONE, "floor gap");
                    expect(problems, level, at.at(15, 6, 2), Blocks.COBBLESTONE, "water in wall");
                    expect(problems, level, at.at(25, 2, 7), Blocks.COBBLESTONE, "gravel over ceiling");
                    expect(problems, level, at.at(12, -2, 4), Blocks.AIR, "cobweb");
                    expect(problems, level, at.at(13, 2, 0), Blocks.AIR, "fence");
                    expect(problems, level, at.at(6, 0, 0), Blocks.TORCH, "torch");
                    expect(problems, level, at.at(17, 1, 0), Blocks.AIR, "sculk shrieker");
                    expect(problems, level, at.at(17, -1, 0), Blocks.AIR, "sculk");
                    expect(problems, level, at.at(21, 0, 4), Blocks.AIR, "kelp");
                    for (BlockPos stairs : List.of(at.at(18, 3, 0), at.at(19, -6, 2))) {
                        BlockState s = level.getBlockState(stairs);
                        if (!s.is(Blocks.OAK_STAIRS) || s.getValue(BlockStateProperties.WATERLOGGED)) problems.add("waterlogged stairs became " + s);
                    }
                    // no fluid left in the tunnel
                    for (int d = 0; d < 51; d++)
                        for (int w = -5; w <= 5; w++)
                            for (int h = 0; h < 7; h++) {
                                if (!level.getFluidState(at.at(d, w, h)).isEmpty()) problems.add("fluid in the tunnel at " + d + "," + w + "," + h);
                            }
                    reach(problems, level, at, 51, "base");

                    expect(problems, level, down.at(0, 0, 0), Blocks.AIR, "shaft top");
                    expect(problems, level, down.at(25, -5, 3), Blocks.AIR, "shaft middle corner");
                    expect(problems, level, down.at(25, 6, 0), Blocks.STONE, "shaft wall");
                    expect(problems, level, down.at(25, 0, -3), Blocks.LADDER, "shaft ladder");
                    expect(problems, level, down.at(50, 0, 0), Blocks.WATER, "pool");
                    expect(problems, level, down.at(51, 0, 0), Blocks.STONE, "shaft bottom");

                    expect(problems, level, up.at(50, 5, 3), Blocks.AIR, "shaft up top corner");
                    expect(problems, level, up.at(25, 0, -3), Blocks.LADDER, "shaft up ladder");
                    expect(problems, level, up.at(51, 0, 0), Blocks.STONE, "shaft up cap");
                    expect(problems, level, up.at(51, 1, 1), Blocks.COBBLESTONE, "gravel over shaft up");

                    use(player, iron, start);
                    worn(problems, iron, 16);
                })
                .thenExecuteAfter(ticksFor(75), () -> {
                    reach(problems, level, at, 75, "iron");
                    reach(problems, level, reach, 89, "reach III");
                    expect(problems, level, fired.at(10, 0, 3), Blocks.AIR, "dispenser tunnel");
                    expect(problems, level, fired.at(50, -5, 6), Blocks.AIR, "dispenser tunnel far corner");
                    if (level.getBlockEntity(dispenser) instanceof DispenserBlockEntity box && box.getItem(0).getDamageValue() != 1) {
                        problems.add("dispenser dream worn " + box.getItem(0).getDamageValue());
                    }
                    use(player, gold, start);
                    worn(problems, gold, 32);
                })
                .thenExecuteAfter(ticksFor(100), () -> {
                    reach(problems, level, at, 100, "gold");
                    expect(problems, level, at.at(50, 6, 3), Blocks.AIR, "gold 13 wide");
                    expect(problems, level, at.at(50, 7, 3), Blocks.STONE, "gold not 15 wide");
                    expect(problems, level, at.at(50, 0, 7), Blocks.STONE, "gold 7 tall");
                    use(player, diamond, start);
                    worn(problems, diamond, 64);
                })
                .thenExecuteAfter(ticksFor(150), () -> {
                    reach(problems, level, at, 150, "diamond");
                    expect(problems, level, at.at(50, -6, 8), Blocks.AIR, "diamond 9 tall");
                    expect(problems, level, at.at(50, 0, 9), Blocks.STONE, "diamond not 11 tall");
                    for (int i = 0; i < 3; i++) use(player, netherite, start);
                    if (netherite.isEmpty() || netherite.isDamageableItem()) problems.add("netherite wore out");
                })
                .thenExecuteAfter(ticksFor(200), () -> {
                    reach(problems, level, at, 200, "netherite");
                    expect(problems, level, at.at(50, 7, 8), Blocks.AIR, "netherite 15x9 corner");
                    expect(problems, level, at.at(50, 8, 3), Blocks.STONE, "netherite not 17 wide");
                    if (!problems.isEmpty()) helper.fail(String.join("; ", problems));
                })
                .thenSucceed();
    }

    private static int ticksFor(int length) {
        return (length + 2) / MinersDreamMod.LENGTH_PER_TICK.get() + 5;
    }

    private static Player player(GameTestHelper helper, BlockPos feet, float pitch) {
        Player player = helper.makeMockPlayer();
        player.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, pitch); // yRot 0 = facing south
        return player;
    }

    private static void fill(ServerLevel level, Blast.Frame f, int d0, int d1, int hw, int h0, int h1) {
        for (int d = d0; d <= d1; d++)
            for (int w = -hw; w <= hw; w++)
                for (int h = h0; h <= h1; h++) level.setBlock(f.at(d, w, h), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void clear(ServerLevel level, Blast.Frame f) {
        for (int h = 0; h < 2; h++) level.setBlock(f.at(0, 0, h), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void reach(List<String> problems, ServerLevel level, Blast.Frame f, int length, String what) {
        expect(problems, level, f.at(length - 1, 0, 3), Blocks.AIR, what + " end of tunnel");
        expect(problems, level, f.at(length, 0, 3), Blocks.STONE, what + " past the end");
    }

    private static void worn(List<String> problems, ItemStack stack, int max) {
        if (stack.getDamageValue() != 1 || stack.getMaxDamage() != max) problems.add(stack + " uses " + stack.getDamageValue() + "/" + stack.getMaxDamage());
    }

    private static InteractionResult use(Player player, ItemStack stack, BlockPos start) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos below = start.below();
        return stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false)));
    }

    private static void refuses(List<String> problems, Player player, Item dream, @Nullable Level where) {
        if (where == null) {
            problems.add("no level to try " + dream + " in");
            return;
        }
        ItemStack stack = new ItemStack(dream);
        BlockPos pos = player.blockPosition().below();
        // constructor is protected
        UseOnContext context = new UseOnContext(where, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)) {};
        if (dream.useOn(context) != InteractionResult.FAIL || stack.getDamageValue() != 0) problems.add(dream + " worked in " + where.dimension().location());
    }

    private static void expect(List<String> problems, ServerLevel level, BlockPos pos, Block block, String what) {
        Block found = level.getBlockState(pos).getBlock();
        if (found != block) problems.add(what + " is " + found);
    }

    private static void upgrade(List<String> problems, ServerLevel level, ItemStack dream, Item block, Item expected, int wear) {
        SimpleContainer table = new SimpleContainer(new ItemStack(Items.TNT), dream, new ItemStack(block));
        ItemStack out = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, table, level)
                .map(r -> r.assemble(table, level.registryAccess())).orElse(ItemStack.EMPTY);
        if (out.getItem() != expected || out.getDamageValue() != wear) problems.add(dream + " + " + block + " gave " + out + " wear " + out.getDamageValue());
    }
}
