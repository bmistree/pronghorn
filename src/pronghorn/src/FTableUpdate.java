package pronghorn;

import net.floodlightcontroller.pronghornmodule.PronghornFlowTableEntry;
import net.floodlightcontroller.pronghornmodule.PronghornFlowTableEntry.Operation;


public class FTableUpdate
{
    public PronghornFlowTableEntry entry = null;
    private String previous_actions_removing;
    
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

    /**
       @param {String} actions --- Although sending a flow mod remove
       to the controller does not require having any actions, if we
       need to undo this removal (ie, re-add a flow mod), we must know
       the actions that this re-added flow_mod should allow.  That is
       why we have the additional parameter below.
     */
    public static FTableUpdate create_remove_update(
        String _entry_name,String src_ip, String dst_ip,
        String actions)
    {
        FTableUpdate to_return = new FTableUpdate();
        to_return.entry =
            new PronghornFlowTableEntry(Operation.REMOVE);
        to_return.entry.entry_name = _entry_name;
        to_return.entry.ip_src = src_ip;
        to_return.entry.ip_dst = dst_ip;
        to_return.entry.actions = null;

        to_return.previous_actions_removing = actions;
        return to_return;
    }

    /**
       Takes current flow mod instruction and creates an FTableUpdate
       that is the opposite of it.  If we run an FTableUpdate and then
       its update, we should be in the state that we were before
       running either.
     */
    public FTableUpdate create_undo()
    {
        if (entry.op == Operation.INSERT)
        {
            String actions_to_use = "";
            if (entry.actions != null)
                actions_to_use = entry.actions;
            return create_remove_update(
                entry.entry_name,entry.ip_src,entry.ip_dst,
                actions_to_use);
        }
        
        return create_insert_update(
            entry.entry_name,entry.ip_src,entry.ip_dst,
            previous_actions_removing);
    }
    
    /**
       Private constructor: use static methods to construct insertion
       and remove updates.
     */
    private FTableUpdate()
    {}
}