package com.ibm.wala.ipa.callgraph.multithread.graph;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.CodeScanner;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.multithread.util.MultiThreadAnalysisUtil;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.FieldReference;

/**
 * Context Interpreter that uses signatures where available
 */
public class AnalysisContextInterpreter implements SSAContextInterpreter {

    @Override
    public Iterator<NewSiteReference> iterateNewSites(CGNode node) {
        assert node != null;
        try {
            return CodeScanner.getNewSites(node.getMethod()).iterator();
        } catch (InvalidClassFileException e) {
            assert false;
            return null;
        }
    }

    @Override
    public Iterator<FieldReference> iterateFieldsRead(CGNode node) {
        assert node != null;
        try {
            return CodeScanner.getFieldsRead(node.getMethod()).iterator();
        } catch (InvalidClassFileException e) {
            assert false;
            return null;
        }
    }

    @Override
    public Iterator<FieldReference> iterateFieldsWritten(CGNode node) {
        assert node != null;
        try {
            return CodeScanner.getFieldsWritten(node.getMethod()).iterator();
        } catch (InvalidClassFileException e) {
            assert false;
            return null;
        }
    }

    @Override
    public boolean recordFactoryType(CGNode node, IClass klass) {
        // TODO Factory types not tracked
        return false;
    }

    @Override
    public boolean understands(CGNode node) {
        return true;
    }

    @Override
    public Iterator<CallSiteReference> iterateCallSites(CGNode node) {
        assert node != null;
        try {
            return CodeScanner.getCallSites(node.getMethod()).iterator();
        } catch (InvalidClassFileException e) {
            assert false;
            return null;
        }
    }

    @Override
    public IR getIR(CGNode node) {
        return MultiThreadAnalysisUtil.getIR(node);
    }

    @Override
    public DefUse getDU(CGNode node) {
        return MultiThreadAnalysisUtil.getDefUse(node.getMethod());
    }

    @Override
    public int getNumberOfStatements(CGNode node) {
        IR ir = getIR(node);
        if (ir == null) {
            return -1;
        }
        return ir.getInstructions().length;
    }

    @Override
    public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCFG(CGNode n) {
        IR ir = getIR(n);
        if (ir == null) {
            return null;
        }
        return ir.getControlFlowGraph();
    }

}
