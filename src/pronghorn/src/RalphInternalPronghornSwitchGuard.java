package pronghorn;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ralph.RalphGlobals;
import ralph.RalphObject;
import ralph.Variables.AtomicNumberVariable;
import ralph.ActiveEvent;
import ralph.ICancellableFuture;
import ralph.SpeculativeFuture;

import RalphExtended.WrapApplyToHardware;
import RalphExtended.IHardwareChangeApplier;
import RalphExtended.IHardwareStateSupplier;
import RalphExtended.ISpeculateListener;
import RalphExtended.ExtendedHardwareOverrides;

import pronghorn.SwitchDeltaJava._InternalFlowTableDelta;


public class RalphInternalPronghornSwitchGuard extends AtomicNumberVariable
{
    private final ExtendedHardwareOverrides<
        ArrayList<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>>
        extended_hardware_overrides;

    public final String ralph_internal_switch_id;
    private final boolean should_speculate;

    protected static final Logger log =
        LoggerFactory.getLogger(RalphInternalPronghornSwitchGuard.class);
    
    
    public RalphInternalPronghornSwitchGuard(
        RalphGlobals ralph_globals,String _ralph_internal_switch_id,
        boolean _should_speculate,

        // initializes extended_hardware_overrides.  used to actually
        // push changes to switches.
        IHardwareChangeApplier<
            ArrayList<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>>
        hardware_applier,

        // initializes extended_hardware_overrides.  used to get flow
        // table deltas on switches
        IHardwareStateSupplier<
            ArrayList<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>>
        hardware_state_supplier,

        // initializes extended_hardware_overrides.  Using this call,
        // notifies switch guard, ftable_deltas, etc., that they can
        // speculate.
        ISpeculateListener speculate_listener)
    {
        super(false,new Double(0),ralph_globals);
        ralph_internal_switch_id = _ralph_internal_switch_id;

        should_speculate = _should_speculate;
        extended_hardware_overrides =
            new ExtendedHardwareOverrides<
                ArrayList<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>>(
                    hardware_applier,hardware_state_supplier,speculate_listener,
                    should_speculate,ralph_globals);
        extended_hardware_overrides.set_controlling_object(this);
    }


    @Override
    protected ICancellableFuture hardware_first_phase_commit_hook(
        ActiveEvent active_event)
    {
        return extended_hardware_overrides.hardware_first_phase_commit_hook(
            active_event);
    }

    @Override
    protected void hardware_complete_commit_hook(ActiveEvent active_event)
    {
        extended_hardware_overrides.hardware_complete_commit_hook(active_event);
    }            

    @Override
    protected void hardware_backout_hook(ActiveEvent active_event)
    {
        extended_hardware_overrides.hardware_backout_hook(active_event);
    }

    @Override
    protected boolean hardware_first_phase_commit_speculative_hook(
        SpeculativeFuture sf)
    {
        return extended_hardware_overrides.hardware_first_phase_commit_speculative_hook(sf);
    }
}