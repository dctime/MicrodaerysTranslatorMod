package net.github.dctime.compability.jade;

import mcp.mobius.waila.api.ICommonAccessor;
import mcp.mobius.waila.api.event.WailaRenderEvent;
import mcp.mobius.waila.api.event.WailaTooltipEvent;
import net.github.dctime.Config;
import net.github.dctime.GoogleAIStudioTranslatorClient;
import net.github.dctime.libs.TooltipRelated;
import net.github.dctime.libs.Translator;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.List;

//@Mod.EventBusSubscriber(modid = GoogleAIStudioTranslatorClient.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TestJadeRenderEvent {
//    @SubscribeEvent
//    public static void onJadeTooltip(WailaTooltipEvent event) {
//        List<ITextComponent> currentTip = event.getCurrentTip();
//        ICommonAccessor accessor = event.getAccessor();
//        TooltipRelated.translateListOfText(currentTip, accessor.getStack());
//        System.out.println("Jade render");
//        onTooltipCollected(tooltip, accessor);
//    }
}
