package pronghorn;

import pronghorn.RTable._InternalRoutingTableEntry;
import RalphDataWrappers.ListTypeDataWrapper;
import ralph.ExtendedVariables.ExtendedInternalAtomicList;
import pronghorn.RTable;


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

    
    private RoutingTableToHardware rtable_to_hardware_obj =
        DEFAULT_ROUTING_TABLE_TO_HARDWARE;

    // For now, any change to the internal routing table just gets
    // accepted.
    public InternalRoutingTableList()
    {
        super (RTable.STRUCT_LOCKED_MAP_WRAPPER__RoutingTableEntry);
    }

    public InternalRoutingTableList(
        RoutingTableToHardware _rtable_to_hardware_obj)
    {
        super (RTable.STRUCT_LOCKED_MAP_WRAPPER__RoutingTableEntry);
        rtable_to_hardware_obj = _rtable_to_hardware_obj;
    }

    protected boolean apply_changes_to_hardware(
        ListTypeDataWrapper<
            _InternalRoutingTableEntry,_InternalRoutingTableEntry> dirty)
    {
        return rtable_to_hardware_obj.apply_changes_to_hardware(dirty);
    }
    protected void undo_dirty_changes_to_hardware(
        ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry> to_undo)
    {
        rtable_to_hardware_obj.undo_dirty_changes_to_hardware(to_undo);
    }
}