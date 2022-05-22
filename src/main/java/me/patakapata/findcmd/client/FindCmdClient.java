package me.patakapata.findcmd.client;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.patakapata.findcmd.client.mixin.accessor.DefaultPosArgumentAccessor;
import me.patakapata.findcmd.client.mixin.accessor.LookingPosArgumentAccessor;
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
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DefaultPosArgument;
import net.minecraft.command.argument.LookingPosArgument;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

@Environment(EnvType.CLIENT)
public class FindCmdClient implements ClientModInitializer {
    public static final List<Block> COMMAND_BLOCKS = ImmutableList.of(Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK);
    public static final Map<BlockPos, Pattern> queuedPos = new HashMap<>();
    public static final Logger LOGGER = LogManager.getLogger("FindCmd");
    public static final String MOD_ID = "findcmd";

    public static final Identifier TEXTURE_ATLAS = new Identifier("findcmd", "textures/atlas.png");
    public static final Identifier OVERLAY_RESOURCE_ID = new Identifier(MOD_ID, "misc/overlay");

    public static SpriteAtlasTexture SPRITE_ATLAS;
    public static Sprite OVERLAY_SPRITE;

    private static final MutableText TEMPLATE;

    static {
        TEMPLATE = new LiteralText("");
        TEMPLATE.setStyle(TEMPLATE.getStyle().withColor(Formatting.GRAY));

        LiteralText findCmd = new LiteralText("FindCmd");
        findCmd.setStyle(findCmd.getStyle().withColor(Formatting.GOLD));

        MutableText body = new TranslatableText("[%s] ", findCmd);
        body.setStyle(TEMPLATE.getStyle().withColor(Formatting.DARK_GRAY));

        TEMPLATE.append(body);
    }

    public static Sprite getDedicatedOverlaySprite() {
        return OVERLAY_SPRITE;
    }

    @SuppressWarnings("unused")
    public static Text applyTemplate(String body) {
        return TEMPLATE.shallowCopy().append(body);
    }

    public static Text applyTemplate(Text body) {
        return TEMPLATE.shallowCopy().append(body);
    }

