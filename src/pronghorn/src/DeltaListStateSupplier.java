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

/**
   When InternalPronghornSwitchGuard is ready to commit, it uses this
   class to grab deltas that should be pushed to hardware.
 */
public class DeltaListStateSupplier
    implements IHardwareStateSupplier<
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>>
{
    protected static final Logger log =
        LoggerFactory.getLogger(DeltaListStateSupplier.class);

    private final _InternalSwitchDelta switch_delta;


    public DeltaListStateSupplier(_InternalSwitchDelta _switch_delta)
    {
        switch_delta = _switch_delta;
    }

    @Override
    public List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>
        get_state_to_push(ActiveEvent active_event)
    {
        // FIXME: ensure that this is a safe access.
        AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
            internal_ft_deltas_list = get_internal_ft_deltas_list();

        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>> to_return = 
            internal_ft_deltas_list.dirty_val.val;
        
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
}
    
                                 