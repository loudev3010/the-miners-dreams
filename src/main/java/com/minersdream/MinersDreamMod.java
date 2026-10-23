package com.minersdream;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

@Mod(MinersDreamMod.MOD_ID)
public class MinersDreamMod {
    public static final String MOD_ID = "minersdream";

    // config/minersdream-common.toml
    private static final ForgeConfigSpec.Builder CONFIG = new ForgeConfigSpec.Builder();
    static final ForgeConfigSpec.IntValue LENGTH_PER_TICK = CONFIG
            .comment("Blocks of tunnel length a blast carves per tick. Higher is faster but can lag a server.")
            .defineInRange("lengthPerTick", 4, 1, 256);
    static final ForgeConfigSpec.BooleanValue TORCHES = CONFIG
            .comment("Light Overworld and End tunnels with torches.")
            .define("torches", true);
    static final ForgeConfigSpec.BooleanValue RAILS = CONFIG
            .comment("Lay a rail down the middle of every tunnel.")
            .define("rails", false);
    private static final ForgeConfigSpec CONFIG_SPEC = CONFIG.build();

    private static final Set<ResourceKey<Level>> OVERWORLD = Set.of(Level.OVERWORLD);
    private static final Set<ResourceKey<Level>> OVERWORLD_AND_NETHER = Set.of(Level.OVERWORLD, Level.NETHER);
    private static final Set<ResourceKey<Level>> ALL = Set.of(Level.OVERWORLD, Level.NETHER, Level.END);

    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MOD_ID);

    private static final EnchantmentCategory DREAMS = EnchantmentCategory.create("MINERSDREAM_DREAMS", item -> item instanceof MinersDreamItem);
    public static final RegistryObject<Enchantment> REACH = ENCHANTMENTS.register("reach",
            () -> new DreamEnchantment(Enchantment.Rarity.UNCOMMON, 3));
    public static final RegistryObject<Enchantment> BLAST_RADIUS = ENCHANTMENTS.register("blast_radius",
            () -> new DreamEnchantment(Enchantment.Rarity.RARE, 2));

    // uses / size (length, half width, height) per tier
    public static final RegistryObject<Item> MINERS_DREAM = ITEMS.register("miners_dream",
            () -> new MinersDreamItem(new Item.Properties().durability(4).rarity(Rarity.UNCOMMON), OVERWORLD, new MinersDreamItem.Size(51, 5, 7)));
    public static final RegistryObject<Item> IRON_MINERS_DREAM = ITEMS.register("iron_miners_dream",
            () -> new MinersDreamItem(new Item.Properties().durability(16).rarity(Rarity.UNCOMMON), OVERWORLD, new MinersDreamItem.Size(75, 5, 7)));
    public static final RegistryObject<Item> GOLD_MINERS_DREAM = ITEMS.register("gold_miners_dream",
            () -> new MinersDreamItem(new Item.Properties().durability(32).rarity(Rarity.UNCOMMON), OVERWORLD_AND_NETHER, new MinersDreamItem.Size(100, 6, 7)));
    public static final RegistryObject<Item> DIAMOND_MINERS_DREAM = ITEMS.register("diamond_miners_dream",
            () -> new MinersDreamItem(new Item.Properties().durability(64).rarity(Rarity.RARE), ALL, new MinersDreamItem.Size(150, 6, 9)));
    public static final RegistryObject<Item> NETHERITE_MINERS_DREAM = ITEMS.register("netherite_miners_dream",
            () -> new MinersDreamItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC), ALL, new MinersDreamItem.Size(200, 7, 9)));

    public MinersDreamMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        ENCHANTMENTS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) ITEMS.getEntries().forEach(event::accept);
        });
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(
                () -> ITEMS.getEntries().forEach(item -> DispenserBlock.registerBehavior(item.get(), MinersDreamItem.DISPENSE))));
    }

    private static final class DreamEnchantment extends Enchantment {
        private final int maxLevel;

        DreamEnchantment(Rarity rarity, int maxLevel) {
            super(rarity, DREAMS, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
            this.maxLevel = maxLevel;
        }

        @Override
        public int getMaxLevel() {
            return maxLevel;
        }

        @Override
        public int getMinCost(int level) {
            return 10 * level;
        }

        @Override
        public int getMaxCost(int level) {
            return 10 * level + 30;
        }
    }
}
