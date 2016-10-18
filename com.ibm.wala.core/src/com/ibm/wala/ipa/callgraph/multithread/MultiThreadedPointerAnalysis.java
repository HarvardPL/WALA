/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.ipa.callgraph.multithread;

import java.util.Collection;
import java.util.Iterator;

import com.ibm.wala.analysis.pointers.HeapGraph;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.ProgramCounter;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.multithread.analyses.HeapAbstractionFactory;
import com.ibm.wala.ipa.callgraph.multithread.graph.ObjectField;
import com.ibm.wala.ipa.callgraph.multithread.graph.PointsToGraph;
import com.ibm.wala.ipa.callgraph.multithread.graph.PointsToGraphNode;
import com.ibm.wala.ipa.callgraph.multithread.graph.ReferenceVariableCache;
import com.ibm.wala.ipa.callgraph.multithread.graph.ReferenceVariableReplica;
import com.ibm.wala.ipa.callgraph.multithread.registrar.MethodSummaryNodes;
import com.ibm.wala.ipa.callgraph.multithread.registrar.ReferenceVariableFactory;
import com.ibm.wala.ipa.callgraph.multithread.registrar.ReferenceVariableFactory.ReferenceVariable;
import com.ibm.wala.ipa.callgraph.multithread.registrar.StatementRegistrar;
import com.ibm.wala.ipa.callgraph.propagation.FilteredPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.FilteredPointerKey.TypeFilter;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.intset.OrdinalSetMapping;

/**
 * A multi-threaded pointer analysis.
 *
 */
public final class MultiThreadedPointerAnalysis implements PointerAnalysis<InstanceKey> {

  private final PointsToGraph g;
  private final HeapModel hm;
  private IClassHierarchy cha;
  
  public MultiThreadedPointerAnalysis(PointsToGraph g, StatementRegistrar r, ReferenceVariableCache rvcache, HeapAbstractionFactory haf, IClassHierarchy cha) {
    this.g = g;
    this.cha = cha;
    this.hm = new MTHeapModel(g, rvcache, haf, cha);
  }

  @Override
  public OrdinalSet<InstanceKey> getPointsToSet(PointerKey key) {
    assert key instanceof PointsToGraphNode;
    return g.getOrdinalPointsToSet((PointsToGraphNode) key);
  }

  @Override
  public HeapModel getHeapModel() {
    return hm;
  }

  @Override
  public HeapGraph<InstanceKey> getHeapGraph() {
    // XXX Need to create this data structure from the points-to graph
    throw new UnsupportedOperationException("No heap graph available (yet) for multi-threaded pointer analysis.");
  }

  @Override
  public OrdinalSetMapping<InstanceKey> getInstanceKeyMapping() {
    return g.getOrdinalSetMapping();
  }

  @Override
  public Iterable<PointerKey> getPointerKeys() {
    return g.getAllNodes();
  }

  @Override
  public Collection<InstanceKey> getInstanceKeys() {
    return g.getAllInstanceKeys();
  }

  @Override
  public boolean isFiltered(PointerKey pk) {
    // Filtered pks not supported
    return false;
  }

  @Override
  public IClassHierarchy getClassHierarchy() {
    return cha;
  }
  
  private final static class MTHeapModel implements HeapModel {
    
    private final HeapAbstractionFactory haf;
    private final IClassHierarchy cha;
    private final ReferenceVariableCache cache;
    private final PointsToGraph g;

    public MTHeapModel(PointsToGraph g, ReferenceVariableCache cache, HeapAbstractionFactory haf, IClassHierarchy cha) {
      this.haf = haf;
      this.cha = cha;
      this.cache = cache;
      this.g = g;
    }
    
    @Override
    public PointerKey getPointerKeyForStaticField(IField f) {
      // All fields are treated flow-insensitively
      return new ReferenceVariableReplica(haf.initialContext(), cache.getStaticField(f), haf);
    }
    
    @Override
    public PointerKey getPointerKeyForReturnValue(CGNode node) {
      MethodSummaryNodes ms = cache.getMethodSummary(node.getMethod());
      if (ms == null) {
        // This method was never registered!
        return null;
      }
      return new ReferenceVariableReplica(node.getContext(), ms.getReturn(), haf);
    }
    
