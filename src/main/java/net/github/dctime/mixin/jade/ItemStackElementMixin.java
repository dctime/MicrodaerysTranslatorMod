package net.github.dctime.mixin.jade;

import net.github.dctime.libs.jade.IGetItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import snownee.jade.api.ui.Element;
import snownee.jade.impl.ui.ItemStackElement;

@Mixin(ItemStackElement.class)
public abstract class ItemStackElementMixin extends Element implements IGetItem {
    @Shadow @Final
    private ItemStack stack;

    @Override
    public ItemStack getItem() {
        return stack;
    }
}
