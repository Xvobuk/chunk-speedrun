package ru.chunkspeedrun.mixin;
import net.minecraft.commands.*;
import com.mojang.brigadier.*;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.chunkspeedrun.ChunkSpeedrun;
@Mixin(Commands.class)
public abstract class CommandsMixin {
 @Shadow @Final private CommandDispatcher<CommandSourceStack> dispatcher;
 @Inject(method="<init>",at=@At("RETURN")) private void register(Commands.CommandSelection selection,CommandBuildContext context,CallbackInfo ci){
  dispatcher.register(Commands.literal("reset").executes(c->ChunkSpeedrun.reset(c.getSource().getPlayerOrException())));
  dispatcher.register(Commands.literal("leaderboard").executes(c->{ChunkSpeedrun.refresh();c.getSource().sendSuccess(()->Component.literal("Обновление таблицы…"),false);return 1;}));
 }
 @Inject(method="performCommand",at=@At("HEAD"),cancellable=true) private void guard(ParseResults<CommandSourceStack> parse,String command,CallbackInfo ci){
  String root=Commands.trimOptionalPrefix(command).strip().split("\\s+",2)[0];
  if(ChunkSpeedrun.locked()&&!java.util.Set.of("reset","leaderboard","help","msg","tell","w").contains(root)){
   parse.getContext().getSource().sendFailure(Component.literal("Во время челленджа команды изменения мира запрещены. /reset — новая попытка."));ci.cancel();
  }
 }
}
