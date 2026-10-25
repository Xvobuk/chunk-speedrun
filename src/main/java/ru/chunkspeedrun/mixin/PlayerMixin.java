package ru.chunkspeedrun.mixin;
import net.minecraft.server.level.*;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ru.chunkspeedrun.ChunkSpeedrun;
import java.util.Set;
@Mixin(ServerPlayer.class)
public abstract class PlayerMixin {
 private static boolean csr$outside(ServerLevel level,double x,double z){var r=ChunkSpeedrun.run;return ChunkSpeedrun.locked()&&(level!=ChunkSpeedrun.server.overworld()||x<r.chunkX*16+.3||x>r.chunkX*16+15.7||z<r.chunkZ*16+.3||z>r.chunkZ*16+15.7);}
 @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",at=@At("HEAD"),cancellable=true)
 private void teleport(ServerLevel level,double x,double y,double z,Set<Relative> relative,float yaw,float pitch,boolean camera,CallbackInfoReturnable<Boolean> cir){if(csr$outside(level,x,z))cir.setReturnValue(false);}
 @Inject(method="teleportTo(DDD)V",at=@At("HEAD"),cancellable=true)
 private void position(double x,double y,double z,CallbackInfo ci){if(ChunkSpeedrun.locked()&&csr$outside(((ServerPlayer)(Object)this).level(),x,z))ci.cancel();}
 @Inject(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",at=@At("HEAD"),cancellable=true)
 private void portal(TeleportTransition transition,CallbackInfoReturnable<ServerPlayer> cir){if(csr$outside(transition.newLevel(),transition.position().x,transition.position().z))cir.setReturnValue((ServerPlayer)(Object)this);}
}
