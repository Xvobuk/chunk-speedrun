package ru.chunkspeedrun.mixin;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.chunkspeedrun.ChunkSpeedrun;
@Mixin(KeyboardInput.class)
public abstract class InputMixin {
 @Inject(method="tick",at=@At("TAIL")) private void tick(CallbackInfo ci){var m=Minecraft.getInstance();var k=((ClientInput)(Object)this).keyPresses;if(m.gui.screen()==null&&!m.isPaused()&&(k.forward()||k.backward()||k.left()||k.right()||k.jump()))ChunkSpeedrun.signalInput();}
}
