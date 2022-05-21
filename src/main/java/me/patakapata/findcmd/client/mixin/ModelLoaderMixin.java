package me.patakapata.findcmd.client.mixin;

import com.mojang.datafixers.util.Pair;
import me.patakapata.findcmd.client.FindCmdClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(ModelLoader.class)
public abstract class ModelLoaderMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=stitching"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void inject_init(
            ResourceManager resourceManager,
            BlockColors blockColors,
            Profiler profiler,
            int i,
            CallbackInfo ci,
            Set<Pair<String, String>> set,
            Set<SpriteIdentifier> set2,
            Map<Identifier, List<SpriteIdentifier>> map
    ) {
        // ブロックアトラスに登録してアニメーションしてもらう
        map.get(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).add(new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, FindCmdClient.OVERLAY_RESOURCE_ID));
    }
}
