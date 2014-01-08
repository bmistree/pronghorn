package pronghorn;

import java.util.ArrayList;
import RalphDataWrappers.ListTypeDataWrapper;
import pronghorn.RTable._InternalRoutingTableEntry;
import ralph.RalphObject;
import RalphExceptions.BackoutException;

/**
   Subclass this object to override behavior of internal list when
   it is asked to push changes to hardware or undo pushed changes
   to hardware.
 */
public class FloodlightRoutingTableToHardware
{
    /**
       Each routing table entry requires a unique name to associate it
       with routing table entries on actual switches.  This int can be
       used to generate these names.
     */
    private int unique_entry_name_generator = 0;
    
    /**
       Keeps track of the associated unique name of each routing table
       entry in each element of the list.
     */
    private ArrayList<String> entry_names = new ArrayList<String>();
    private ArrayList<String> prev_entry_names = null;

    private ShimInterface shim;
    private String floodlight_switch_id;
    
    public FloodlightRoutingTableToHardware(
        ShimInterface _shim, String _floodlight_switch_id)
    {
        shim = _shim;
    }

    public boolean apply_changes_to_hardware(
        ListTypeDataWrapper<
            _InternalRoutingTableEntry,_InternalRoutingTableEntry> dirty)
    {
        // backup previous version of entry names in case we need to
        // back out changes to hardware.
        prev_entry_names = new ArrayList(entry_names);
        
        ArrayList<RTableUpdate> floodlight_updates = new ArrayList<RTableUpdate>();

        ArrayList<RalphObject<_InternalRoutingTableEntry,_InternalRoutingTableEntry>>
            dirty_list = dirty.val;
        for (ListTypeDataWrapper.OpTuple op_tuple : dirty.partner_change_log)
        {
            if (dirty.is_delete_key_tuple(op_tuple))
            {
                int deleted_key = op_tuple.key;
                String removed_entry_name = entry_names.remove(deleted_key);
                floodlight_updates.add(
                    RTableUpdate.create_remove_update(removed_entry_name));
            }
            else if (dirty.is_add_key_tuple(op_tuple))
            {
                int index_added = op_tuple.key;
                // HUGE FIXME
                System.out.println(
                    "\nFIXME: index_added may not map to final dirty value of array\n");

                // when we call get, we get the wrapped version of the
                // object, not the object itself.
                RalphObject ro = dirty_list.get(index_added);
                
                _InternalRoutingTableEntry added_entry = null;

                try {
                    added_entry = (_InternalRoutingTableEntry)(ro.get_val(null));
                } catch (BackoutException _ex) {
                    _ex.printStackTrace();
                    System.out.println(
                        "\nERROR: should never throw backout " +
                        "exception when pushing to hardware.");
                    assert(false);
                }

                // HUGE FIXME
                System.out.println(
                    "\nFIXME: still need to translate additions to rtable updates");
            }
            else if (dirty.is_write_key(op_tuple))
            {
                // FIXME: not handling writes to keys: only handling
                // additions and removals to start with.
                System.out.println(
                    "FIXME: still must define rtable changes for writes.");
                assert(false);
            }
            // DEBUG
            else
            {
                // FIXME: change from system.out.println
                System.out.println("Unknown tuple type.");
                assert(false);
            }
            // END DEBUG
        }

        // force the shim to push the changes to swithces.
        return shim.switch_rtable_updates(
            floodlight_switch_id,floodlight_updates);
    }
    public void undo_dirty_changes_to_hardware(
        ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry>
        to_undo)
    {
        entry_names = prev_entry_names;
        System.out.println("FIXME: Still need undo changes made to routing table.");
    }
}
