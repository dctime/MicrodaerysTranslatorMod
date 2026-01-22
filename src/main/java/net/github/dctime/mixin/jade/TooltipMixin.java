package net.github.dctime.mixin.jade;

import com.google.common.collect.Lists;
import mcp.mobius.waila.api.ITaggableList;
import mcp.mobius.waila.api.impl.TaggedTextComponent;
import mcp.mobius.waila.overlay.RayTracing;
import mcp.mobius.waila.overlay.Tooltip;
import net.github.dctime.libs.jade.IGetItem;
import net.github.dctime.libs.jade.ITooltipMixin;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(value = Tooltip.class, remap = false)
public abstract class TooltipMixin implements IGetItem, ITooltipMixin {
    @Shadow
    ItemStack identifierStack;

    @Shadow @Final @Mutable
    private List<Tooltip.Line> lines;
    @Shadow @Final @Mutable
    private Dimension totalSize;

    @Shadow
    public abstract boolean hasItem();

    public ItemStack getItem() {
        if (hasItem()) {
            if (identifierStack == null) {
                identifierStack = RayTracing.INSTANCE.getIdentifierStack();
            }
        }
        return identifierStack;
    }

    @Shadow
    protected abstract Dimension getLineSize(ITextComponent component, List<ITextComponent> components);

    public void computeLinesSelf() {
        List<ITextComponent> components = lines.stream()
                .map(line->line.getComponent()) // 或是寫成 a -> a.getB()
                .collect(Collectors.toList());
        this.totalSize.width = 0;
        this.totalSize.height = 0;
        lines.forEach((l) -> {
            ITextComponent c = l.getComponent();
            Dimension size = this.getLineSize(c, components);
            this.totalSize.setSize(Math.max(this.totalSize.width, size.width), this.totalSize.height + size.height);
//            ITextComponent component = c;
//            if (c instanceof TaggedTextComponent) {
//                component = (ITextComponent)((ITaggableList)components).getTag(((TaggedTextComponent)c).getTag());
//            }
        });
    }

    // computeLines add lines to tooltip
    // addPadding follow on
}
