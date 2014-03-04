package pronghorn;


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

import pronghorn.SwitchDeltaJava._InternalFlowTableDelta;
import pronghorn.FTable._InternalFlowTableEntry;


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
        implements FlowTableToHardwareFactory
    {
        @Override
        public FlowTableToHardware construct(
            ShimInterface shim, String internal_switch_id)
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

    private final ShimInterface shim;
    private final String floodlight_switch_id;

    protected FloodlightFlowTableToHardware(
        ShimInterface _shim, String _floodlight_switch_id)
    {
        shim = _shim;
        floodlight_switch_id = _floodlight_switch_id;
    }

    private String new_unique_entry_name ()
    {
        int prev = ++unique_entry_name_generator;
        return floodlight_switch_id + ":_:" + Integer.toString(prev);
    }

    
    /**
       @param {boolean} undo --- true if should actually try to
       undo the changes in dirty, rather than apply them.  Note: if
       reverse is true, this means that we must go backwards through
       the list.
     */
    private List<FTableUpdate> produce_ftable_updates(
        ListTypeDataWrapper<
            _InternalFlowTableDelta,_InternalFlowTableDelta> dirty,
        boolean undo)
    {
        List<FTableUpdate> floodlight_updates = new ArrayList<FTableUpdate>();
        
        for (RalphObject ro : dirty.val)
        {
            _InternalFlowTableDelta flow_table_delta = null;
            _InternalFlowTableEntry entry = null;
            try
            {
                flow_table_delta = (_InternalFlowTableDelta) (ro.get_val(null));
                entry = flow_table_delta.entry.dirty_val.val;
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                log.error(
                    "Should always be able to cast to InternalFlowTableDelta",
                    ex.toString());
                assert(false);
            }

            // non tvar, therefore has different val.val access pattern.
            boolean insertion =
                flow_table_delta.inserted.val.val.booleanValue();
            String src_ip = entry.src_ip.dirty_val.val;
            String dst_ip = entry.dst_ip.dirty_val.val;
            String actions = entry.action.dirty_val.val;

            FTableUpdate update_to_push =  null;
            // FIXME: get rid of entry names in PronghonrFlowTableEntry
            String entry_name = "";
            
            if ((insertion && (! undo)) ||
                (undo && (! insertion)))
            {
                update_to_push = FTableUpdate.create_insert_update(
                    entry_name, src_ip, dst_ip,actions);
            }
            else
            {
                update_to_push = FTableUpdate.create_remove_update(
                    entry_name, src_ip, dst_ip);
            }
            floodlight_updates.add(update_to_push);
        }
        return floodlight_updates;
    }
    
    
    @Override
    protected boolean apply_changes_to_hardware(
        ListTypeDataWrapper<
            _InternalFlowTableDelta,_InternalFlowTableDelta> dirty)
    {
        List<FTableUpdate> floodlight_updates =
            produce_ftable_updates(dirty,false);
        // request shim to push the changes to swithces.
        return shim.switch_rtable_updates(
            floodlight_switch_id,floodlight_updates);
    }

    
    @Override
    protected void undo_dirty_changes_to_hardware(
        ListTypeDataWrapper<_InternalFlowTableDelta,_InternalFlowTableDelta>
        to_undo)
    {
        List<FTableUpdate> floodlight_updates =
            produce_ftable_updates(to_undo,true);
        // FIXME: should actually return boolean;
        shim.switch_rtable_updates(
            floodlight_switch_id,floodlight_updates);
    }
}
