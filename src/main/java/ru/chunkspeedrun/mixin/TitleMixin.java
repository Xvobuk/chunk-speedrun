package ru.chunkspeedrun.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(TitleScreen.class)
public abstract class TitleMixin extends Screen {
 protected TitleMixin(){super(Component.empty());}
 @Inject(method="init",at=@At("TAIL")) private void button(CallbackInfo ci){
  addRenderableWidget(Button.builder(Component.literal("Чанк на скорость"),b->{
   var m=Minecraft.getInstance();String name="ChunkSpeedrun-"+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
   m.createWorldOpenFlows().createFreshLevel(name,new LevelSettings(name,GameType.SURVIVAL,new LevelSettings.DifficultySettings(Difficulty.HARD,false,true),false,WorldDataConfiguration.DEFAULT),WorldOptions.defaultWithRandomSeed().withBonusChest(false).withStructures(true),WorldPresets::createNormalWorldDimensions,m.gui.screen());
  }).bounds(8,8,180,20).build());
 }
}
