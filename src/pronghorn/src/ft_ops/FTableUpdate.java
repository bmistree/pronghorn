package pronghorn.ft_ops;

import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionActions;
import org.openflow.protocol.instruction.OFInstructionWriteActions;

import net.floodlightcontroller.packet.IPv4;



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


    protected static final Logger log =
        LoggerFactory.getLogger(FTableUpdate.class);
    
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
        OFFlowMod to_return = new OFFlowMod();

        // decide whether to add or delete
        if (insertion)
            to_return.setCommand(OFFlowMod.OFPFC_ADD);
        else
            to_return.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);

        
        // handle matches
        OFMatch match = new OFMatch();
        if (src_ip != null)
            match.setNetworkSource(IPv4.toIPv4Address(src_ip));
        if (dst_ip != null)
            match.setNetworkDestination(IPv4.toIPv4Address(dst_ip));
        to_return.setMatch(match);
        
        // add operations for insertions
        if (insertion)
        {
            // FIXME: currently, solely allowing instructions for
            // actions, not for applying actions or jumping to
            // different tables, etc.
            List<OFAction> actions_list = string_action_to_actions_list();
            OFInstructionActions instruction_actions =
                new OFInstructionWriteActions(actions_list);
            instruction_actions.setActions(actions_list);

            List<OFInstruction> instructions = new ArrayList<OFInstruction>();
            to_return.setInstructions(instructions);
        }
        
        return to_return;
    }


    /**
       Takes actions string and returns a list of OFActions
       corresponding to those.
     */
    private List<OFAction> string_action_to_actions_list()
    {
        // FIXME: Currently, returning empty action.  This is because
        // I should ultimately write different types of actions in
        // ralph instead of just using a string.
        log.error(
            "Using empty set of actions in FTableUpdate.  " +
            "See FIXME near logging.");
        
        List<OFAction> to_return = new ArrayList<OFAction>();
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