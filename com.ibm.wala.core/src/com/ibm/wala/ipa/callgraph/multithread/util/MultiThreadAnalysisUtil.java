package com.ibm.wala.ipa.callgraph.multithread.util;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.FakeRootMethod;
import com.ibm.wala.ipa.callgraph.multithread.util.print.CFGWriter;
import com.ibm.wala.ipa.callgraph.multithread.util.print.PrettyPrinter;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.AnalysisScopeReader;

/**
 * Utilities that are valid for an entire set of analyses, and can be accessed statically
 */
public class MultiThreadAnalysisUtil {
    /**
     * Cache containing and managing SSA IR we are analyzing
     */
    private static AnalysisCache cache;
    /**
     * Options for the analysis (e.g. entry points)
     */
    private static AnalysisOptions options;
    /**
     * Class hierarchy for the code being analyzed
     */
    private static IClassHierarchy cha;
    /**
     * WALA's fake root method (calls the entry points)
     */
    private static AbstractRootMethod fakeRoot;
    /**
     * Class for java.lang.String
     */
    private static IClass stringClass;
    /**
     * Class for java.lang.Object
     */
    private static IClass objectClass;
    /**
     * Class for java.lang.Throwable
     */
    private static IClass throwableClass;
    /**
     * Class for java.lang.Error
     */
    private static IClass errorClass;
    /**
     * Class for java.lang.Cloneable
     */
    private static IClass cloneableInterface;
    /**
     * Class for java.io.Serializable
     */
    private static IClass serializableInterface;
    /**
     * type of the field in java.lang.String
     */
    public static final TypeReference STRING_VALUE_TYPE = TypeReference.JavaLangObject;
    /**
     * Class for value field of java.lang.String
     */
    private static IClass stringValueClass;
    public static IClass privilegedActionClass;
    public static IClass privilegedExceptionActionClass;

    private static String outputDirectory;
    private static AnalysisScope scope;

    /**
     * File describing classes that should be ignored by all analyses, even the WALA class loader
     */
    private static final File EXCLUSIONS_FILE = new File("Exclusions.txt");
    /**
     * File containing the location of the java standard library and other standard jars
     */
    private static final String PRIMORDIAL_FILENAME = "primordial.txt";

    /**
     * Number of threads to use for concurrent data structures
     *
     * Initialized to total processors to make sure there is something here during initialization
     */
    public static int numThreads = Runtime.getRuntime().availableProcessors();

    /**
     * Methods should be accessed statically, make sure to call {@link MultiThreadAnalysisUtil#init(String, String)} before running
     * an analysis
     */
    private MultiThreadAnalysisUtil() {
        // Intentionally blank
    }
    
    /**
     * Initialize the analysis from already computed classes
     * 
     * @param options Analysis options 
     * @param cache cache for analysis results
     * @param cha class hierarchy
     * @param scope class path scope
     */
    public static void initFromObjects(AnalysisOptions options, AnalysisCache cache, IClassHierarchy cha, AnalysisScope scope) {
        
        MultiThreadAnalysisUtil.cache = cache;
        MultiThreadAnalysisUtil.scope = scope;
        MultiThreadAnalysisUtil.cha = cha;
        MultiThreadAnalysisUtil.scope = scope;
        MultiThreadAnalysisUtil.outputDirectory = ".";
        
        addEntriesToRootMethod();
        setUpCommonClasses();
    }
 
    /**
     * Inialize key WALA classes
     *
     * @param classPath Java class path to load class filed from with entries separated by ":"
     * @param entryPoint entry point main method, e.g mypackage.mysubpackage.MyClass
     * @param outputDirectory directory to put outputfiles into
     *
     * @throws IOException Thrown when the analysis scope is invalid
     * @throws ClassHierarchyException Thrown by WALA during class hierarchy construction, if there are issues with the
     *             class path and for other reasons see {@link ClassHierarchy}
     */
    public static void init(String classPath, String entryPoint, String outputDirectory) throws IOException,
                                                                                        ClassHierarchyException {

        MultiThreadAnalysisUtil.outputDirectory = outputDirectory;
        MultiThreadAnalysisUtil.cache = new AnalysisCache();


        MultiThreadAnalysisUtil.scope = AnalysisScopeReader.readJavaScope(PRIMORDIAL_FILENAME,
                                                                EXCLUSIONS_FILE,
                                                                MultiThreadAnalysisUtil.class.getClassLoader());
        System.err.println("CLASSPATH=" + classPath);
        AnalysisScopeReader.addClassPathToScope(classPath, scope, ClassLoaderReference.Application);

        long start = System.currentTimeMillis();

        MultiThreadAnalysisUtil.cha = ClassHierarchy.make(scope);
        System.err.println(MultiThreadAnalysisUtil.cha.getNumberOfClasses() + " classes loaded. It took "
                + (System.currentTimeMillis() - start) + "ms");

        Iterable<Entrypoint> entrypoints;
        if (entryPoint == null) {
            entrypoints = Collections.emptySet();
        }
        else {
            // Add L to the name to indicate that this is a class name
            entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, MultiThreadAnalysisUtil.cha, "L"
                    + entryPoint.replace(".", "/"));
        }
        MultiThreadAnalysisUtil.options = new AnalysisOptions(scope, entrypoints);

