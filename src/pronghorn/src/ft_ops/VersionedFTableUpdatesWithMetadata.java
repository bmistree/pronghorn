package pronghorn.ft_ops;

import java.util.List;
import com.google.protobuf.ByteString;

import ralph.CommitMetadata;

import ralph_version_protobuffs.SingleDeviceUpdateProto.SingleDeviceUpdateMessage;
import ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate;
import ft_ops.serialized_update.FTableUpdatesProto.FTableUpdates;

/**
   Contains an ftable udpate along with the type of update that was
   performed (stage, completed, backedout).
 */

public class VersionedFTableUpdatesWithMetadata
{
    final public List<FTableUpdate> ftable_updates;
    final public SingleDeviceUpdateMessage.UpdateType update_type;
    final public CommitMetadata commit_metadata;
    /**
       The lamport time of the remote server issuing version response
       that this update was processed.
     */
    final public Long remote_local_lamport_time;
    
    public VersionedFTableUpdatesWithMetadata(SingleDeviceUpdateMessage update_msg)
    {
        long root_commit_lamport_time = update_msg.getRootCommitLamportTime();
        String root_application_uuid =
            update_msg.getRootApplicationUuid().getData();
        String event_name = update_msg.getEventName();
        String event_uuid = update_msg.getEventUuid().getData();
        commit_metadata = new CommitMetadata(
            root_commit_lamport_time,root_application_uuid,
            event_name,event_uuid);
        
        remote_local_lamport_time = update_msg.getLocalLamportTime();
        
        update_type = update_msg.getUpdateType();

        if (update_msg.hasUpdateMsgData())
        {
            ftable_updates =
                produce_ftable_updates_from_update_msg(update_msg);
        }
        else
        {
            // just reference staged values from previous update.
            ftable_updates = null;
        }
    }

    private static List<FTableUpdate> produce_ftable_updates_from_update_msg(
        SingleDeviceUpdateMessage msg)
    {
        ByteString msg_data = msg.getUpdateMsgData();
        List<FTableUpdate> to_return = null;
        try
        {
            FTableUpdates ftable_updates_msg =
                FTableUpdates.parseFrom(msg_data);

            to_return = FTableUpdate.deserialize_update_list(
                ftable_updates_msg);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            assert(false);
            System.exit(-1);
        }
        return to_return;
    }
}