package com.minersdream;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.common.util.FakePlayerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class MinersDreamItem extends Item {
    private static final int COOLDOWN_TICKS = 20;

    record Size(int length, int halfWidth, int height) {
        // reach +25% length per level, blast radius +2 wide/tall per level
        Size enchanted(ItemStack stack) {
            int reach = EnchantmentHelper.getItemEnchantmentLevel(MinersDreamMod.REACH.get(), stack);
            int radius = EnchantmentHelper.getItemEnchantmentLevel(MinersDreamMod.BLAST_RADIUS.get(), stack);
            return new Size(length + length * reach / 4, halfWidth + radius, height + 2 * radius);
        }
    }

    static final DispenseItemBehavior DISPENSE = new OptionalDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            ServerLevel level = source.getLevel();
            MinersDreamItem dream = (MinersDreamItem) stack.getItem();
            setSuccess(dream.dimensions.contains(level.dimension()));
            if (isSuccess()) {
                Size s = dream.size.enchanted(stack);
                Direction facing = source.getBlockState().getValue(DispenserBlock.FACING);
                Blast.start(level, FakePlayerFactory.getMinecraft(level), source.getPos(),
                        Blast.Frame.toward(source.getPos(), facing, Direction.NORTH, s.height()), s);
                if (stack.hurt(1, level.random, null)) stack.shrink(1);
            }
            return stack;
        }
    };

    private final Set<ResourceKey<Level>> dimensions;
    private final Component where;
    private final Size size;

    public MinersDreamItem(Properties properties, Set<ResourceKey<Level>> dimensions, Size size) {
        super(properties);
        this.dimensions = dimensions;
        this.size = size;
        this.where = Component.translatable("dimension.minersdream." + (dimensions.size() == 1
                ? dimensions.iterator().next().location().getPath()
                : dimensions.contains(Level.END) ? "all" : "overworld_and_nether"));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!dimensions.contains(level.dimension())) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.minersdream.wrong_dimension", where), true);
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            Size s = size.enchanted(context.getItemInHand());
            Blast.start(server, player, player.blockPosition(), Blast.Frame.of(player, s.height()), s);
            context.getItemInHand().hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public int getEnchantmentValue() {
        return 10;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true; // netherite one has no durability but should still take reach/blast radius
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Size s = size.enchanted(stack);
        tooltip.add(Component.translatable("tooltip.minersdream.tunnel", s.length(), 2 * s.halfWidth() + 1, s.height()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.minersdream.aim").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.minersdream.works_in", where).withStyle(ChatFormatting.GRAY));
        tooltip.add(stack.isDamageableItem()
                ? Component.translatable("tooltip.minersdream.uses", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                        .withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.minersdream.unbreakable").withStyle(ChatFormatting.GOLD));
    }
}
