package pronghorn;

import java.util.List;
import pronghorn.SwitchDeltaJava._InternalFlowTableDelta;

import ralph.RalphObject;
import RalphExtended.IHardwareChangeApplier;

/**
   Implement this interface to override behavior of internal list when
   it is asked to push changes to hardware or undo pushed changes to
   hardware.

   Note: I originally wanted to turn FlowTableToHardware into an
   interface.  However, I think it might be a compile error to create
   an interface that implements another templatized interface???
 */
public abstract class FlowTableToHardware
    implements IHardwareChangeApplier<
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>>
{
    @Override
    public abstract boolean apply(
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>
        dirty);
    @Override    
    public abstract boolean undo(
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>
        to_undo);
}
