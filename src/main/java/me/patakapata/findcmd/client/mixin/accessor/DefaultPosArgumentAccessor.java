package me.patakapata.findcmd.client.mixin.accessor;

import net.minecraft.command.argument.CoordinateArgument;
import net.minecraft.command.argument.DefaultPosArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


@Mixin(DefaultPosArgument.class)
public interface DefaultPosArgumentAccessor {
    @Accessor("x")
    CoordinateArgument accessor_getX();

    @Accessor("y")
    CoordinateArgument accessor_getY();

    @Accessor("z")
    CoordinateArgument accessor_getZ();
}
