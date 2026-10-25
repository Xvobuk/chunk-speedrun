package ru.chunkspeedrun.mixin;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.core.*;
import java.util.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.chunkspeedrun.ChunkSpeedrun;
@Mixin(PistonStructureResolver.class)
public abstract class PistonMixin {
 @Shadow public abstract List<BlockPos> getToPush();
 @Shadow public abstract List<BlockPos> getToDestroy();
 @Shadow @Final private Direction pushDirection;
 @Inject(method="resolve",at=@At("RETURN"),cancellable=true) private void resolve(CallbackInfoReturnable<Boolean> cir){if(ChunkSpeedrun.locked()&&cir.getReturnValueZ()&&(getToDestroy().stream().anyMatch(p->!ChunkSpeedrun.run.inside(p))||getToPush().stream().anyMatch(p->!ChunkSpeedrun.run.inside(p)||!ChunkSpeedrun.run.inside(p.relative(pushDirection)))))cir.setReturnValue(false);}
}
