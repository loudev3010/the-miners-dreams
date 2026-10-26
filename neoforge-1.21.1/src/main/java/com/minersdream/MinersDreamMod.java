package com.minersdream;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

@Mod(MinersDreamMod.MOD_ID)
public class MinersDreamMod {
    public static final String MOD_ID = "minersdream";

    // config/minersdream-common.toml
    private static final ModConfigSpec.Builder CONFIG = new ModConfigSpec.Builder();
    static final ModConfigSpec.IntValue LENGTH_PER_TICK = CONFIG
            .comment("Blocks of tunnel length a blast carves per tick. Higher is faster but can lag a server.")
            .defineInRange("lengthPerTick", 4, 1, 256);
    static final ModConfigSpec.BooleanValue TORCHES = CONFIG
            .comment("Light Overworld and End tunnels with torches.")
            .define("torches", true);
    static final ModConfigSpec.BooleanValue RAILS = CONFIG
            .comment("Lay a rail down the middle of every tunnel.")
            .define("rails", false);
    private static final ModConfigSpec CONFIG_SPEC = CONFIG.build();

    private static final Set<ResourceKey<Level>> OVERWORLD = Set.of(Level.OVERWORLD);
    private static final Set<ResourceKey<Level>> OVERWORLD_AND_NETHER = Set.of(Level.OVERWORLD, Level.NETHER);
    private static final Set<ResourceKey<Level>> ALL = Set.of(Level.OVERWORLD, Level.NETHER, Level.END);

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    // enchantments are data in 1.21 (data/minersdream/enchantment)
    public static final ResourceKey<Enchantment> REACH = enchantment("reach");
    public static final ResourceKey<Enchantment> BLAST_RADIUS = enchantment("blast_radius");

    // uses / size (length, half width, height) per tier
    public static final DeferredItem<Item> MINERS_DREAM = ITEMS.register("miners_dream",
            () -> new MinersDreamItem(new Item.Properties().durability(4).rarity(Rarity.UNCOMMON), OVERWORLD, new MinersDreamItem.Size(51, 5, 7)));
    public static final DeferredItem<Item> IRON_MINERS_DREAM = ITEMS.register("iron_miners_dream",
            () -> new MinersDreamItem(new Item.Properties().durability(16).rarity(Rarity.UNCOMMON), OVERWORLD, new MinersDreamItem.Size(75, 5, 7)));
    public static final DeferredItem<Item> GOLD_MINERS_DREAM = ITEMS.register("gold_miners_dream",
            () -> new MinersDreamItem(new Item.Properties().durability(32).rarity(Rarity.UNCOMMON), OVERWORLD_AND_NETHER, new MinersDreamItem.Size(100, 6, 7)));
    public static final DeferredItem<Item> DIAMOND_MINERS_DREAM = ITEMS.register("diamond_miners_dream",
            () -> new MinersDreamItem(new Item.Properties().durability(64).rarity(Rarity.RARE), ALL, new MinersDreamItem.Size(150, 6, 9)));
    public static final DeferredItem<Item> NETHERITE_MINERS_DREAM = ITEMS.register("netherite_miners_dream",
            () -> new MinersDreamItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC), ALL, new MinersDreamItem.Size(200, 7, 9)));

    public MinersDreamMod(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) ITEMS.getEntries().forEach(item -> event.accept(item.get()));
        });
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(
                () -> ITEMS.getEntries().forEach(item -> DispenserBlock.registerBehavior(item.get(), MinersDreamItem.DISPENSE))));
    }

    private static ResourceKey<Enchantment> enchantment(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(MOD_ID, name));
    }
}
