package ru.chunkspeedrun.mixin;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Display.class)
public interface DisplayAccess {
 @Invoker("setBillboardConstraints") void csr$billboard(Display.BillboardConstraints constraints);
 @Invoker("setViewRange") void csr$range(float range);
}
