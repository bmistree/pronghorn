package pronghorn.port_ops;

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

import pronghorn.SwitchDeltaJava._InternalSwitchDelta;
import pronghorn.SwitchDeltaJava._InternalPortDelta;
import pronghorn.ft_ops.RalphInternalValueRemover;
import pronghorn.PortJava._InternalPort;

public class PortDeltaStateSupplier
    implements IHardwareStateSupplier<List<PortUpdate>>
{
    protected static final Logger log =
        LoggerFactory.getLogger(PortDeltaStateSupplier.class);

    private final _InternalSwitchDelta switch_delta;
    
    public PortDeltaStateSupplier(_InternalSwitchDelta _switch_delta)
    {
        switch_delta = _switch_delta;
    }
    
    /**
       Returns empty list if get backout in middle.
     */
    @Override
    public List<PortUpdate> get_state_to_push(ActiveEvent active_event)
    {
        // FIXME: ensure that this is a safe access.
        AtomicInternalList<_InternalPortDelta,_InternalPortDelta>
            internal_port_deltas_list = get_internal_port_deltas_list();

        // We can get null here if the transaction has been backed
        // out.  In this case, it's okay to set internal_list to an
        // empty list, because we will undo/not apply its changes
        // anyways.

        // FIXME: ensure that this is a safe access.  May have to lock
        // internal_ft_deltas_list object first.
        List<RalphObject<_InternalPortDelta,_InternalPortDelta>> internal_list =
            null;
        internal_port_deltas_list._lock();
        if (internal_port_deltas_list.dirty_val != null)
            internal_list = internal_port_deltas_list.dirty_val.val;
        else
        {
            internal_list =
                new ArrayList<RalphObject<_InternalPortDelta,_InternalPortDelta>>();
        }
        internal_port_deltas_list._unlock();


        List<PortUpdate> to_return = produce_port_updates(internal_list);

        
        // FIXME: REALLY, REALLY shouldn't have to do this here.  only
        // require it because speculate is the only place where we
        // reset deltas to be empty.  We will not reset it if
        // speculation is turned off.  Need a way to ensure that we
        // reset val.  See Issue #10 and comment in speculate of
        // SwitchSpeculateListener.
        internal_port_deltas_list.force_speculate(
            active_event,
            // so that resets delta list
            new ArrayList<RalphObject<_InternalPortDelta,_InternalPortDelta>>(),
            // forces update on internal val
            true);

        return to_return;
    }

    private AtomicInternalList<_InternalPortDelta,_InternalPortDelta>
        get_internal_port_deltas_list()
    {
        // these accesses are safe, because we assume the invariant
        // that will only receive changes on PronghornSwitchGuard
        // if no other event is writing to them.

        // grabbing ft_deltas to actually get changes made to hardware.
        AtomicListVariable<_InternalPortDelta,_InternalPortDelta>
            port_deltas_list = switch_delta.port_deltas;
        AtomicInternalList<_InternalPortDelta,_InternalPortDelta>
            internal_port_deltas_list = null;

        internal_port_deltas_list =
            RalphInternalValueRemover.<
                AtomicInternalList<
                    _InternalPortDelta,_InternalPortDelta>,
                _InternalPortDelta>
            list_get_internal(port_deltas_list);
        return internal_port_deltas_list;
    }


    private List<PortUpdate> produce_port_updates(
        List<RalphObject<_InternalPortDelta,_InternalPortDelta>> dirty)
    {
        List<PortUpdate> port_updates =
            new ArrayList<PortUpdate>();
        
        for (RalphObject ro : dirty)
        {
            _InternalPortDelta internal_port_delta = null;
            try
            {
                internal_port_delta = (_InternalPortDelta) (ro.get_val(null));
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                log.error(
                    "Should always be able to cast to InternalFlowTableDelta",
                    ex.toString());
                assert(false);
            }

            PortUpdate port_update =
                PortUpdate.port_update_from_internal(
                    internal_port_delta);

            // means that got a backout while trying to translate.
            // should not produce any updates because everything gets
            // rolled back.
            if (port_update == null)
                return new ArrayList<PortUpdate>();

            port_updates.add(port_update);
        }


        return port_updates;
    }
}
