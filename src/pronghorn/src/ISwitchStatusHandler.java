package pronghorn;

import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;

public interface ISwitchStatusHandler
    extends IOFSwitchListener, ILinkDiscoveryListener
{}