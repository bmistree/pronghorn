#!/bin/bash

BRIDGE_NAME=test

ovs-vsctl add-br $BRIDGE_NAME
ovs-vsctl add-port $BRIDGE_NAME test-port-1
ovs-vsctl add-port $BRIDGE_NAME test-port-2
ovs-vsctl set-controller $BRIDGE_NAME tcp:127.0.0.1:6633
ovs-vsctl set bridge $BRIDGE_NAME protocols=OpenFlow13
