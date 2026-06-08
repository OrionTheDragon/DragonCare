package com.dragoncare.command;

import com.dragoncare.DragonCare;
import com.dragoncare.worldgen.DragonHunterGuildPlacer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class ModCommands {

    private ModCommands() {}

    /* ===================== CONSTANTS — must mirror the actual placers ===================== */

    // Guild (DragonHunterGuildPlacer)
    private static final int GUILD_REGION_SIZE = 64;
    private static final double GUILD_BASE_CHANCE = 0.03;

    // Camp (DragonHunterPlacer)
    private static final int CAMP_REGION_SIZE = 8;
    private static final double CAMP_CHANCE = 0.80;

    /* ===================== Registration ===================== */

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        // Cheat-only: requires op level 2 (standard Minecraft cheat/op permission)
        dispatcher.register(CommandManager.literal("iaf_locate")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("camp")
                        .executes(ModCommands::locateCamp))
                .then(CommandManager.literal("forge")
                        .executes(ModCommands::locateForge))
                .then(CommandManager.literal("map")
                        .executes(ModCommands::giveGuildMap))
        );

        // Cheat-only: requires op level 2 (standard Minecraft cheat/op permission)
        dispatcher.register(CommandManager.literal("iaf_spawn")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("guild")
                        .executes(ModCommands::spawnGuild))
        );
    }

    /* ===================== /iaf_spawn guild ===================== */

    private static int spawnGuild(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        BlockPos pos = BlockPos.ofFloored(source.getPosition());
        DragonHunterGuildPlacer.forceSpawn(source.getWorld(), pos);
        source.sendFeedback(() -> Text.literal("Forced Dragon Hunter Guild spawn at " + pos.toShortString())
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int giveGuildMap(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // TODO: Implement map logic later. Currently disabled.
        source.sendError(Text.literal("This feature is currently disabled.").formatted(Formatting.RED));
        return 0;

        /*
        ServerWorld world = source.getWorld();
        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());

        BlockPos targetPos = null;

        com.dragoncare.worldgen.DragonHunterState state = com.dragoncare.worldgen.DragonHunterState.get(world);
        BlockPos placedGuild = state.getFirstGuildPos();
        if (placedGuild != null) {
            targetPos = placedGuild;
        } else {
            // Find closest planned sunflower spiral candidate
            BlockPos spawn = world.getSpawnPos();
            double baseAngle = (world.getSeed() & 0xFFFFFL) * (2.0 * Math.PI / 0xFFFFFL);
            double goldenAngle = 2.39996322972865332D;
            int sunflowerSamples = 40;
            int sunflowerMinRadius = 600;
            int sunflowerMaxRadius = 3000;

            List<BlockPos> candidates = new ArrayList<>();
            for (int i = 0; i < sunflowerSamples; i++) {
                double angle = baseAngle + i * goldenAngle;
                double t = (i + 0.5D) / sunflowerSamples;
                double radius = sunflowerMinRadius + (sunflowerMaxRadius - sunflowerMinRadius) * Math.sqrt(t);

                int blockX = spawn.getX() + (int) Math.round(Math.cos(angle) * radius);
                int blockZ = spawn.getZ() + (int) Math.round(Math.sin(angle) * radius);
                candidates.add(new BlockPos(blockX, 0, blockZ));
            }
            if (!candidates.isEmpty()) {
                candidates.sort(Comparator.comparingDouble(c ->
                        Math.pow(c.getX() - playerPos.getX(), 2) + Math.pow(c.getZ() - playerPos.getZ(), 2)));
                targetPos = candidates.get(0);
            }
        }

        if (targetPos == null) {
            source.sendError(Text.literal("Could not find any placed guilds or planned candidates. Try spawning one first."));
            return 0;
        }

        try {
            net.minecraft.server.network.ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld overworld = world.getServer() != null ? world.getServer().getOverworld() : world;

            // Generate map of scale 3 centered on targetPos (corresponds to the loot table map)
            net.minecraft.item.ItemStack map = net.minecraft.item.FilledMapItem.createMap(overworld, targetPos.getX(), targetPos.getZ(),
                    (byte) 3, true, true);
            
            // Add Red-X decoration to map state
            net.minecraft.item.map.MapState.addDecorationsNbt(map, targetPos, "+dragoncare_guild", net.minecraft.item.map.MapIcon.Type.RED_X);
            
            // Add localized custom name
            map.setCustomName(Text.translatable("item.dragoncare.guild_map"));

            // Insert into inventory, drop if full
            if (!player.getInventory().insertStack(map)) {
                player.dropItem(map, false);
            }

            BlockPos finalTarget = targetPos;
            source.sendFeedback(() -> Text.literal("Gave Dragon Hunter Guild Map pointing to " + finalTarget.toShortString())
                    .formatted(Formatting.GREEN), true);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendError(Text.literal("This command must be executed by a player."));
            return 0;
        }
        */
    }

    /* ===================== /iaf_locate camp ===================== */

    private static int locateCamp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerWorld world = source.getWorld();
        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());

        int playerRegX = Math.floorDiv(playerPos.getX() >> 4, CAMP_REGION_SIZE);
        int playerRegZ = Math.floorDiv(playerPos.getZ() >> 4, CAMP_REGION_SIZE);

        List<BlockPos> candidates = new ArrayList<>();

        for (int radius = 0; radius <= 15; radius++) {
            for (int rx = -radius; rx <= radius; rx++) {
                for (int rz = -radius; rz <= radius; rz++) {
                    if (Math.abs(rx) != radius && Math.abs(rz) != radius) continue;

                    int regX = playerRegX + rx;
                    int regZ = playerRegZ + rz;

                    // Reproduce EXACT same RNG sequence as DragonHunterPlacer.onChunkLoad
                    Random regionRng = new Random(world.getSeed()
                            ^ ((long) regX * 7392845L + (long) regZ * 938173L));

                    if (regionRng.nextDouble() < CAMP_CHANCE) {
                        int chunkX = regX * CAMP_REGION_SIZE + regionRng.nextInt(CAMP_REGION_SIZE);
                        int chunkZ = regZ * CAMP_REGION_SIZE + regionRng.nextInt(CAMP_REGION_SIZE);
                        // Camp uses: (chunkX << 4) + 4 + regionRng.nextInt(8)
                        int blockX = (chunkX << 4) + 4 + regionRng.nextInt(8);
                        int blockZ = (chunkZ << 4) + 4 + regionRng.nextInt(8);
                        candidates.add(new BlockPos(blockX, 0, blockZ));
                    }
                }
            }
            if (!candidates.isEmpty()) break;
        }

        if (candidates.isEmpty()) {
            source.sendError(Text.literal("No Camp sites found within scan range.").formatted(Formatting.RED));
            return 0;
        }

        candidates.sort(Comparator.comparingDouble(c ->
                Math.pow(c.getX() - playerPos.getX(), 2) + Math.pow(c.getZ() - playerPos.getZ(), 2)));

        int shown = 0;
        for (BlockPos c : candidates) {
            if (shown >= 3) break;
            String biomeInfo = getBiomeName(world, c.getX(), c.getZ());
            int dist = (int) Math.sqrt(Math.pow(c.getX() - playerPos.getX(), 2) + Math.pow(c.getZ() - playerPos.getZ(), 2));
            sendLocateResult(source, "Camp", c, dist, biomeInfo, true);
            shown++;
        }

        return 1;
    }

    /* ===================== /iaf_locate forge ===================== */

    private static int locateForge(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerWorld world = source.getWorld();
        BlockPos pos = BlockPos.ofFloored(source.getPosition());

        Identifier villageId = Identifier.of("minecraft", "village");
        Registry<Structure> registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);

        source.sendFeedback(() -> Text.literal("Searching for nearest village...").formatted(Formatting.GRAY), false);

        var result = world.getChunkManager().getChunkGenerator()
                .locateStructure(world,
                        registry.getOrCreateEntryList(TagKey.of(RegistryKeys.STRUCTURE, villageId)),
                        pos, 100, false);

        if (result != null) {
            BlockPos villagePos = result.getFirst();
            int dist = (int) Math.sqrt(villagePos.getSquaredDistance(pos));
            String biome = getBiomeName(world, villagePos.getX(), villagePos.getZ());
            sendLocateResult(source, "Village (Forge nearby)", villagePos, dist, biome, true);
            return 1;
        }

        source.sendError(Text.literal("No villages found within 100 chunks.").formatted(Formatting.RED));
        return 0;
    }

    /* ===================== Helpers ===================== */

    /** Get the human-readable biome name at a position. */
    private static String getBiomeName(ServerWorld world, int x, int z) {
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
        RegistryEntry<Biome> biomeEntry = world.getBiome(new BlockPos(x, y, z));
        Optional<net.minecraft.registry.RegistryKey<Biome>> keyOpt = biomeEntry.getKey();
        return keyOpt.map(k -> k.getValue().getPath()).orElse("unknown");
    }

    /** Send a locate result with distance, biome info, and clickable teleport. */
    private static void sendLocateResult(ServerCommandSource source, String type,
                                          BlockPos pos, int dist, String biome, boolean validBiome) {
        String coords = String.format("[%d, ~, %d]", pos.getX(), pos.getZ());

        MutableText msg = Text.literal("").append(
                Text.literal("● " + type).formatted(Formatting.GREEN, Formatting.BOLD)
        ).append(
                Text.literal(" at ").formatted(Formatting.GRAY)
        ).append(
                Text.literal(coords)
                        .formatted(Formatting.AQUA, Formatting.UNDERLINE)
                        .styled(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                        "/tp @s " + pos.getX() + " ~ " + pos.getZ()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Text.literal("Click to teleport"))))
        ).append(
                Text.literal(" (~" + dist + " blocks)").formatted(Formatting.GRAY)
        ).append(
                Text.literal(" [" + biome + "]").formatted(validBiome ? Formatting.DARK_GREEN : Formatting.RED)
        );

        source.sendFeedback(() -> msg, false);
    }
}


