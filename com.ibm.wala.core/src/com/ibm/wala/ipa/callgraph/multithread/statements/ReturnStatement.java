package com.ibm.wala.ipa.callgraph.multithread.statements;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.multithread.analyses.HeapAbstractionFactory;
import com.ibm.wala.ipa.callgraph.multithread.engine.PointsToAnalysis.StmtAndContext;
import com.ibm.wala.ipa.callgraph.multithread.graph.GraphDelta;
import com.ibm.wala.ipa.callgraph.multithread.graph.PointsToGraph;
import com.ibm.wala.ipa.callgraph.multithread.graph.ReferenceVariableReplica;
import com.ibm.wala.ipa.callgraph.multithread.registrar.StatementRegistrar;
import com.ibm.wala.ipa.callgraph.multithread.registrar.ReferenceVariableFactory.ReferenceVariable;

/**
 * Points-to statement for a "return" instruction
 */
public class ReturnStatement extends PointsToStatement {

    /**
     * Node for return result
     */
    private ReferenceVariable result;
    /**
     * Node summarizing all return values for the method
     */
    private final ReferenceVariable returnSummary;

    /**
     * Create a points-to statement for a return instruction
     *
     * @param result
     *            Node for return result
     * @param returnSummary
     *            Node summarizing all return values for the method
     * @param m
     *            method the points-to statement came from
     */
    protected ReturnStatement(ReferenceVariable result,
            ReferenceVariable returnSummary, IMethod m) {
        super(m);
        this.result = result;
        this.returnSummary = returnSummary;
    }

    @Override
    public GraphDelta process(Context context, HeapAbstractionFactory haf,
            PointsToGraph g, GraphDelta delta, StatementRegistrar registrar, StmtAndContext originator) {
        ReferenceVariableReplica returnRes =
 new ReferenceVariableReplica(context, result, haf);
        ReferenceVariableReplica summaryRes =
 new ReferenceVariableReplica(context, returnSummary, haf);

        // don't need to use delta, as this just adds a subset edge
        return g.copyEdges(returnRes, summaryRes);

    }

    @Override
    public String toString() {
        return "return " + result;
    }

    @Override
    public void replaceUse(int useNumber, ReferenceVariable newVariable) {
        assert useNumber == 0;
        result = newVariable;
    }

    @Override
    public List<ReferenceVariable> getUses() {
        return Collections.singletonList(result);
    }

    @Override
    public ReferenceVariable getDef() {
        return null;
    }

}
