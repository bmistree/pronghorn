package pronghorn;

import java.util.List;
import java.util.ArrayList;
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
    implements IHardwareChangeApplier<List<FTableUpdate>>
{
    @Override
    public abstract boolean apply(List<FTableUpdate> to_apply);

    /**
       @param {List<FTableUpdate>} to_undo --- This should hold the
       same contents as the list provided in to_apply.  Classes that
       implement this interface should reverse the calls themselves
       when they are undo-ing.
     */
    @Override    
    public abstract boolean undo(List<FTableUpdate> to_undo);


    final protected List<FTableUpdate> transform_from_to_undo(
        List<FTableUpdate> to_undo)
    {
        List<FTableUpdate> to_return = new ArrayList<FTableUpdate>();
        for (FTableUpdate ftab_update : to_undo)
            to_return.add(ftab_update.create_undo());
        return to_return;
    }
}
