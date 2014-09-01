package pronghorn.ft_ops;

import java.util.List;

import com.google.protobuf.ByteString;

import RalphVersions.IDeviceSpecificUpdateSerializer;

import ft_ops.serialized_update.FTableUpdatesProto.FTableUpdates;

public class VersionUpdateSerializer
{
    public static FTableVersionUpdateSerializer FTABLE_SERIALIZER =
        new FTableVersionUpdateSerializer();
    
    private static class FTableVersionUpdateSerializer
        implements IDeviceSpecificUpdateSerializer<List<FTableUpdate>>
    {
        @Override
        public ByteString serialize(List<FTableUpdate> to_serialize)
        {
            FTableUpdates.Builder table_builder =
                FTableUpdate.serialize_update_list(to_serialize);
            
            FTableUpdates msg = table_builder.build();
            
            return ByteString.copyFrom(msg.toByteArray());
        }
    }
}