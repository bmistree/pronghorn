Pronghorn
===========

Pronghorn is a distributed controller platform for software defined
networks.  Pronghorn is interested in build a system to reduce the
negative effects that poorly written or malicious code can have on a
software defined network.  To this end, Pronghorn

   * Allows controllers to export different interfaces (subgraphs) to
     different applications, thereby preventing untrusted applications
     from modifying sensitive state,
     
   * Limits the amount of time an application can monopolize a locked
     resource, and
     
   * Prevents an application that does not correctly issue
     barriers/clean up its state from causing invisible disagreement
     between software and hardware state


Install
---------
After cloning repo, in base directory, update submodules:

    cd pronghorn
    git submodule update --init --recursive

Enter floodlight externals directory and rebuild floodlight:

    cd pronghorn/externals/floodligh
    ant dist

Following this, you should be able to build all experiments and
sources.
