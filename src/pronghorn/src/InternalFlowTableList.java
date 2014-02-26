package pronghorn;

import pronghorn.FTable;
import pronghorn.FTable._InternalFlowTableEntry;
import pronghorn.FlowTableToHardware.WrapApplyToHardware;

import RalphDataWrappers.ListTypeDataWrapper;
import ralph.ExtendedVariables.ExtendedInternalAtomicList;
import RalphExceptions.BackoutException;
import ralph.ActiveEvent;
import ralph.RalphGlobals;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
   Each switch's flow table is represented as a list of flow
   table entries.  This class overrides the internal list of flow
   table entries and ensures that if there is a hardware error that we
   will not be able to reaccess the class's data.
 */
class InternalFlowTableList
    extends ExtendedInternalAtomicList<
    _InternalFlowTableEntry,_InternalFlowTableEntry>
{
    /**
       By default, will only simulate pushing changes to hardware.
       (Ie., will always return message that says it did push changes
       to hardware without doing any work.)
     */
    private static final FlowTableToHardware DEFAULT_FLOW_TABLE_TO_HARDWARE =
        new FlowTableToHardware();


    /**
       Keeps track of whether the hardware failed.  If it did, any
       code that tires to perform an operation on it will
       automatically backout.  Assumption is that some other handler
       will issue a callback to remove this flow table (and its
       associated switch struct) from a pronghorn instance.  That
       event will not call any methods on this list and therefore will
       not be backedout by it.
     */
    private boolean hardware_failed = false;
    
    private FlowTableToHardware rtable_to_hardware_obj =
        DEFAULT_FLOW_TABLE_TO_HARDWARE;

    /**
       We use a separate thread to actually push changes to hardware.
       This service allows us to schedule those pushes on separate
       threads.
     */
    private ExecutorService hardware_push_service = null;
    
    // For now, any change to the internal flow table just gets
    // accepted.
    public InternalFlowTableList(
        ExecutorService _hardware_push_service, RalphGlobals ralph_globals)
    {
        super (
            FTable.STRUCT_LOCKED_MAP_WRAPPER__FlowTableEntry,ralph_globals);
        hardware_push_service = _hardware_push_service;
    }

    public InternalFlowTableList(
        FlowTableToHardware _rtable_to_hardware_obj,
        ExecutorService _hardware_push_service, RalphGlobals ralph_globals)
    {
        super (
            FTable.STRUCT_LOCKED_MAP_WRAPPER__FlowTableEntry,
            ralph_globals);
        rtable_to_hardware_obj = _rtable_to_hardware_obj;
        hardware_push_service = _hardware_push_service;
    }

    @Override
    protected Future<Boolean> apply_changes_to_hardware(
        ListTypeDataWrapper<
            _InternalFlowTableEntry,_InternalFlowTableEntry> dirty)
    {
        WrapApplyToHardware to_apply_to_hardware =
            new WrapApplyToHardware(rtable_to_hardware_obj,dirty);
        // request application to 
        hardware_push_service.execute(to_apply_to_hardware);
        return to_apply_to_hardware.to_notify_when_complete;
    }
    @Override
    protected void undo_dirty_changes_to_hardware(
        ListTypeDataWrapper<_InternalFlowTableEntry,_InternalFlowTableEntry> to_undo)
    {
        rtable_to_hardware_obj.undo_dirty_changes_to_hardware(to_undo);
    }


    /**
       @see discussion above hardware_failed private variable.
     */
    @Override
    protected
        ListTypeDataWrapper<_InternalFlowTableEntry,_InternalFlowTableEntry>
        acquire_read_lock(
            ActiveEvent active_event,ReentrantLock to_unlock) throws BackoutException
    {
        // if hardware has failed, cannot operate on data anymore:
        // will backout event.  relies on another event that doesn't
        // actually operate on flow table list to remove all
        // references to it.  See discussion above in hardware_failed.
        if (hardware_failed)
        {
            if (to_unlock != null)
                to_unlock.unlock();
            
            throw new BackoutException();
        }

        return
            (ListTypeDataWrapper<_InternalFlowTableEntry,_InternalFlowTableEntry>)
            super.acquire_read_lock(active_event,to_unlock);
    }

    
    /**
       @see discussion above hardware_failed private variable.
     */
    @Override
    protected
        ListTypeDataWrapper<_InternalFlowTableEntry,_InternalFlowTableEntry>
        acquire_write_lock(
            ActiveEvent active_event, ReentrantLock to_unlock)
            throws BackoutException
    {
        if (hardware_failed)
        {
            if (to_unlock != null)
                to_unlock.unlock();
            throw new BackoutException();
        }
        
        return
            (ListTypeDataWrapper<_InternalFlowTableEntry,_InternalFlowTableEntry>)
            super.acquire_write_lock(active_event,to_unlock);
    }
}