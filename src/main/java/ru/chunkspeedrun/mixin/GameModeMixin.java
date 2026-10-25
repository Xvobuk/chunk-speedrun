package ru.chunkspeedrun.mixin;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.chunkspeedrun.ChunkSpeedrun;
@Mixin(ServerPlayerGameMode.class)
public abstract class GameModeMixin {
 @Shadow @Final protected ServerPlayer player;
 @Inject(method="destroyBlock",at=@At("HEAD"),cancellable=true) private void destroy(BlockPos pos,CallbackInfoReturnable<Boolean> cir){
  if(ChunkSpeedrun.locked()&&!ChunkSpeedrun.run.inside(pos)){cir.setReturnValue(false);return;}ChunkSpeedrun.signalInput();
 }
 @Inject(method="useItem",at=@At("HEAD")) private void use(ServerPlayer p,Level l,ItemStack stack,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir){ChunkSpeedrun.signalInput();}
 @Inject(method="useItemOn",at=@At("HEAD"),cancellable=true) private void useOn(ServerPlayer p,Level l,ItemStack stack,InteractionHand hand,BlockHitResult hit,CallbackInfoReturnable<InteractionResult> cir){
  if(ChunkSpeedrun.locked()&&!ChunkSpeedrun.run.inside(hit.getBlockPos())){cir.setReturnValue(InteractionResult.FAIL);return;}ChunkSpeedrun.signalInput();
 }
 @Inject(method="changeGameModeForPlayer",at=@At("HEAD"),cancellable=true) private void mode(GameType type,CallbackInfoReturnable<Boolean> cir){if(ChunkSpeedrun.locked()&&type!=GameType.SURVIVAL)cir.setReturnValue(false);}
}
