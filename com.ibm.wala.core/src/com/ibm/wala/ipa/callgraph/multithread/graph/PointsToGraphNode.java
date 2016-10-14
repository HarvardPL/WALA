package com.ibm.wala.ipa.callgraph.multithread.graph;

import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.types.TypeReference;

/**
 * Local variable or static field in a particular code context or an object field
 */
public interface PointsToGraphNode extends PointerKey {
    /**
     * Get the type this points to graph node represents
     * 
     * @return type
     */
    TypeReference getExpectedType();
}
