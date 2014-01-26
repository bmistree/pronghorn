package pronghorn;

import pronghorn.RTable._InternalRoutingTableEntry;
import RalphDataWrappers.ListTypeDataWrapper;
import ralph.ExtendedVariables.ExtendedInternalAtomicList;
import pronghorn.RTable;
import RalphExceptions.BackoutException;
import ralph.ActiveEvent;
import java.util.concurrent.Future;
import pronghorn.RoutingTableToHardware.WrapApplyToHardware;
import java.util.concurrent.ExecutorService;

/**
   Each switch's routing table is represented as a list of routing
   table entries.  This class overrides the internal list of routing
   table entries and ensures that if there is a hardware error that we
   will not be able to reaccess the class's data.
 */
class InternalRoutingTableList
    extends ExtendedInternalAtomicList<
    _InternalRoutingTableEntry,_InternalRoutingTableEntry>
{
    /**
       By default, will only simulate pushing changes to hardware.
       (Ie., will always return message that says it did push changes
       to hardware without doing any work.)
     */
    private static final RoutingTableToHardware DEFAULT_ROUTING_TABLE_TO_HARDWARE =
        new RoutingTableToHardware();


    /**
       Keeps track of whether the hardware failed.  If it did, any
       code that tires to perform an operation on it will
       automatically backout.  Assumption is that some other handler
       will issue a callback to remove this routing table (and its
       associated switch struct) from a pronghorn instance.  That
       event will not call any methods on this list and therefore will
       not be backedout by it.
     */
    private boolean hardware_failed = false;
    
    private RoutingTableToHardware rtable_to_hardware_obj =
        DEFAULT_ROUTING_TABLE_TO_HARDWARE;

    /**
       We use a separate thread to actually push changes to hardware.
       This service allows us to schedule those pushes on separate
       threads.
     */
    private ExecutorService hardware_push_service = null;
    
    // For now, any change to the internal routing table just gets
    // accepted.
    public InternalRoutingTableList(ExecutorService _hardware_push_service)
    {
        super (RTable.STRUCT_LOCKED_MAP_WRAPPER__RoutingTableEntry);
        hardware_push_service = _hardware_push_service;
    }

    public InternalRoutingTableList(
        RoutingTableToHardware _rtable_to_hardware_obj,
        ExecutorService _hardware_push_service)
    {
        super (RTable.STRUCT_LOCKED_MAP_WRAPPER__RoutingTableEntry);
        rtable_to_hardware_obj = _rtable_to_hardware_obj;
        hardware_push_service = _hardware_push_service;
    }

    @Override
    protected Future<Boolean> apply_changes_to_hardware(
        ListTypeDataWrapper<
            _InternalRoutingTableEntry,_InternalRoutingTableEntry> dirty)
    {
        WrapApplyToHardware to_apply_to_hardware =
            new WrapApplyToHardware(rtable_to_hardware_obj,dirty);
        // request application to 
        hardware_push_service.execute(to_apply_to_hardware);
        return to_apply_to_hardware.to_notify_when_complete;
    }
    @Override
    protected void undo_dirty_changes_to_hardware(
        ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry> to_undo)
    {
        rtable_to_hardware_obj.undo_dirty_changes_to_hardware(to_undo);
    }


    /**
       @see discussion above hardware_failed private variable.
     */
    @Override
    protected
        ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry>
        acquire_read_lock(
            ActiveEvent active_event) throws BackoutException
    {
        // if hardware has failed, cannot operate on data anymore:
        // will backout event.  relies on another event that doesn't
        // actually operate on routing table list to remove all
        // references to it.  See discussion above in hardware_failed.
        if (hardware_failed)
            throw new BackoutException();

        return
            (ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry>)
            super.acquire_read_lock(active_event);
    }
    
    /**
       @see discussion above hardware_failed private variable.
     */
    @Override
    protected
        ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry>
        acquire_write_lock(
            ActiveEvent active_event) throws BackoutException
    {
        if (hardware_failed)
            throw new BackoutException();
        
        return
            (ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry>)
            super.acquire_write_lock(active_event);
    }
}