package pronghorn.port_ops;

import java.util.List;

import RalphExtended.IHardwareChangeApplier;

/**
   Currently, changes to ports only come from events in hardware.
   Therefore, do not need to actually push them.
 */
public class PortsAvailableToHardware
    implements IHardwareChangeApplier<List<PortUpdate>>
{
    @Override
    public boolean apply(List<PortUpdate> to_apply)
    {
        return true;
    }

    @Override
    public boolean undo(List<PortUpdate> to_undo)
    {
        return true;
    }

    @Override
    public boolean partial_undo(List<PortUpdate> to_partially_undo) {
        return true;
    }
}