        addEntriesToRootMethod();
        setUpCommonClasses();
        ensureSignatures();
    }

    private static void ensureSignatures() {
        IClass systemClass = cha.lookupClass(TypeReference.JavaLangSystem);
        Selector arrayCopy = Selector.make("arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V");
        IMethod m = cha.resolveMethod(systemClass, arrayCopy);
        if (getIR(m) == null) {
            System.err.println("WARNING: cannot resolve signatures. Ensure \"classes/signatures\" is on the analysis classpath set with \"-cp\".");
        } else {
            System.err.println("Signatures: ENABLED");
        }
    }

    private static void addEntriesToRootMethod() {
        // Set up the entry points
        fakeRoot = new FakeRootMethod(cha, options, cache);
        for (Entrypoint e : options.getEntrypoints()) {
            // Add in the fake root method that sets up the call to main
            SSAAbstractInvokeInstruction call = e.addCall(fakeRoot);

            if (call == null) {
                throw new RuntimeException("Missing entry point " + e);
            }
        }
        // Have to add return to maintain the invariant that two basic blocks
        // have more than one edge between them. Otherwise the exit basic block
        // could have an exception edge and normal edge from the same basic
        // block.
        fakeRoot.addReturn(-1, false);
        String fullFilename = outputDirectory + "/cfg_" + PrettyPrinter.methodString(fakeRoot);
        CFGWriter.writeToFile(fakeRoot, fullFilename);
    }

    private static void setUpCommonClasses() {
        stringClass = cha.lookupClass(TypeReference.JavaLangString);
        stringValueClass = cha.lookupClass(STRING_VALUE_TYPE);
        objectClass = cha.lookupClass(TypeReference.JavaLangObject);
        throwableClass = cha.lookupClass(TypeReference.JavaLangThrowable);
        errorClass = cha.lookupClass(TypeReference.JavaLangError);
        TypeName privTN = TypeName.string2TypeName("Ljava/security/PrivilegedAction");
        TypeReference privTR = TypeReference.findOrCreate(ClassLoaderReference.Primordial, privTN);
        privilegedActionClass = cha.lookupClass(privTR);

        TypeName privETN = TypeName.string2TypeName("Ljava/security/PrivilegedExceptionAction");
        TypeReference privETR = TypeReference.findOrCreate(ClassLoaderReference.Primordial, privETN);
        privilegedExceptionActionClass = cha.lookupClass(privETR);

        cloneableInterface = cha.lookupClass(TypeReference.JavaLangCloneable);
        serializableInterface = cha.lookupClass(TypeReference.JavaIoSerializable);

    }

    /**
     * Cache of various analysis artifacts, contains the SSA IR
     *
     * @return WALA analysis cache
     */
    public static AnalysisCache getCache() {
        return cache;
    }

    /**
     * WALA analysis options, contains the entry-point
     *
     * @return WALA analysis options
     */
    public static AnalysisOptions getOptions() {
        return options;
    }

    /**
     * WALA's class hierarchy
     *
     * @return class hierarchy
     */
    public static IClassHierarchy getClassHierarchy() {
        return cha;
    }

    /**
     * The root method that calls the entry-points
     *
     * @return WALA fake root method (sets up and calls actual entry points)
     */
    public static AbstractRootMethod getFakeRoot() {
        return fakeRoot;
    }

    /**
     * Get the IR for the given method, returns null for native methods without signatures
     *
     * @param resolvedMethod method to get the IR for
     * @return the code for the given method, null for native methods
     */
    public static IR getIR(IMethod resolvedMethod) {
        if (resolvedMethod.isNative()) {
            // Native method with no signature
            return null;
        }

        return cache.getSSACache().findOrCreateIR(resolvedMethod, Everywhere.EVERYWHERE, options.getSSAOptions());
    }

    /**
     * Get the def-use results for the given method, returns null for native methods without signatures
     *
     * @param resolvedMethod method to get the def-use results for
     * @return the def-use for the given method, null for native methods
     */
    public static DefUse getDefUse(IMethod resolvedMethod) {
        if (resolvedMethod.isNative()) {
            // Native method with no signature
            return null;
        }

        return cache.getSSACache().findOrCreateDU(resolvedMethod, Everywhere.EVERYWHERE, options.getSSAOptions());
    }

    /**
     * Get the IR for the method represented by the call graph node, returns null for native methods without signatures
     *
     * @param n call graph node
     * @return the code for the given call graph node, null for native methods without signatures
     */
    public static IR getIR(CGNode n) {
        return getIR(n.getMethod());
    }

    /**
     * Get the canonical class for java.lang.String
     *
     * @return class
     */
    public static IClass getStringClass() {
        return stringClass;
    }

    /**
     * Get the canonical class for java.lang.Objecy
     *
     * @return class
     */
    public static IClass getObjectClass() {
        return objectClass;
    }

    /**
     * Get the canonical class for java.lang.Throwable
     *
     * @return class
     */
    public static IClass getThrowableClass() {
        return throwableClass;
    }

    /**
     * Get the canonical class for java.lang.Error
     *
     * @return class
     */
    public static IClass getErrorClass() {
        return errorClass;
    }

    public static IClass getCloneableInterface() {
        return cloneableInterface;
    }

    public static IClass getSerializableInterface() {
        return serializableInterface;
    }

    public static IClass getStringValueClass() {
        return stringValueClass;
    }

    /**
     * Get the directory to put output files into
     *
     * @return folder name
     */
    public static String getOutputDirectory() {
        return outputDirectory;
    }

    public static AnalysisScope getScope() {
        return scope;
    }
    
    public static <W, T> ConcurrentHashMap<W, T> createConcurrentHashMap() {
      return new ConcurrentHashMap<>(16, 0.75f, Runtime.getRuntime().availableProcessors());
    }

    public static <T> Set<T> createConcurrentSet() {
      return Collections.newSetFromMap(MultiThreadAnalysisUtil.<T, Boolean> createConcurrentHashMap());
    }

}
