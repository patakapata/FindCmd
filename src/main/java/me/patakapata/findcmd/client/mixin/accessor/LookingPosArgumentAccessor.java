package me.patakapata.findcmd.client.mixin.accessor;

import net.minecraft.command.argument.LookingPosArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LookingPosArgument.class)
public interface LookingPosArgumentAccessor {
    @Accessor("x")
    double accessor_getX();

    @Accessor("y")
    double accessor_getY();

    @Accessor("z")
    double accessor_getZ();
}
