package pronghorn.ft_ops;


import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ralph.RalphObject;
import RalphExceptions.BackoutException;
import RalphDataWrappers.ListTypeDataWrapper;

import pronghorn.IFloodlightShim;

/**
   Subclass this object to override behavior of internal list when
   it is asked to push changes to hardware or undo pushed changes
   to hardware.
 */
public class FloodlightFlowTableToHardware extends FlowTableToHardware
{
    protected static final Logger log =
        LoggerFactory.getLogger(FloodlightFlowTableToHardware.class);
    
    private static class FloodlightFlowTableToHardwareFactory
        implements IFlowTableToHardwareFactory
    {
        @Override
        public FlowTableToHardware construct(
            IFloodlightShim shim, String internal_switch_id)
        {
            return new FloodlightFlowTableToHardware(
                shim,internal_switch_id);
        }
    }

    public final static
      FloodlightFlowTableToHardwareFactory FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY =
        new FloodlightFlowTableToHardwareFactory();
    
    
    /**
       Each flow table entry requires a unique name to associate it
       with flow table entries on actual switches.  This int can be
       used to generate these names.
     */
    private int unique_entry_name_generator = 0;
    
    /**
       Keeps track of the associated unique name of each flow table
       entry in each element of the list.
     */
    private final ArrayList<String> entry_names = new ArrayList<String>();

    private final IFloodlightShim shim;
    private final String floodlight_switch_id;

    protected FloodlightFlowTableToHardware(
        IFloodlightShim _shim, String _floodlight_switch_id)
    {
        shim = _shim;
        floodlight_switch_id = _floodlight_switch_id;
    }

    private String new_unique_entry_name ()
    {
        int prev = ++unique_entry_name_generator;
        return floodlight_switch_id + ":_:" + Integer.toString(prev);
    }

        
    @Override
    public boolean apply(List<FTableUpdate> to_apply)
    {
        // request shim to push the changes to swithces.
        return shim.switch_rtable_updates(
            floodlight_switch_id,to_apply);
    }


    /**
       @param {List<FTableUpdate>} to_undo --- The original operations
       that were pushed to the switch, which we must now undo.  (Ie.,
       must reverse operations in to_undo before applying to switch.)
     */
    @Override
    public boolean undo(List<FTableUpdate> to_undo)
    {
        List<FTableUpdate> floodlight_updates = 
            transform_from_to_undo(to_undo);
        return shim.switch_rtable_updates(
            floodlight_switch_id,floodlight_updates);
    }
}
