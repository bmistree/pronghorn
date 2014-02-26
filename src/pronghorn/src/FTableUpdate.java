package pronghorn;

import net.floodlightcontroller.pronghornmodule.PronghornFlowTableEntry;
import net.floodlightcontroller.pronghornmodule.PronghornFlowTableEntry.Operation;


public class FTableUpdate
{
    public PronghornFlowTableEntry entry = null;
    
    public static FTableUpdate create_insert_update(
        String entry_name, String src_ip, String dst_ip,
        String actions)
    {
        FTableUpdate to_return = new FTableUpdate();
        to_return.entry =
            new PronghornFlowTableEntry(Operation.INSERT);
        to_return.entry.entry_name = entry_name;
        to_return.entry.active = true;
        to_return.entry.ip_src = src_ip;
        to_return.entry.ip_dst = dst_ip;

        if (actions.equals(""))
            to_return.entry.actions = null;
        else
            to_return.entry.actions = actions;
        
        return to_return;
    }
    
    public static FTableUpdate create_remove_update(String _entry_name)
    {
        FTableUpdate to_return = new FTableUpdate();
        to_return.entry =
            new PronghornFlowTableEntry(Operation.REMOVE);
        to_return.entry.entry_name = _entry_name;
        return to_return;
    }
    
    /**
       Private constructor: use static methods to construct insertion
       and remove updates.
     */
    private FTableUpdate()
    {}
}