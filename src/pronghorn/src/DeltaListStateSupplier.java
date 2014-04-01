package pronghorn;

import java.util.List;

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
        return internal_ft_deltas_list.dirty_val.val;
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
    
                                 