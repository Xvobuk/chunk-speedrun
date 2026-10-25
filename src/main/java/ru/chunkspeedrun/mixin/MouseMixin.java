package ru.chunkspeedrun.mixin;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.chunkspeedrun.ChunkSpeedrun;
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
 @Shadow private double accumulatedDX;
 @Shadow private double accumulatedDY;
 @Inject(method="turnPlayer",at=@At("HEAD")) private void turn(double time,CallbackInfo ci){var m=Minecraft.getInstance();if(m.player!=null&&m.gui.screen()==null&&!m.isPaused()&&(accumulatedDX!=0||accumulatedDY!=0))ChunkSpeedrun.signalInput();}
}
