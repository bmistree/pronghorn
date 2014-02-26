package pronghorn;

import java.util.ArrayList;
import RalphDataWrappers.ListTypeDataWrapper;
import pronghorn.FTable._InternalFlowTableEntry;
import ralph.RalphObject;
import RalphExceptions.BackoutException;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;

/**
   Subclass this object to override behavior of internal list when
   it is asked to push changes to hardware or undo pushed changes
   to hardware.
 */
public class FloodlightFlowTableToHardware extends FlowTableToHardware
{
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
    private ArrayList<String> entry_names = new ArrayList<String>();

    private ShimInterface shim;
    private String floodlight_switch_id;

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
       @param {boolean} reverse --- true if should actually try to
       undo the changes in dirty, rather than apply them.  Note: if
       reverse is true, this means that we must go backwards through
       the list.
     */
    private ArrayList<FTableUpdate> produce_rtable_updates(
        ListTypeDataWrapper<
            _InternalFlowTableEntry,_InternalFlowTableEntry> dirty,
        boolean reverse)
    {
        /*
          We have a list of operations that were performed on a list and the
          final contents of the list.  We want to take these changes and
          push the relevant deltas to hardware.

          The challenge is if we have some set of operations like:

             add to index 0
             add to index 1
             remove from index 0

          Then, if we just run through the list of operations sequentially,
          when we get to the second operation (add to index 1), we do not
          know where the object will be stored in the final array list and
          therefore will not be able to read the changes that that operation
          makes to the flow table.

          Instead, we use a different algorithm:
          
            1) Run through all changes to flow table and

                a) update entry_names: a list that stores the flow table
                   entry name associated with each index in the flow
                   table list.  After 1, entry_names should be a list of
                   names for flow table entries that would be stored on
                   the switch after pushing the change.

                b) Map each added tuple to its entry name.  For each add
                   tuple, assign it a unique entry name and create a map
                   from add tuples to entry names.

             2) Run through the list of changes to the flow table again.
                Each time notice an added tuple:

                a) Check that the added tuple was not in the removed tuple.
                   If it was, then we had added an entry and then removed it:
                   do not need to push these changes to switch.

                b) Create an update for each added tuple that was not
                   removed.  First need to find the index of the associated
                   change in the ralph list.  Can do this by using map to
                   find add_tuple's associated unique name and then locating
                   the index of this unique name in the entry_name list.

             3) Add a remove update for each unique name that is still in
                entries_to_remove --- a set containing the names of all entries
                that were removed by this transaction (and not added by it also).
         */
        Set<String> entries_to_remove = new HashSet<String>();
        HashMap<ListTypeDataWrapper.OpTuple,String> added_tuples_to_hidden_names =
            new HashMap<ListTypeDataWrapper.OpTuple,String>();


        // Step 0 from above
        ArrayList<ListTypeDataWrapper.OpTuple> changes_to_iter_over =
            (ArrayList<ListTypeDataWrapper.OpTuple>)dirty.partner_change_log.clone();
        if (reverse)
            Collections.reverse(changes_to_iter_over);

        // Step 1 from above
        for (ListTypeDataWrapper.OpTuple op_tuple : changes_to_iter_over)
        {
            if (
                // we're going in the forward direction and it's a
                // delete key.
                (dirty.is_delete_key_tuple(op_tuple) && (! reverse)) ||
                // we're going in the reverse direction and it's not an
                // add key, which we must delete to undo
                (dirty.is_add_key_tuple(op_tuple) && (reverse)))
            {
                int deleted_key = op_tuple.key;
                // 1a from above
                String removed_entry_name = entry_names.remove(deleted_key);
                entries_to_remove.add(removed_entry_name);
            }
            else if (
                // we're going in the forward direction and it's an
                // add key
                (dirty.is_add_key_tuple(op_tuple) && (! reverse)) ||
                // we're undoing an add
                (dirty.is_delete_key_tuple(op_tuple) && (reverse)))
            {
                int index_added = op_tuple.key;
                // 1a from above
                String entry_name = new_unique_entry_name ();
                entry_names.add(index_added,entry_name);
                // 1b from above
                added_tuples_to_hidden_names.put(op_tuple,entry_name);
            }
        }

        // Step 2 from above
        ArrayList<FTableUpdate> floodlight_updates = new ArrayList<FTableUpdate>();
        for (ListTypeDataWrapper.OpTuple op_tuple : changes_to_iter_over)
        {
            if (
                (dirty.is_add_key_tuple(op_tuple) && (!reverse)) ||
                (dirty.is_delete_key_tuple(op_tuple) && (reverse)))
            {
                String entry_name = added_tuples_to_hidden_names.get(op_tuple);

                // Step 2a from above
                // check if we added the entry and then removed it: do not
                // need to push addition or removal to hardware.
                if (entries_to_remove.remove(entry_name))
                    continue;

                // Step 2b from above
                // entry was added and not deleted: get associated object
                // and create an update for it
                int added_entry_index = entry_names.indexOf(entry_name);

                RalphObject ro = op_tuple.what_added_or_removed;
                _InternalFlowTableEntry added_entry = null;
                try { 
                    added_entry = (_InternalFlowTableEntry) (ro.get_val(null));
                } catch (Exception _ex) {
                    _ex.printStackTrace();
                    System.out.println("\nShould never get to this point.");
                    assert(false);
                }
                    
                String src_ip = added_entry.src_ip.dirty_val.val;
                String dst_ip = added_entry.dst_ip.dirty_val.val;
                String action = added_entry.action.dirty_val.val;

                FTableUpdate update_to_push =  FTableUpdate.create_insert_update(
                    entry_name, src_ip, dst_ip,action);

                floodlight_updates.add(update_to_push);
            }
        }

        // Step 3 from above
        for (String removed_entry_name : entries_to_remove)
            floodlight_updates.add(
                FTableUpdate.create_remove_update(removed_entry_name));
        
        return floodlight_updates;
    }
    
    @Override
    public boolean apply_changes_to_hardware(
        ListTypeDataWrapper<
            _InternalFlowTableEntry,_InternalFlowTableEntry> dirty)
    {
        ArrayList<FTableUpdate> floodlight_updates =
            produce_rtable_updates(dirty,false);
        // request shim to push the changes to swithces.
        return shim.switch_rtable_updates(
            floodlight_switch_id,floodlight_updates);
    }
    
    @Override
    public void undo_dirty_changes_to_hardware(
        ListTypeDataWrapper<_InternalFlowTableEntry,_InternalFlowTableEntry>
        to_undo)
    {
        ArrayList<FTableUpdate> floodlight_updates =
            produce_rtable_updates(to_undo,true);
        // FIXME: should actually return boolean;
        shim.switch_rtable_updates(
            floodlight_switch_id,floodlight_updates);
    }
}
