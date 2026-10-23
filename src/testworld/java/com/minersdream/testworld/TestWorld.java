package com.minersdream.testworld;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Dev only: -Dminersdream.testWorld=true makes a test world with every dream on start-up.
@Mod(TestWorld.MOD_ID)
@Mod.EventBusSubscriber(modid = TestWorld.MOD_ID)
public final class TestWorld {
    static final String MOD_ID = "minersdream_testworld";
    private static final boolean ENABLED = Boolean.getBoolean("minersdream.testWorld");
    private static final boolean CREATIVE = Boolean.getBoolean("minersdream.testWorld.creative");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String GIVEN = "minersdream_testworld";
    private static final List<String> DREAMS = List.of("miners_dream", "iron_miners_dream", "gold_miners_dream",
            "diamond_miners_dream", "netherite_miners_dream");

    public TestWorld() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(GIVEN)) return;
        data.putBoolean(GIVEN, true);

        ServerLevel level = player.serverLevel();
        BlockPos cave = findCave(level, player.blockPosition());
        player.teleportTo(level, cave.getX() + 0.5, cave.getY(), cave.getZ() + 0.5, player.getYRot(), 0.0F);
        player.setRespawnPosition(level.dimension(), cave, 0.0F, true, false);

        for (String dream : DREAMS) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minersdream", dream));
            if (item != null && item != Items.AIR) player.getInventory().add(new ItemStack(item));
        }
        List.of(new ItemStack(Items.SMITHING_TABLE), new ItemStack(Items.TNT, 8), new ItemStack(Items.IRON_BLOCK, 2),
                new ItemStack(Items.GOLD_BLOCK, 2), new ItemStack(Items.DIAMOND_BLOCK, 2), new ItemStack(Items.NETHERITE_BLOCK, 2),
                new ItemStack(Items.TORCH, 64), new ItemStack(Items.DIAMOND_PICKAXE),
                book("reach", 3), book("blast_radius", 2),
                new ItemStack(Items.ANVIL), new ItemStack(Items.DISPENSER), new ItemStack(Items.LEVER)).forEach(s -> player.getInventory().add(s));
        player.giveExperienceLevels(30);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        LOGGER.info("[TestWorld] {} placed at {} on {}", player.getGameProfile().getName(), cave, level.getBlockState(cave.below()));
    }

    private static ItemStack book(String enchantment, int level) {
        Enchantment e = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("minersdream", enchantment));
        return e == null ? ItemStack.EMPTY : EnchantedBookItem.createForEnchantment(new EnchantmentInstance(e, level));
    }

    // natural cave near spawn, otherwise carve a room
    private static BlockPos findCave(ServerLevel level, BlockPos spawn) {
        for (int r = 0; r <= 48; r += 8)
            for (int dx = -r; dx <= r; dx += 8)
                for (int dz = -r; dz <= r; dz += 8) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // nearest first
                    for (int y = -50; y <= -8; y++) {
                        BlockPos p = new BlockPos(spawn.getX() + dx, y, spawn.getZ() + dz);
                        if (level.getBlockState(p.below()).is(Blocks.DEEPSLATE)
                                && level.getBlockState(p).is(Blocks.CAVE_AIR)
                                && level.getBlockState(p.above()).is(Blocks.CAVE_AIR)
                                && level.getBlockState(p.above(2)).is(Blocks.CAVE_AIR)) return p;
                    }
                }
        BlockPos room = new BlockPos(spawn.getX(), -40, spawn.getZ());
        for (BlockPos p : BlockPos.betweenClosed(room.offset(-3, 0, -3), room.offset(3, 3, 3))) {
            level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (BlockPos p : BlockPos.betweenClosed(room.offset(-3, -1, -3), room.offset(3, -1, 3))) {
            level.setBlock(p, Blocks.DEEPSLATE.defaultBlockState(), Block.UPDATE_ALL);
        }
        LOGGER.info("[TestWorld] no natural cave near spawn, carved a room");
        return room;
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        private Client() {}

        private static boolean created;
        private static int ticksInWorld;

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent event) {
            if (!ENABLED || event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (!created && mc.screen instanceof TitleScreen) {
                created = true;
                String name = "Miners Dream Test " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm"));
                LevelSettings settings = new LevelSettings(name, CREATIVE ? GameType.CREATIVE : GameType.SURVIVAL, false,
                        Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(WorldOptions.randomSeed(), true, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions());
            }
            // screenshot to check the setup
            if (mc.player != null && mc.screen == null && ++ticksInWorld == 100) {
                Screenshot.grab(mc.gameDirectory, "minersdream-testworld.png", mc.getMainRenderTarget(),
                        msg -> LOGGER.info("[TestWorld] screenshot: {}", msg.getString()));
            }
            if (SHOWCASE && mc.player != null && mc.screen == null) Showcase.tick(mc);
        }
    }

    private static final boolean SHOWCASE = Boolean.getBoolean("minersdream.showcase");

    // -Pshowcase: records each dream blasting a tunnel to run/showcase/ (frames for the mod page gifs)
    private static final class Showcase {
        private record Shot(String dream, int length) {}

        private static final List<Shot> SHOTS = List.of(new Shot("miners_dream", 51), new Shot("iron_miners_dream", 75),
                new Shot("gold_miners_dream", 100), new Shot("diamond_miners_dream", 150), new Shot("netherite_miners_dream", 200));
        private static final int SETTLE = 80; // let chunks render first
        private static int shot = -1, ticks, frame;

        static void tick(Minecraft mc) {
            IntegratedServer server = mc.getSingleplayerServer();
            if (server == null) return;
            mc.getToasts().clear();
            mc.gui.getChat().clearMessages(false);
            if (shot == -1) {
                if (ticks == 0) {
                    // small window, big frames lag the chunk rebuilds
                    if (mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
                    GLFW.glfwRestoreWindow(mc.getWindow().getWindow());
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 960, 540);
                }
                if (++ticks < 100) return; // wait for the world to load
                shot = 0;
                ticks = 0;
            }
            if (shot >= SHOTS.size()) {
                if (shot++ == SHOTS.size()) mc.stop();
                return;
            }
            int index = shot;
            Shot s = SHOTS.get(index);
            if (ticks == 0) server.execute(() -> setUp(server, index, s));
            if (ticks == SETTLE) {
                server.execute(() -> fire(server));
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            if (ticks >= SETTLE - 10 && ticks % 2 == 0) capture(mc, s.dream(), frame++);
            if (++ticks > SETTLE + s.length() / 4 + 60) {
                shot++;
                ticks = 0;
                frame = 0;
            }
        }

        private static void setUp(IntegratedServer server, int index, Shot s) {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            server.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
            BlockPos spawn = level.getSharedSpawnPos();
            BlockPos room = new BlockPos(spawn.getX() + 48 * index, -52, spawn.getZ());
            for (BlockPos p : BlockPos.betweenClosed(room.offset(-2, 0, -2), room.offset(2, 3, 2))) {
                level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            for (BlockPos p : BlockPos.betweenClosed(room.offset(-2, -1, -2), room.offset(2, -1, 2))) {
                level.setBlock(p, Blocks.DEEPSLATE.defaultBlockState(), Block.UPDATE_ALL);
            }
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minersdream", s.dream()));
            player.getInventory().setItem(index, new ItemStack(item));
            player.getInventory().selected = index;
            player.connection.send(new ClientboundSetCarriedItemPacket(index));
            player.teleportTo(level, room.getX() + 0.5, room.getY(), room.getZ() + 0.5, 0.0F, 12.0F);
        }

        private static void fire(IntegratedServer server) {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            BlockPos below = player.blockPosition().below();
            player.getMainHandItem().getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false)));
        }

        private static void capture(Minecraft mc, String dream, int frame) {
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                Path dir = mc.gameDirectory.toPath().resolve("showcase").resolve(dream);
                Files.createDirectories(dir);
                image.writeToFile(dir.resolve(String.format("%03d.png", frame)));
            } catch (IOException e) {
                LOGGER.error("[TestWorld] showcase frame failed", e);
            }
        }
    }
}