    @Override
    public PointerKey getPointerKeyForLocal(CGNode node, int valueNumber) {
      ReferenceVariable rv = cache.getReferenceVariable(valueNumber, node.getMethod());
      if (rv == null) {
        // No reference variable for the requested variable
        return null;
      }
      return new ReferenceVariableReplica(node.getContext(), rv, haf);
    }
    
    @Override
    public PointerKey getPointerKeyForInstanceField(InstanceKey I, IField field) {
      return new ObjectField(I, field.getReference());
    }
    
    @Override
    public PointerKey getPointerKeyForExceptionalReturnValue(CGNode node) {
      MethodSummaryNodes ms = cache.getMethodSummary(node.getMethod());
      if (ms == null) {
        // This method was never registered!
        return null;
      }
      return new ReferenceVariableReplica(node.getContext(), ms.getException(), haf);
    }
    
    @Override
    public PointerKey getPointerKeyForArrayContents(InstanceKey I) {
      return new ObjectField(I, PointsToGraph.ARRAY_CONTENTS, I.getConcreteType().getReference().getArrayElementType());
    }
    
    @Override
    public FilteredPointerKey getFilteredPointerKeyForLocal(CGNode node, int valueNumber, final TypeFilter filter) {
      throw new UnsupportedOperationException("Cannot create filtered pointer keys in multi-threaded analysis.");
    }
    
    
    
    @Override
    public InstanceKey getInstanceKeyForPEI(CGNode node, ProgramCounter instr, TypeReference type) {
      int basicBlockID = node.getIR().getControlFlowGraph().getBlockForInstruction(instr.getProgramCounter()).getNumber();
      ReferenceVariable rv = cache.getImplicitExceptionNode(type, basicBlockID, node.getMethod());
      Iterator<InstanceKey> iter = g.pointsToIterator(new ReferenceVariableReplica(node.getContext(), rv, haf));
      InstanceKey ik = iter.next();
      assert !iter.hasNext() : "Each generated exception should point to a single abstract object.";
      return ik;
    }
    
    @Override
    public InstanceKey getInstanceKeyForMultiNewArray(CGNode node, NewSiteReference allocation, int dim) {
      ReferenceVariable rv;
      if (dim == 0) {
        // this is the actual array that was created
        int valueNumber = node.getIR().getNew(allocation).getDef();
        rv = cache.getReferenceVariable(valueNumber, node.getMethod());
      } else {
        // This is one of the inner dimensions
        rv = cache.getInnerArray(dim, allocation.getProgramCounter(), node.getMethod());
      }
      Iterator<InstanceKey> iter = g.pointsToIterator(new ReferenceVariableReplica(node.getContext(), rv, haf));
      InstanceKey ik = iter.next();
      assert !iter.hasNext() : "Each new array should point to a single abstract object.";
      return ik;
    }
    
    @Override
    public InstanceKey getInstanceKeyForMetadataObject(Object obj, TypeReference objType) {
      // XXX Reflection is not supported by the multi-threaded analysis
      throw new UnsupportedOperationException("Reflection is not supported by the multi-threaded analysis");
    }
    
    @Override
    public <T> InstanceKey getInstanceKeyForConstant(TypeReference type, T S) {
      // XXX Constants are treated like normal allocations
      throw new UnsupportedOperationException("Constants are treated like normal allocations in the multi-threaded analysis");
    }
    
    @Override
    public InstanceKey getInstanceKeyForAllocation(CGNode node, NewSiteReference allocation) {
      int valueNumber = node.getIR().getNew(allocation).getDef();
      ReferenceVariableReplica rep = (ReferenceVariableReplica) getPointerKeyForLocal(node, valueNumber);
      Iterator<InstanceKey> iter = g.pointsToIterator(rep);
      InstanceKey ik = iter.next();
      assert !iter.hasNext() : "Each new array should point to a single abstract object.";
      return ik;
    }
    
    @Override
    public Iterator<PointerKey> iteratePointerKeys() {
      return g.getAllNodes().iterator();
    }
    
    @Override
    public IClassHierarchy getClassHierarchy() {
      return cha;
    }
  }
}
