package ru.chunkspeedrun.mixin;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.chunkspeedrun.ChunkSpeedrun;
@Mixin(MinecraftServer.class)
public abstract class ServerMixin {
 @Inject(method="stopServer",at=@At("HEAD")) private void stop(CallbackInfo ci){ChunkSpeedrun.stop((MinecraftServer)(Object)this);}
}
