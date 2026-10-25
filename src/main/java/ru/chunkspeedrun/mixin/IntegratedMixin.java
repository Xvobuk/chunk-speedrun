package ru.chunkspeedrun.mixin;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.BooleanSupplier;
import ru.chunkspeedrun.ChunkSpeedrun;
@Mixin(IntegratedServer.class)
public abstract class IntegratedMixin {
 @Inject(method="tickServer",at=@At("RETURN")) private void tick(BooleanSupplier supplier,CallbackInfo ci){MinecraftServer s=(MinecraftServer)(Object)this;ChunkSpeedrun.tick(s,s.isPaused());}
 @Inject(method="publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;ZI)Z",at=@At("HEAD"),cancellable=true)
 private void lan(MinecraftServer.MultiplayerScope scope,boolean commands,int port,CallbackInfoReturnable<Boolean> cir){if(ChunkSpeedrun.locked())cir.setReturnValue(false);}
}
