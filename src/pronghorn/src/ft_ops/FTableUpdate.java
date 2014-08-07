package pronghorn.ft_ops;

import org.openflow.protocol.OFFlowMod;

public class FTableUpdate
{
    final private String src_ip;
    final private String dst_ip;
    final private String actions;
    /**
       true if this is an insertion flow mod.  false if it's a
       deletion.
     */
    private boolean insertion;
    
    public static FTableUpdate create_insert_update(
        String src_ip, String dst_ip, String actions)
    {
        FTableUpdate to_return = new FTableUpdate(
            src_ip,dst_ip,actions,true);
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
        String src_ip, String dst_ip, String actions)
    {
        FTableUpdate to_return = new FTableUpdate(
            src_ip,dst_ip,actions,false);
        return to_return;
    }


    /**
       Takes update and generates a flow mod out of it, which
       floodlight sends to switch.
     */
    public OFFlowMod to_flow_mod(int xid)
    {
        /**
           TODO: fill in stub method
         */
        assert(false);
        return null;
    }
    
    /**
       Takes current flow mod instruction and creates an FTableUpdate
       that is the opposite of it.  If we run an FTableUpdate and then
       its update, we should be in the state that we were before
       running either.
     */
    public FTableUpdate create_undo()
    {
        /**
           TODO: fill in stub method as part of update to OF 1.3.
         */
        assert(false);
        return null;
    }
    
    /**
       Private constructor: use static methods to construct insertion
       and remove updates.
     */
    private FTableUpdate(
        String _src_ip,String _dst_ip, String _actions,boolean _insertion)
    {
        src_ip = _src_ip;
        dst_ip = _dst_ip;
        actions = _actions;
        insertion = _insertion;
    }
}