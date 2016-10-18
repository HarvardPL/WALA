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

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.multithread.analyses.HeapAbstractionFactory;
import com.ibm.wala.ipa.callgraph.multithread.engine.PointsToAnalysis;
import com.ibm.wala.ipa.callgraph.multithread.engine.PointsToAnalysisMultiThreaded;
import com.ibm.wala.ipa.callgraph.multithread.engine.PointsToAnalysisSingleThreaded;
import com.ibm.wala.ipa.callgraph.multithread.graph.PointsToGraph;
import com.ibm.wala.ipa.callgraph.multithread.registrar.StatementRegistrar;
import com.ibm.wala.ipa.callgraph.multithread.registrar.StatementRegistrationPass;
import com.ibm.wala.ipa.callgraph.multithread.statements.PointsToStatement;
import com.ibm.wala.ipa.callgraph.multithread.statements.StatementFactory;
import com.ibm.wala.ipa.callgraph.multithread.util.MultiThreadAnalysisUtil;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;

public class MultiThreadedCallGraphBuilder implements CallGraphBuilder {
  
  private static int OUTPUT_LEVEL = 0;
  
  /**
   * Should the registration of pointer statements (which requires 
   * a pass over the byte code) be done on-demand during the pointer analysis (i.e., online)
   * or all at the beginning before the multi-threaded analysis starts?
   */
  private static final boolean REGISTER_STMTS_LAZILY = false;

  private HeapAbstractionFactory haf;
  private boolean useSingleAllocForGenEx = false;
  private boolean useSingleAllocForThrowable = false;
  private boolean useSingleAllocForPrimitiveArrays = false;
  private boolean useSingleAllocForStrings = false;
  private boolean useSingleAllocForImmutableWrappers = false;
  private MultiThreadedPointerAnalysis pta;

  public MultiThreadedCallGraphBuilder(AnalysisOptions options, AnalysisCache cache, IClassHierarchy cha, AnalysisScope scope,
      HeapAbstractionFactory haf) {
    MultiThreadAnalysisUtil.initFromObjects(options, cache, cha, scope);
    this.haf = haf;
  }

  @Override
  public CallGraph makeCallGraph(AnalysisOptions options, IProgressMonitor monitor) throws IllegalArgumentException,
      CallGraphBuilderCancelException {
    PointsToAnalysis analysis = new PointsToAnalysisMultiThreaded(haf);
    PointsToAnalysis.outputLevel = OUTPUT_LEVEL;
    PointsToGraph g;
    StatementRegistrar registrar;
    StatementFactory factory = new StatementFactory();
    if (REGISTER_STMTS_LAZILY) {
        registrar = new StatementRegistrar(factory,
                                           useSingleAllocForGenEx,
                                           useSingleAllocForThrowable,
                                           useSingleAllocForPrimitiveArrays,
                                           useSingleAllocForStrings,
                                           useSingleAllocForImmutableWrappers);
        g = analysis.solveAndRegister(registrar);
    }
    else {
        StatementRegistrationPass pass = new StatementRegistrationPass(factory,
                                                                       useSingleAllocForGenEx,
                                                                       useSingleAllocForThrowable,
                                                                       useSingleAllocForPrimitiveArrays,
                                                                       useSingleAllocForStrings,
                                                                       useSingleAllocForImmutableWrappers);
        pass.run();
        registrar = pass.getRegistrar();
        PointsToAnalysis.outputLevel = OUTPUT_LEVEL;
        g = analysis.solve(registrar);
    }

    if (OUTPUT_LEVEL >= 1) {
      System.err.println("Registered statements: " + registrar.size());
    }
    if (OUTPUT_LEVEL >= 2) {
        for (IMethod m : registrar.getRegisteredMethods()) {
            for (PointsToStatement s : registrar.getStatementsForMethod(m)) {
                System.err.println("\t" + s + " (" + s.getClass().getSimpleName() + ")");
            }
        }
    }
    if (OUTPUT_LEVEL >= 1) {
      //        System.err.println(g.getNodes().size() + " PTG nodes.");
      System.err.println(g.getCallGraph().getNumberOfNodes() + " CG nodes.");
      System.err.println(g.clinitCount + " Class initializers.");
    }
    pta = new MultiThreadedPointerAnalysis(g, registrar, registrar.getAllLocals(), haf, MultiThreadAnalysisUtil.getClassHierarchy());
    return g.getCallGraph();
  }

  @Override
  public PointerAnalysis<InstanceKey> getPointerAnalysis() {
    return pta;
  }

  @Override
  public AnalysisCache getAnalysisCache() {
    return MultiThreadAnalysisUtil.getCache();
  }
}
