// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: singleFTableUpdate.proto

package ft_ops.serialized_update;

public final class SingleFTableUpdateProto {
  private SingleFTableUpdateProto() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
  }
  public interface SingleFTableUpdateOrBuilder
      extends com.google.protobuf.MessageOrBuilder {

    // required bool insertion = 1;
    /**
     * <code>required bool insertion = 1;</code>
     *
     * <pre>
     * whether or not this is an insertion or a deletion
     * </pre>
     */
    boolean hasInsertion();
    /**
     * <code>required bool insertion = 1;</code>
     *
     * <pre>
     * whether or not this is an insertion or a deletion
     * </pre>
     */
    boolean getInsertion();

    // optional bytes of_match = 2;
    /**
     * <code>optional bytes of_match = 2;</code>
     *
     * <pre>
     * Generated from the writeTo method of OFMatch.  Regenerate
     * OFMatch using OFMatch.readFrom.
     * </pre>
     */
    boolean hasOfMatch();
    /**
     * <code>optional bytes of_match = 2;</code>
     *
     * <pre>
     * Generated from the writeTo method of OFMatch.  Regenerate
     * OFMatch using OFMatch.readFrom.
     * </pre>
     */
    com.google.protobuf.ByteString getOfMatch();

    // repeated bytes of_instructions = 3;
    /**
     * <code>repeated bytes of_instructions = 3;</code>
     *
     * <pre>
     * Generated from the writeTo method of OFInstruction.  Regenerate
     * each instruction separately using OFInstruction.readFrom.
     * </pre>
     */
    java.util.List<com.google.protobuf.ByteString> getOfInstructionsList();
    /**
     * <code>repeated bytes of_instructions = 3;</code>
     *
     * <pre>
     * Generated from the writeTo method of OFInstruction.  Regenerate
     * each instruction separately using OFInstruction.readFrom.
     * </pre>
     */
    int getOfInstructionsCount();
    /**
     * <code>repeated bytes of_instructions = 3;</code>
     *
     * <pre>
     * Generated from the writeTo method of OFInstruction.  Regenerate
     * each instruction separately using OFInstruction.readFrom.
     * </pre>
     */
    com.google.protobuf.ByteString getOfInstructions(int index);
  }
  /**
   * Protobuf type {@code SingleFTableUpdate}
   */
  public static final class SingleFTableUpdate extends
      com.google.protobuf.GeneratedMessage
      implements SingleFTableUpdateOrBuilder {
    // Use SingleFTableUpdate.newBuilder() to construct.
    private SingleFTableUpdate(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
      super(builder);
      this.unknownFields = builder.getUnknownFields();
    }
    private SingleFTableUpdate(boolean noInit) { this.unknownFields = com.google.protobuf.UnknownFieldSet.getDefaultInstance(); }

    private static final SingleFTableUpdate defaultInstance;
    public static SingleFTableUpdate getDefaultInstance() {
      return defaultInstance;
    }

    public SingleFTableUpdate getDefaultInstanceForType() {
      return defaultInstance;
    }

    private final com.google.protobuf.UnknownFieldSet unknownFields;
    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
        getUnknownFields() {
      return this.unknownFields;
    }
    private SingleFTableUpdate(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      initFields();
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            default: {
              if (!parseUnknownField(input, unknownFields,
                                     extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
            case 8: {
              bitField0_ |= 0x00000001;
              insertion_ = input.readBool();
              break;
            }
            case 18: {
              bitField0_ |= 0x00000002;
              ofMatch_ = input.readBytes();
              break;
            }
            case 26: {
              if (!((mutable_bitField0_ & 0x00000004) == 0x00000004)) {
                ofInstructions_ = new java.util.ArrayList<com.google.protobuf.ByteString>();
                mutable_bitField0_ |= 0x00000004;
              }
              ofInstructions_.add(input.readBytes());
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e.getMessage()).setUnfinishedMessage(this);
      } finally {
        if (((mutable_bitField0_ & 0x00000004) == 0x00000004)) {
          ofInstructions_ = java.util.Collections.unmodifiableList(ofInstructions_);
        }
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return ft_ops.serialized_update.SingleFTableUpdateProto.internal_static_SingleFTableUpdate_descriptor;
    }

    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return ft_ops.serialized_update.SingleFTableUpdateProto.internal_static_SingleFTableUpdate_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate.class, ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate.Builder.class);
    }

    public static com.google.protobuf.Parser<SingleFTableUpdate> PARSER =
        new com.google.protobuf.AbstractParser<SingleFTableUpdate>() {
      public SingleFTableUpdate parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new SingleFTableUpdate(input, extensionRegistry);
      }
    };

    @java.lang.Override
    public com.google.protobuf.Parser<SingleFTableUpdate> getParserForType() {
      return PARSER;
    }

    private int bitField0_;
    // required bool insertion = 1;
    public static final int INSERTION_FIELD_NUMBER = 1;
    private boolean insertion_;
    /**
     * <code>required bool insertion = 1;</code>
     *
     * <pre>
     * whether or not this is an insertion or a deletion
     * </pre>
     */
    public boolean hasInsertion() {
      return ((bitField0_ & 0x00000001) == 0x00000001);
    }
    /**
     * <code>required bool insertion = 1;</code>
     *
     * <pre>
     * whether or not this is an insertion or a deletion
     * </pre>
     */
    public boolean getInsertion() {
      return insertion_;
    }

    // optional bytes of_match = 2;
    public static final int OF_MATCH_FIELD_NUMBER = 2;
    private com.google.protobuf.ByteString ofMatch_;
    /**
     * <code>optional bytes of_match = 2;</code>
     *
     * <pre>
     * Generated from the writeTo method of OFMatch.  Regenerate
     * OFMatch using OFMatch.readFrom.
     * </pre>
     */
    public boolean hasOfMatch() {
      return ((bitField0_ & 0x00000002) == 0x00000002);
    }
    /**
     * <code>optional bytes of_match = 2;</code>
     *
     * <pre>
     * Generated from the writeTo method of OFMatch.  Regenerate
     * OFMatch using OFMatch.readFrom.
     * </pre>
     */
    public com.google.protobuf.ByteString getOfMatch() {
      return ofMatch_;
    }

    // repeated bytes of_instructions = 3;
    public static final int OF_INSTRUCTIONS_FIELD_NUMBER = 3;
    private java.util.List<com.google.protobuf.ByteString> ofInstructions_;
    /**
     * <code>repeated bytes of_instructions = 3;</code>
     *
     * <pre>
     * Generated from the writeTo method of OFInstruction.  Regenerate
     * each instruction separately using OFInstruction.readFrom.
     * </pre>
     */
    public java.util.List<com.google.protobuf.ByteString>
        getOfInstructionsList() {
      return ofInstructions_;
    }
    /**
     * <code>repeated bytes of_instructions = 3;</code>
     *
     * <pre>
     * Generated from the writeTo method of OFInstruction.  Regenerate
     * each instruction separately using OFInstruction.readFrom.
     * </pre>
     */
    public int getOfInstructionsCount() {
      return ofInstructions_.size();
    }
    /**
     * <code>repeated bytes of_instructions = 3;</code>
     *
     * <pre>
     * Generated from the writeTo method of OFInstruction.  Regenerate
     * each instruction separately using OFInstruction.readFrom.
     * </pre>
     */
    public com.google.protobuf.ByteString getOfInstructions(int index) {
      return ofInstructions_.get(index);
    }

    private void initFields() {
      insertion_ = false;
      ofMatch_ = com.google.protobuf.ByteString.EMPTY;
      ofInstructions_ = java.util.Collections.emptyList();
    }
    private byte memoizedIsInitialized = -1;
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized != -1) return isInitialized == 1;

      if (!hasInsertion()) {
        memoizedIsInitialized = 0;
        return false;
      }
      memoizedIsInitialized = 1;
      return true;
    }

    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      if (((bitField0_ & 0x00000001) == 0x00000001)) {
        output.writeBool(1, insertion_);
      }
      if (((bitField0_ & 0x00000002) == 0x00000002)) {
        output.writeBytes(2, ofMatch_);
      }
      for (int i = 0; i < ofInstructions_.size(); i++) {
        output.writeBytes(3, ofInstructions_.get(i));
      }
      getUnknownFields().writeTo(output);
    }

    private int memoizedSerializedSize = -1;
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;

      size = 0;
      if (((bitField0_ & 0x00000001) == 0x00000001)) {
        size += com.google.protobuf.CodedOutputStream
          .computeBoolSize(1, insertion_);
      }
      if (((bitField0_ & 0x00000002) == 0x00000002)) {
        size += com.google.protobuf.CodedOutputStream
          .computeBytesSize(2, ofMatch_);
      }
      {
        int dataSize = 0;
        for (int i = 0; i < ofInstructions_.size(); i++) {
          dataSize += com.google.protobuf.CodedOutputStream
            .computeBytesSizeNoTag(ofInstructions_.get(i));
        }
        size += dataSize;
        size += 1 * getOfInstructionsList().size();
      }
      size += getUnknownFields().getSerializedSize();
      memoizedSerializedSize = size;
      return size;
    }

    private static final long serialVersionUID = 0L;
    @java.lang.Override
    protected java.lang.Object writeReplace()
        throws java.io.ObjectStreamException {
      return super.writeReplace();
    }

    public static ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }
    public static ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input);
    }
    public static ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input, extensionRegistry);
    }
    public static ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }

    public static Builder newBuilder() { return Builder.create(); }
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    public Builder toBuilder() { return newBuilder(this); }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessage.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code SingleFTableUpdate}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessage.Builder<Builder>
       implements ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdateOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return ft_ops.serialized_update.SingleFTableUpdateProto.internal_static_SingleFTableUpdate_descriptor;
      }

      protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return ft_ops.serialized_update.SingleFTableUpdateProto.internal_static_SingleFTableUpdate_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate.class, ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate.Builder.class);
      }

      // Construct using ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessage.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessage.alwaysUseFieldBuilders) {
        }
      }
      private static Builder create() {
        return new Builder();
      }

      public Builder clear() {
        super.clear();
        insertion_ = false;
        bitField0_ = (bitField0_ & ~0x00000001);
        ofMatch_ = com.google.protobuf.ByteString.EMPTY;
        bitField0_ = (bitField0_ & ~0x00000002);
        ofInstructions_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000004);
        return this;
      }

      public Builder clone() {
        return create().mergeFrom(buildPartial());
      }

      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return ft_ops.serialized_update.SingleFTableUpdateProto.internal_static_SingleFTableUpdate_descriptor;
      }

      public ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate getDefaultInstanceForType() {
        return ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate.getDefaultInstance();
      }

      public ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate build() {
        ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      public ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate buildPartial() {
        ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate result = new ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate(this);
        int from_bitField0_ = bitField0_;
        int to_bitField0_ = 0;
        if (((from_bitField0_ & 0x00000001) == 0x00000001)) {
          to_bitField0_ |= 0x00000001;
        }
        result.insertion_ = insertion_;
        if (((from_bitField0_ & 0x00000002) == 0x00000002)) {
          to_bitField0_ |= 0x00000002;
        }
        result.ofMatch_ = ofMatch_;
        if (((bitField0_ & 0x00000004) == 0x00000004)) {
          ofInstructions_ = java.util.Collections.unmodifiableList(ofInstructions_);
          bitField0_ = (bitField0_ & ~0x00000004);
        }
        result.ofInstructions_ = ofInstructions_;
        result.bitField0_ = to_bitField0_;
        onBuilt();
        return result;
      }

      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate) {
          return mergeFrom((ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate other) {
        if (other == ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate.getDefaultInstance()) return this;
        if (other.hasInsertion()) {
          setInsertion(other.getInsertion());
        }
        if (other.hasOfMatch()) {
          setOfMatch(other.getOfMatch());
        }
        if (!other.ofInstructions_.isEmpty()) {
          if (ofInstructions_.isEmpty()) {
            ofInstructions_ = other.ofInstructions_;
            bitField0_ = (bitField0_ & ~0x00000004);
          } else {
            ensureOfInstructionsIsMutable();
            ofInstructions_.addAll(other.ofInstructions_);
          }
          onChanged();
        }
        this.mergeUnknownFields(other.getUnknownFields());
        return this;
      }

      public final boolean isInitialized() {
        if (!hasInsertion()) {
          
          return false;
        }
        return true;
      }

      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate) e.getUnfinishedMessage();
          throw e;
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      // required bool insertion = 1;
      private boolean insertion_ ;
      /**
       * <code>required bool insertion = 1;</code>
       *
       * <pre>
       * whether or not this is an insertion or a deletion
       * </pre>
       */
      public boolean hasInsertion() {
        return ((bitField0_ & 0x00000001) == 0x00000001);
      }
      /**
       * <code>required bool insertion = 1;</code>
       *
       * <pre>
       * whether or not this is an insertion or a deletion
       * </pre>
       */
      public boolean getInsertion() {
        return insertion_;
      }
      /**
       * <code>required bool insertion = 1;</code>
       *
       * <pre>
       * whether or not this is an insertion or a deletion
       * </pre>
       */
      public Builder setInsertion(boolean value) {
        bitField0_ |= 0x00000001;
        insertion_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>required bool insertion = 1;</code>
       *
       * <pre>
       * whether or not this is an insertion or a deletion
       * </pre>
       */
      public Builder clearInsertion() {
        bitField0_ = (bitField0_ & ~0x00000001);
        insertion_ = false;
        onChanged();
        return this;
      }

      // optional bytes of_match = 2;
      private com.google.protobuf.ByteString ofMatch_ = com.google.protobuf.ByteString.EMPTY;
      /**
       * <code>optional bytes of_match = 2;</code>
       *
       * <pre>
       * Generated from the writeTo method of OFMatch.  Regenerate
       * OFMatch using OFMatch.readFrom.
       * </pre>
       */
      public boolean hasOfMatch() {
        return ((bitField0_ & 0x00000002) == 0x00000002);
      }
      /**
       * <code>optional bytes of_match = 2;</code>
       *
       * <pre>
       * Generated from the writeTo method of OFMatch.  Regenerate
       * OFMatch using OFMatch.readFrom.
       * </pre>
       */
      public com.google.protobuf.ByteString getOfMatch() {
        return ofMatch_;
      }
      /**
       * <code>optional bytes of_match = 2;</code>
       *
       * <pre>
       * Generated from the writeTo method of OFMatch.  Regenerate
       * OFMatch using OFMatch.readFrom.
       * </pre>
       */
      public Builder setOfMatch(com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000002;
        ofMatch_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>optional bytes of_match = 2;</code>
       *
       * <pre>
       * Generated from the writeTo method of OFMatch.  Regenerate
       * OFMatch using OFMatch.readFrom.
       * </pre>
       */
      public Builder clearOfMatch() {
        bitField0_ = (bitField0_ & ~0x00000002);
        ofMatch_ = getDefaultInstance().getOfMatch();
        onChanged();
        return this;
      }

      // repeated bytes of_instructions = 3;
      private java.util.List<com.google.protobuf.ByteString> ofInstructions_ = java.util.Collections.emptyList();
      private void ensureOfInstructionsIsMutable() {
        if (!((bitField0_ & 0x00000004) == 0x00000004)) {
          ofInstructions_ = new java.util.ArrayList<com.google.protobuf.ByteString>(ofInstructions_);
          bitField0_ |= 0x00000004;
         }
      }
      /**
       * <code>repeated bytes of_instructions = 3;</code>
       *
       * <pre>
       * Generated from the writeTo method of OFInstruction.  Regenerate
       * each instruction separately using OFInstruction.readFrom.
       * </pre>
       */
      public java.util.List<com.google.protobuf.ByteString>
          getOfInstructionsList() {
        return java.util.Collections.unmodifiableList(ofInstructions_);
      }
      /**
       * <code>repeated bytes of_instructions = 3;</code>
       *
       * <pre>
       * Generated from the writeTo method of OFInstruction.  Regenerate
       * each instruction separately using OFInstruction.readFrom.
       * </pre>
       */
      public int getOfInstructionsCount() {
        return ofInstructions_.size();
      }
      /**
       * <code>repeated bytes of_instructions = 3;</code>
       *
       * <pre>
       * Generated from the writeTo method of OFInstruction.  Regenerate
       * each instruction separately using OFInstruction.readFrom.
       * </pre>
       */
      public com.google.protobuf.ByteString getOfInstructions(int index) {
        return ofInstructions_.get(index);
      }
      /**
       * <code>repeated bytes of_instructions = 3;</code>
       *
       * <pre>
       * Generated from the writeTo method of OFInstruction.  Regenerate
       * each instruction separately using OFInstruction.readFrom.
       * </pre>
       */
      public Builder setOfInstructions(
          int index, com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureOfInstructionsIsMutable();
        ofInstructions_.set(index, value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated bytes of_instructions = 3;</code>
       *
       * <pre>
       * Generated from the writeTo method of OFInstruction.  Regenerate
       * each instruction separately using OFInstruction.readFrom.
       * </pre>
       */
      public Builder addOfInstructions(com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensureOfInstructionsIsMutable();
        ofInstructions_.add(value);
        onChanged();
        return this;
      }
      /**
       * <code>repeated bytes of_instructions = 3;</code>
       *
       * <pre>
       * Generated from the writeTo method of OFInstruction.  Regenerate
       * each instruction separately using OFInstruction.readFrom.
       * </pre>
       */
      public Builder addAllOfInstructions(
          java.lang.Iterable<? extends com.google.protobuf.ByteString> values) {
        ensureOfInstructionsIsMutable();
        super.addAll(values, ofInstructions_);
        onChanged();
        return this;
      }
      /**
       * <code>repeated bytes of_instructions = 3;</code>
       *
       * <pre>
       * Generated from the writeTo method of OFInstruction.  Regenerate
       * each instruction separately using OFInstruction.readFrom.
       * </pre>
       */
      public Builder clearOfInstructions() {
        ofInstructions_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000004);
        onChanged();
        return this;
      }

      // @@protoc_insertion_point(builder_scope:SingleFTableUpdate)
    }

    static {
      defaultInstance = new SingleFTableUpdate(true);
      defaultInstance.initFields();
    }

    // @@protoc_insertion_point(class_scope:SingleFTableUpdate)
  }

  private static com.google.protobuf.Descriptors.Descriptor
    internal_static_SingleFTableUpdate_descriptor;
  private static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_SingleFTableUpdate_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\030singleFTableUpdate.proto\"R\n\022SingleFTab" +
      "leUpdate\022\021\n\tinsertion\030\001 \002(\010\022\020\n\010of_match\030" +
      "\002 \001(\014\022\027\n\017of_instructions\030\003 \003(\014B3\n\030ft_ops" +
      ".serialized_updateB\027SingleFTableUpdatePr" +
      "oto"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
      new com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner() {
        public com.google.protobuf.ExtensionRegistry assignDescriptors(
            com.google.protobuf.Descriptors.FileDescriptor root) {
          descriptor = root;
          internal_static_SingleFTableUpdate_descriptor =
            getDescriptor().getMessageTypes().get(0);
          internal_static_SingleFTableUpdate_fieldAccessorTable = new
            com.google.protobuf.GeneratedMessage.FieldAccessorTable(
              internal_static_SingleFTableUpdate_descriptor,
              new java.lang.String[] { "Insertion", "OfMatch", "OfInstructions", });
          return null;
        }
      };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
  }

  // @@protoc_insertion_point(outer_class_scope)
}
