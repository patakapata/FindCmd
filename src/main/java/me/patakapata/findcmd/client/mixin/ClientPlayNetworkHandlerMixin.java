package me.patakapata.findcmd.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import me.patakapata.findcmd.client.FindCmdClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.MessageType;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin implements ClientPlayPacketListener {
    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void inject_onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (packet.getType() == MessageType.SYSTEM && RenderSystem.isOnRenderThread() && packet.getMessage() instanceof TranslatableText message) {
            if (message.getKey().equals("commands.data.block.query") && FindCmdClient.handleFeedback(message)) {
                ci.cancel();
            }
        }
    }
}
