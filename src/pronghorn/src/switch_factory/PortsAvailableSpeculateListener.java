package pronghorn.switch_factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import RalphExtended.ISpeculateListener;
import ralph.ActiveEvent;

/**
   FIXME: currently do not speculate on port changes.
 */
public class PortsAvailableSpeculateListener implements ISpeculateListener
{
    public PortsAvailableSpeculateListener()
    {}

    @Override
    public void speculate(ActiveEvent active_event)
    {
    }
}