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
import com.ibm.wala.ipa.callgraph.multithread.graph.PointsToGraphNode;
import com.ibm.wala.ipa.callgraph.multithread.graph.ReferenceVariableReplica;
import com.ibm.wala.ipa.callgraph.multithread.registrar.StatementRegistrar;
import com.ibm.wala.ipa.callgraph.multithread.registrar.ReferenceVariableFactory.ReferenceVariable;

/**
 * Points-to statement for an assignment from a static field to a local variable, v = o.x
 */
public class StaticFieldToLocalStatement extends PointsToStatement {

    /**
     * assignee
     */
    private final ReferenceVariable staticField;
    /**
     * assigned
     */
    private final ReferenceVariable local;

    /**
     * Statement for an assignment from a static field to a local, local = ClassName.staticField
     *
     * @param local points-to graph node for the assigned value
     * @param staticField points-to graph node for assignee
     * @param m
     */
    protected StaticFieldToLocalStatement(ReferenceVariable local, ReferenceVariable staticField, IMethod m) {
        super(m);
        assert staticField.isSingleton() : staticField + " is not static";
        assert !local.isSingleton() : local + " is static";

        this.staticField = staticField;
        this.local = local;
    }

    @Override
    public GraphDelta process(Context context, HeapAbstractionFactory haf, PointsToGraph g, GraphDelta delta,
                              StatementRegistrar registrar, StmtAndContext originator) {

        PointsToGraphNode l = new ReferenceVariableReplica(context, local, haf);
        PointsToGraphNode r = new ReferenceVariableReplica(haf.initialContext(), staticField, haf);

        // don't need to use delta, as this just adds a subset edge
        return g.copyEdges(r, l);
    }

    @Override
    public String toString() {
        return local + " = " + staticField;
    }

    /**
     * Reference variable for the static field being accessed
     *
     * @return variable for the static field
     */
    public ReferenceVariable getStaticField() {
        return staticField;
    }

    @Override
    public void replaceUse(int useNumber, ReferenceVariable newVariable) {
        throw new UnsupportedOperationException("StaticFieldToLocal has no replacable uses");
    }

    @Override
    public List<ReferenceVariable> getUses() {
        return Collections.emptyList();
    }

    @Override
    public ReferenceVariable getDef() {
        return local;
    }
}
