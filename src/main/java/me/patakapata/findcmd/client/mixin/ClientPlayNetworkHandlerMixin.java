package me.patakapata.findcmd.client.mixin;

import com.google.gson.internal.LinkedTreeMap;
import com.mojang.blaze3d.systems.RenderSystem;
import me.patakapata.findcmd.client.BlockHighlighter;
import me.patakapata.findcmd.client.FindCmdClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.MessageType;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin implements ClientPlayPacketListener {

    private static String textAsString(Text text) {
        StringBuilder builder = new StringBuilder();
        text.visit(asString -> {
            builder.append(asString);
            return Optional.empty();
        });
        return builder.toString();
    }

    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void inject_onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (packet.getType() == MessageType.SYSTEM && RenderSystem.isOnRenderThread()) {
            try {
                String msg = textAsString(packet.getMessage());
                int i = msg.indexOf('{');

                if (i != -1 && FindCmdClient.DATA_PREFIX.matcher(msg).find()) {
                    String data = msg.substring(i);

                    @SuppressWarnings("unchecked") LinkedTreeMap<String, Object> map = (LinkedTreeMap<String, Object>) FindCmdClient.GSON.fromJson(data, Object.class);
                    String command = (String) map.get("Command");
                    BlockPos pos = new BlockPos((double) map.get("x"), (double) map.get("y"), (double) map.get("z"));
                    Pattern pattern = FindCmdClient.queuedPos.get(pos);

                    if (pattern != null) {
                        FindCmdClient.queuedPos.remove(pos);
                        Matcher matcher = pattern.matcher(command);

                        if (matcher.find()) {
                            String head = command.substring(0, matcher.start());
                            String body = matcher.group();
                            String foot = command.substring(matcher.end());

                            if (head.length() > FindCmdClient.resultLength + 2) {
                                head = ".." + head.substring(head.length() - FindCmdClient.resultLength);
                            }
                            head = head + "§d§n";
                            if (foot.length() > FindCmdClient.resultLength + 2) {
                                foot = foot.substring(0, FindCmdClient.resultLength) + "..";
                            }
                            foot = "§r§7" + foot;

                            FindCmdClient.sendResult(pos, head + body + foot, command);
                            BlockHighlighter.getInstance().addHighlight(pos, BlockHighlighter.randomColor(), 200);
                        } else {
                            FindCmdClient.LOGGER.debug("Doesn't match with specified pattern by command");
                        }
                        ci.cancel();
                    } else {
                        FindCmdClient.LOGGER.debug("Can not find pattern with " + pos.toShortString());
                    }
                } else {
                    FindCmdClient.LOGGER.debug("Doesn't match with data prefix pattern");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