    @Override
    public void onInitializeClient() {
        DISPATCHER.register(
                literal("find").then(
                        argument("pattern", StringArgumentType.string())
                                .executes(ctx -> findCmd(ctx, 16))).then(
                        argument("radius", IntegerArgumentType.integer(1)).then(
                                argument("pattern", StringArgumentType.string())
                                        .requires(src -> src.hasPermissionLevel(2))
                                        .executes(ctx -> findCmd(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                        )
                )
        );

        DISPATCHER.register(
                literal("highlight").then(
                        argument("position", BlockPosArgumentType.blockPos()).then(
                                argument("color", ColorArgumentType.color())
                                        .executes(ctx -> highlightPos(new Box(getBlockPos(ctx, "position")), Color.getColor(ctx, "color")))
                        ).then(
                                argument("end", BlockPosArgumentType.blockPos()).then(
                                        argument("color", ColorArgumentType.color())
                                                .executes(ctx -> highlightPos(new Box(getBlockPos(ctx, "position"), getBlockPos(ctx, "end")), Color.getColor(ctx, "color")))
                                ).executes(ctx -> highlightPos(new Box(getBlockPos(ctx, "position"), getBlockPos(ctx, "end")), new Color(BlockHighlighter.randomColor())))
                        ).executes(ctx -> highlightPos(new Box(getBlockPos(ctx, "position")), new Color(BlockHighlighter.randomColor())))
                )
        );

        WorldRenderEvents.END.register(BlockHighlighter.getInstance()::onRenderWorld);
        ClientTickEvents.END_CLIENT_TICK.register(BlockHighlighter.getInstance()::onClientTick);
        ClientLifecycleEvents.CLIENT_STARTED.register(BlockHighlighter.getInstance()::onClientStart);
        ClientLifecycleEvents.CLIENT_STOPPING.register(BlockHighlighter.getInstance()::onClientStop);
    }

    public static void registerTexture(TextureManager texManager, ReloadableResourceManagerImpl resManager) {
        resManager.registerReloader(new SinglePreparationResourceReloader<>() {
            @Override
            protected Object prepare(ResourceManager manager, Profiler profiler) {
                return null;
            }

            @Override
            protected void apply(Object prepared, ResourceManager manager, Profiler profiler) {
                if (SPRITE_ATLAS != null) {
                    SPRITE_ATLAS.clear();
                }

                SPRITE_ATLAS = new SpriteAtlasTexture(TEXTURE_ATLAS);
                List<Identifier> sprites = List.of(OVERLAY_RESOURCE_ID);
                SpriteAtlasTexture.Data data = SPRITE_ATLAS.stitch(resManager, sprites.stream(), profiler, 0);

                SPRITE_ATLAS.upload(data);
                texManager.registerTexture(SPRITE_ATLAS.getId(), SPRITE_ATLAS);
                texManager.bindTexture(SPRITE_ATLAS.getId());
                SPRITE_ATLAS.applyTextureFilter(data);

                OVERLAY_SPRITE = SPRITE_ATLAS.getSprite(OVERLAY_RESOURCE_ID);
            }
        });
    }

    public static BlockPos getBlockPos(CommandContext<FabricClientCommandSource> ctx, String name) {
        PosArgument arg = ctx.getArgument(name, PosArgument.class);
        if (arg instanceof DefaultPosArgument defaultPos) {
            return new BlockPos(defaultPosToAbsolutePos(defaultPos, ctx.getSource()));
        } else if (arg instanceof LookingPosArgument lookingPos) {
            return new BlockPos(lookingPosToAbsolutePos(lookingPos, ctx.getSource()));
        } else {
            throw new IllegalArgumentException("Unexpected argument type: " + arg.getClass().getSimpleName());
        }
    }

    public static Vec3d defaultPosToAbsolutePos(DefaultPosArgument arg, FabricClientCommandSource src) {
        Vec3d vec = src.getPosition();
        DefaultPosArgumentAccessor access = (DefaultPosArgumentAccessor) arg;
        return new Vec3d(access.accessor_getX().toAbsoluteCoordinate(vec.x), access.accessor_getY().toAbsoluteCoordinate(vec.y), access.accessor_getZ().toAbsoluteCoordinate(vec.z));
    }

    public static Vec3d lookingPosToAbsolutePos(LookingPosArgument arg, FabricClientCommandSource src) {
        Vec2f vec2f = src.getRotation();
        Vec3d vec3d = src.getPosition().add(0, src.getEntity().getStandingEyeHeight(), 0);
        LookingPosArgumentAccessor accessor = (LookingPosArgumentAccessor) arg;
        double x = accessor.accessor_getX();
        double y = accessor.accessor_getY();
        double z = accessor.accessor_getZ();
        float f = MathHelper.cos((vec2f.y + 90.0F) * 0.017453292F);
        float g = MathHelper.sin((vec2f.y + 90.0F) * 0.017453292F);
        float h = MathHelper.cos(-vec2f.x * 0.017453292F);
        float i = MathHelper.sin(-vec2f.x * 0.017453292F);
        float j = MathHelper.cos((-vec2f.x + 90.0F) * 0.017453292F);
        float k = MathHelper.sin((-vec2f.x + 90.0F) * 0.017453292F);
        Vec3d vec3d2 = new Vec3d(f * h, i, g * h);
        Vec3d vec3d3 = new Vec3d(f * j, k, g * j);
        Vec3d vec3d4 = vec3d2.crossProduct(vec3d3).multiply(-1.0D);
        double d = vec3d2.x * z + vec3d3.x * y + vec3d4.x * x;
        double e = vec3d2.y * z + vec3d3.y * y + vec3d4.y * x;
        double l = vec3d2.z * z + vec3d3.z * y + vec3d4.z * x;
        return new Vec3d(vec3d.x + d, vec3d.y + e, vec3d.z + l);
    }

    @SuppressWarnings("unused")
    public static Vec2f toAbsoluteRot(DefaultPosArgument arg, FabricClientCommandSource src) {
        Vec2f vec = src.getRotation();
        DefaultPosArgumentAccessor access = (DefaultPosArgumentAccessor) arg;
        return new Vec2f((float) access.accessor_getX().toAbsoluteCoordinate(vec.x), (float) access.accessor_getY().toAbsoluteCoordinate(vec.y));
    }

    private static int highlightPos(Box box, Color color) {
        BlockHighlighter.getInstance().addHighlight(box, color.getPacked(), 200);
        return 1;
    }

    private static void sendDataGetCommand(ClientPlayerEntity player, BlockPos pos) {
        LOGGER.debug("Sending data command...");
        player.sendChatMessage(String.format("/data get block %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
        LOGGER.debug("Command sent");
    }

    public static boolean handleFeedback(TranslatableText message) {
        Object[] args = message.getArgs();

        BlockPos pos;
        try {
            pos = new BlockPos((int) args[0], (int) args[1], (int) args[2]);
        } catch (ClassCastException ex1) {
            try {
                pos = new BlockPos(Integer.parseInt((String) args[0]), Integer.parseInt((String) args[1]), Integer.parseInt((String) args[2]));
            } catch (ClassCastException | NumberFormatException ex2) {
                ex2.printStackTrace();
                return false;
            }
        }

        Pattern pattern;
        if (queuedPos.containsKey(pos)) {
            pattern = queuedPos.remove(pos);
        } else {
            return false;
        }

        NbtCompound nbt;
        try {
            nbt = StringNbtReader.parse(((Text) args[3]).getString());
        } catch (CommandSyntaxException ex) {
            ex.printStackTrace();
            return false;
        }

        ChatHud chat = MinecraftClient.getInstance().inGameHud.getChatHud();
        String command = nbt.getString("Command");
        Matcher m = pattern.matcher(command);

        if (m.find()) {
            int start = m.start();
            int end = m.end();
            String found = m.group();

            String matchHead = command.substring(Math.max(start - 5, 0), start);
            if (start >= 5) {
                matchHead = ".." + matchHead;
            }
            String matchFeet = command.substring(end, Math.min(end + 6, command.length()));
            if (command.length() - end >= 5) {
                matchFeet += "..";
            }

            MutableText body = new LiteralText("");
            MutableText x = new LiteralText("%6d".formatted(pos.getX()));
            MutableText y = new LiteralText("%6d".formatted(pos.getY()));
            MutableText z = new LiteralText("%6d".formatted(pos.getZ()));
            MutableText match = new LiteralText(found);
            x.setStyle(x.getStyle().withColor(Formatting.GREEN));
            y.setStyle(y.getStyle().withColor(Formatting.RED));
            z.setStyle(z.getStyle().withColor(Formatting.AQUA));
            match.setStyle(match.getStyle().withColor(Formatting.LIGHT_PURPLE).withUnderline(true));
            body.append(x)
                    .append(", ")
                    .append(y)
                    .append(", ")
                    .append(z)
                    .append(" > ")
                    .append(matchHead)
                    .append(match)
                    .append(matchFeet)
                    .setStyle(body.getStyle()
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(command)))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/highlight %d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ())))
                    );

            chat.addMessage(applyTemplate(body));
        }

        return true;
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

}
