package ru.chunkspeedrun.mixin;
import net.minecraft.world.level.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.chunkspeedrun.ChunkSpeedrun;
@Mixin(FlowingFluid.class)
public abstract class FluidMixin {
 @Inject(method="spreadTo",at=@At("HEAD"),cancellable=true) private void spread(LevelAccessor level,BlockPos pos,BlockState state,Direction direction,FluidState fluid,CallbackInfo ci){
  if(ChunkSpeedrun.locked()&&!ChunkSpeedrun.restoring&&level==ChunkSpeedrun.server.overworld()&&ChunkSpeedrun.run.inside(pos)!=ChunkSpeedrun.run.inside(pos.relative(direction.getOpposite())))ci.cancel();
 }
}
