package pl.gabrys.autorynek.mixin;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(HandledScreen.class)
public interface HandledAccessor {
 @Accessor("focusedSlot") Slot autorynek$focusedSlot();
}
