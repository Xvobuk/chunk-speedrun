package ru.chunkspeedrun.mixin;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.chunkspeedrun.ChunkSpeedrun;
@Mixin(Level.class)
public abstract class LevelMixin {
 @Inject(method="mayInteract",at=@At("HEAD"),cancellable=true)
 private void interact(net.minecraft.world.entity.Entity entity,BlockPos p,CallbackInfoReturnable<Boolean> cir){if(ChunkSpeedrun.locked()&&(Object)this==ChunkSpeedrun.server.overworld()&&!ChunkSpeedrun.run.inside(p))cir.setReturnValue(false);}
 @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("RETURN"))
 private void changed(BlockPos p,BlockState b,int flags,int limit,CallbackInfoReturnable<Boolean> cir){if(ChunkSpeedrun.locked()&&cir.getReturnValueZ()&&(Object)this==ChunkSpeedrun.server.overworld()&&ChunkSpeedrun.run.inside(p))ChunkSpeedrun.blocksChanged=true;}
 @Inject(method="destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",at=@At("HEAD"),cancellable=true)
 private void destroy(BlockPos p,boolean drops,net.minecraft.world.entity.Entity entity,int recursion,CallbackInfoReturnable<Boolean> cir){if(ChunkSpeedrun.locked()&&!ChunkSpeedrun.restoring&&(Object)this==ChunkSpeedrun.server.overworld()&&!ChunkSpeedrun.run.inside(p))cir.setReturnValue(false);}
 @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
 private void set(BlockPos p,BlockState b,int flags,int limit,CallbackInfoReturnable<Boolean> cir){
  if(ChunkSpeedrun.locked()&&!ChunkSpeedrun.restoring&&(Object)this==ChunkSpeedrun.server.overworld()&&!ChunkSpeedrun.run.inside(p))cir.setReturnValue(false);
 }
}
