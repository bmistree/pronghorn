package pronghorn.ft_ops;

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
import pronghorn.MatchJava._InternalMatch;


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

        // We can get null here if the transaction has been backed
        // out.  In this case, it's okay to set internal_list to an
        // empty list, because we will undo/not apply its changes
        // anyways.

        // FIXME: ensure that this is a safe access.  May have to lock
        // internal_ft_deltas_list object first.
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>> internal_list =
            null;
        internal_ft_deltas_list._lock();
        if (internal_ft_deltas_list.dirty_val != null)
            internal_list = internal_ft_deltas_list.dirty_val.val;
        else
        {
            internal_list =
                new ArrayList<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>();
        }
        internal_ft_deltas_list._unlock();
        
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

        ft_deltas_list._lock();
        if (ft_deltas_list.dirty_val != null)
            internal_ft_deltas_list = ft_deltas_list.dirty_val.val;
        else
            internal_ft_deltas_list = ft_deltas_list.val.val;
        ft_deltas_list._unlock();
        
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
            _InternalMatch match = null;

            try
            {
                flow_table_delta = (_InternalFlowTableDelta) (ro.get_val(null));
                flow_table_delta.entry._lock();
                if (flow_table_delta.entry.dirty_val != null)
                    entry = flow_table_delta.entry.dirty_val.val;
                else
                    entry = flow_table_delta.entry.val.val;                
                flow_table_delta.entry._unlock();


                if (entry == null)
                {
                    // can get a null entry in cases where are currently
                    // backing out of an update (and therefore reverting
                    // to original flow_table_delta value).  In that case,
                    // we do not need to produce an update for this target
                    // (no need to push it to switch), and can stop
                    // producing other updates.
                    break;
                }
                
                entry.match._lock();
                if (entry.match.dirty_val != null)
                    match = entry.match.dirty_val.val;
                else
                    match = entry.match.val.val;
                entry.match._unlock();
                if (match == null)
                {
                    // see above note about entry.
                    break;
                }
                
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

            // FIXME: double check that match cannot change out from
            // under us during this process.
            String src_ip = null;
            match.src_ip._lock();
            if (match.src_ip.dirty_val != null)
                src_ip = match.src_ip.dirty_val.val;
            else
                src_ip = match.src_ip.val.val;
            match.src_ip._unlock();

            String dst_ip = null;
            match.dst_ip._lock();
            if (match.dst_ip.dirty_val != null)
                dst_ip = match.dst_ip.dirty_val.val;
            else
                dst_ip = match.dst_ip.val.val;
            match.dst_ip._unlock();
            
            String actions = null;
            entry.action._lock();
            if (entry.action.dirty_val != null)
                actions = entry.action.dirty_val.val;
            else
                actions = entry.action.val.val;
            entry.action._unlock();

            // means that this change was backed out before could
            // complete and src_ip or dst_ip was backed out and reset
            // to default text value.  do not apply change (it will be
            // backed out anyways).
            if (dst_ip.equals("") || src_ip.equals(""))
                break;

            
            FTableUpdate update_to_push =  null;
            if (insertion)
            {
                update_to_push = FTableUpdate.create_insert_update(
                    src_ip, dst_ip,actions);
            }
            else
            {
                update_to_push = FTableUpdate.create_remove_update(
                    src_ip, dst_ip,actions);
            }
            floodlight_updates.add(update_to_push);
        }
        return floodlight_updates;
    }
}
