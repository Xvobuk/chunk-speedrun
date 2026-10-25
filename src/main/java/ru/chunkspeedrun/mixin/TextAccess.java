package ru.chunkspeedrun.mixin;
import net.minecraft.world.entity.Display;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Display.TextDisplay.class)
public interface TextAccess {
 @Invoker("setText") void csr$text(Component component);
 @Invoker("setLineWidth") void csr$width(int width);
 @Invoker("setFlags") void csr$flags(byte flags);
}
