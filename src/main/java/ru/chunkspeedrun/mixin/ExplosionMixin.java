package ru.chunkspeedrun.mixin;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.core.BlockPos;
import java.util.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.chunkspeedrun.ChunkSpeedrun;
@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {
 @Inject(method="calculateExplodedPositions",at=@At("RETURN"),cancellable=true) private void blocks(CallbackInfoReturnable<List<BlockPos>> cir){if(ChunkSpeedrun.locked())cir.setReturnValue(cir.getReturnValue().stream().filter(ChunkSpeedrun.run::inside).collect(java.util.stream.Collectors.toCollection(ArrayList::new)));}
}
