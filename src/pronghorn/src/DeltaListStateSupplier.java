package pronghorn;

import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import RalphExtended.IHardwareStateSupplier;
import ralph.ActiveEvent;
import ralph.RalphObject;
import ralph.AtomicObject;
import ralph.AtomicInternalList;
import ralph.Variables.AtomicListVariable;

import pronghorn.SwitchDeltaJava._InternalFlowTableDelta;
import pronghorn.SwitchDeltaJava._InternalSwitchDelta;
import pronghorn.FTable._InternalFlowTableEntry;


/**
   When InternalPronghornSwitchGuard is ready to commit, it uses this
   class to grab deltas that should be pushed to hardware.
 */
public class DeltaListStateSupplier
    implements IHardwareStateSupplier<List<FTableUpdate>>
{
    protected static final Logger log =
        LoggerFactory.getLogger(DeltaListStateSupplier.class);

    private final _InternalSwitchDelta switch_delta;


    public DeltaListStateSupplier(_InternalSwitchDelta _switch_delta)
    {
        switch_delta = _switch_delta;
    }

    @Override
    public List<FTableUpdate> get_state_to_push(ActiveEvent active_event)
    {
        // FIXME: ensure that this is a safe access.
        AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
            internal_ft_deltas_list = get_internal_ft_deltas_list();

        // FIXME: Ensure that this is also a safe access.
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>> internal_list = 
            internal_ft_deltas_list.dirty_val.val;

        List<FTableUpdate> to_return = produce_ftable_updates(internal_list);

        
        // FIXME: REALLY, REALLY shouldn't have to do this here.  only
        // require it because speculate is the only place where we
        // reset deltas to be empty.  We will not reset it if
        // speculation is turned off.  Need a way to ensure that we
        // reset val.  See Issue #10 and comment in speculate of
        // SwitchSpeculateListener.
        internal_ft_deltas_list.force_speculate(
            active_event,
            // so that resets delta list
            new ArrayList<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>(),
            // forces update on internal val
            true);

        return to_return;
    }
    
    private AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
        get_internal_ft_deltas_list()
    {
        // these accesses are safe, because we assume the invariant
        // that will only receive changes on PronghornSwitchGuard
        // if no other event is writing to them.

        // grabbing ft_deltas to actually get changes made to hardware.
        AtomicListVariable<_InternalFlowTableDelta,_InternalFlowTableDelta>
            ft_deltas_list = switch_delta.ft_deltas;
        AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
            internal_ft_deltas_list = null;

        if (ft_deltas_list.dirty_val != null)
            internal_ft_deltas_list = ft_deltas_list.dirty_val.val;
        else
            internal_ft_deltas_list = ft_deltas_list.val.val;

        return internal_ft_deltas_list;
    }


    /**
       @param {boolean} undo --- true if should actually try to
       undo the changes in dirty, rather than apply them.  Note: if
       reverse is true, this means that we must go backwards through
       the list.
     */
    private List<FTableUpdate> produce_ftable_updates(
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>> dirty)
    {
        List<FTableUpdate> floodlight_updates =
            new ArrayList<FTableUpdate>();
        
        for (RalphObject ro : dirty)
        {
            _InternalFlowTableDelta flow_table_delta = null;
            _InternalFlowTableEntry entry = null;
            try
            {
                flow_table_delta = (_InternalFlowTableDelta) (ro.get_val(null));
                if (flow_table_delta.entry.dirty_val != null)
                    entry = flow_table_delta.entry.dirty_val.val;
                else
                    entry = flow_table_delta.entry.val.val;
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
            String src_ip = null;
            if (entry.src_ip.dirty_val != null)
                src_ip = entry.src_ip.dirty_val.val;
            else
                src_ip = entry.src_ip.val.val;
            
            String dst_ip = null;
            if (entry.dst_ip.dirty_val != null)
                dst_ip = entry.dst_ip.dirty_val.val;
            else
                dst_ip = entry.dst_ip.val.val;
            
            String actions = null;
            if (entry.action.dirty_val != null)
                actions = entry.action.dirty_val.val;
            else
                actions = entry.action.val.val;

            FTableUpdate update_to_push =  null;
            // FIXME: get rid of entry names in PronghonrFlowTableEntry
            String entry_name = "";
            
            if (insertion)
            {
                update_to_push = FTableUpdate.create_insert_update(
                    entry_name, src_ip, dst_ip,actions);
            }
            else
            {
                update_to_push = FTableUpdate.create_remove_update(
                    entry_name, src_ip, dst_ip,actions);
            }
            floodlight_updates.add(update_to_push);
        }
        return floodlight_updates;
    }
}
