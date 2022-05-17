package me.patakapata.findcmd.client;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

@Environment(EnvType.CLIENT)
public class FindCmdClient implements ClientModInitializer {
    public static final List<Block> COMMAND_BLOCKS = ImmutableList.of(Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK);
    public static final Map<BlockPos, Pattern> queuedPos = new HashMap<>();
    public static final Gson GSON = new GsonBuilder().create();
    public static final Pattern DATA_PREFIX = Pattern.compile("^((-?\\d+)(, )?){3}");
    public static final Logger LOGGER = LogManager.getLogger("FindCmd");
    public static int resultLength = 5;

    @Override
    public void onInitializeClient() {
        DISPATCHER.register(
                literal("find").then(
                        argument("pattern", StringArgumentType.string())
                                .executes(ctx -> findCmd(ctx, 16)
                                )
                ).then(
                        argument("radius", IntegerArgumentType.integer(1)).then(
                                argument("pattern", StringArgumentType.string()
                                ).executes(ctx -> findCmd(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                        )
                )
        );
        DISPATCHER.register(
                literal("highlight").then(
                        argument("x", IntegerArgumentType.integer()).then(
                                argument("y", IntegerArgumentType.integer()).then(
                                        argument("z", IntegerArgumentType.integer()).then(
                                                argument("lifeTime", IntegerArgumentType.integer()).then(
                                                        argument("red", IntegerArgumentType.integer(0, 255)).then(
                                                                argument("green", IntegerArgumentType.integer(0, 255)).then(
                                                                        argument("blue", IntegerArgumentType.integer(0, 255)).then(
                                                                                argument("alpha", IntegerArgumentType.integer(0, 255))
                                                                                        .executes(FindCmdClient::highlightCmdPos)
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
        WorldRenderEvents.END.register(BlockHighlighter.getInstance()::onRenderWorld);
        ClientTickEvents.END_CLIENT_TICK.register(BlockHighlighter.getInstance()::onClientTick);
        ClientLifecycleEvents.CLIENT_STARTED.register(BlockHighlighter.getInstance()::onClientStart);
        ClientLifecycleEvents.CLIENT_STOPPING.register(BlockHighlighter.getInstance()::onClientStop);
    }

    private static int highlightCmdPos(CommandContext<FabricClientCommandSource> ctx) {
        BlockPos pos = new BlockPos(
                IntegerArgumentType.getInteger(ctx, "x"),
                IntegerArgumentType.getInteger(ctx, "y"),
                IntegerArgumentType.getInteger(ctx, "z")
        );
        int lifeTime = IntegerArgumentType.getInteger(ctx, "lifeTime");
        int color = ColorHelper.Argb.getArgb(
                IntegerArgumentType.getInteger(ctx, "alpha"),
                IntegerArgumentType.getInteger(ctx, "red"),
                IntegerArgumentType.getInteger(ctx, "green"),
                IntegerArgumentType.getInteger(ctx, "blue")
        );
        BlockHighlighter.getInstance().addHighlight(pos, color, lifeTime);
        return 1;
    }

    private static void sendDataGetCommand(ClientPlayerEntity player, BlockPos pos) {
        LOGGER.debug("Sending data command...");
        player.sendChatMessage(String.format("/data get block %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
        LOGGER.debug("Command sent");
    }

    private static int findCmd(CommandContext<FabricClientCommandSource> ctx, int radius) {
        String rawPattern = StringArgumentType.getString(ctx, "pattern");
        LOGGER.debug("Compiling pattern '" + rawPattern + "'");
        Pattern pattern = Pattern.compile(rawPattern);
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        int count = 0;

        queuedPos.clear();
        if (player != null) {
            World world = player.getWorld();
            BlockPos offset = new BlockPos(radius, radius, radius);
            Iterator<BlockPos> iterator = BlockPos.iterate(player.getBlockPos().subtract(offset), player.getBlockPos().add(offset)).iterator();

            BlockPos pos;
            BlockState state;
            while (iterator.hasNext()) {
                pos = iterator.next();
                state = world.getBlockState(pos);

                if (COMMAND_BLOCKS.contains(state.getBlock())) {
                    queuedPos.put(pos.mutableCopy(), pattern);
                    sendDataGetCommand(player, pos.toImmutable());
                    count++;
                }
            }
        }
        return count;
    }

    public static String escape(String str) {
        StringBuilder builder = new StringBuilder();

        for (char c : str.toCharArray()) {
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                default -> builder.append(c);
            }
        }

        return builder.toString();
    }

    public static String unescape(String str) {
        StringBuilder builder = new StringBuilder();
        boolean isLastBackSlash = false;

        for (char c : str.toCharArray()) {
            if (!isLastBackSlash && c == '\\') {
                isLastBackSlash = true;
            } else {
                builder.append(c);
                isLastBackSlash = false;
            }
        }

        return builder.toString();
    }

    public static void sendResult(BlockPos pos, String msg, @Nullable String tooltip) {
        String raw = "{\"translate\":\"[%s] %s, %s, %s > %s\",\"with\":["
                + "{\"text\":\"FindCmd\",\"color\":\"gold\"},"
                + "{\"text\":\"%s\",\"color\":\"red\"},".formatted("%5d".formatted(pos.getX()).replace(' ', '_'))
                + "{\"text\":\"%s\",\"color\":\"green\"},".formatted("%5d".formatted(pos.getY()).replace(' ', '_'))
                + "{\"text\":\"%s\",\"color\":\"aqua\"},".formatted("%5d".formatted(pos.getZ()).replace(' ', '_'))
                + "{\"text\":\"%s\"".formatted(escape(msg));

        try {
            if (tooltip != null) {
                raw += ",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to facing this block\n" + escape(tooltip) + "\"}";
            }

            raw += "}],\"color\":\"gray\"}";
            Text text = Text.Serializer.fromJson(raw);

            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(text);
        } catch (Exception ex) {
            LOGGER.error("Failed DeSerialize with:\n" + raw);
        }
    }
}
