package net.github.dctime.compability.jade;


import mcp.mobius.waila.api.IWailaClientRegistration;
import mcp.mobius.waila.api.IWailaCommonRegistration;
import mcp.mobius.waila.api.IWailaPlugin;

@mcp.mobius.waila.api.WailaPlugin
public class WailaPlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        //TODO register data providers
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
//        registration.addBeforeRenderCallback(new TestBeforeRenderCallback());
//        registration.addTooltipCollectedCallback(new TestTooltipCollectedCallback());
    }
}
