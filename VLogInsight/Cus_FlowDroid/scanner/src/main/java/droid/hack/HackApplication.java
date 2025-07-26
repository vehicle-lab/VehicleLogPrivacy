package droid.hack;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.instruction.DexBackedInstruction21c;
import org.jf.dexlib2.dexbacked.instruction.DexBackedInstruction31c;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;
import soot.Main;
import soot.jimple.*;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.manifest.IManifestHandler;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.cfg.LibraryClassPatcher;
import soot.jimple.internal.JAssignStmt;
import soot.options.Options;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class HackApplication extends SetupApplication {
    private String apkPath;

    private Map<String, String> stringInformation = Map.of(
            "content://call_log", "READ_CALL_LOG",
            "content://com.android.contacts", "READ_CONTACTS",
            "content://com.android.calendar", "READ_CALENDER",
            "content://sms", "READ_SMS"
    );

    public HackApplication(String androidJar, String apkFileLocation) {
        super(androidJar, apkFileLocation);
        this.apkPath = apkFileLocation;

        initializeSoot();
    }

    private String getClasspath() {
        final String androidJar = config.getAnalysisFileConfig().getAndroidPlatformDir();
        final String apkFileLocation = config.getAnalysisFileConfig().getTargetAPKFile();
        final String additionalClasspath = config.getAnalysisFileConfig().getAdditionalClasspath();

        String classpath = forceAndroidJar ? androidJar : Scene.v().getAndroidJarPath(androidJar, apkFileLocation);
        if (additionalClasspath != null && !additionalClasspath.isEmpty())
            classpath += File.pathSeparator + additionalClasspath;
        return classpath;
    }

    protected void initializeSoot() {
        final String androidJar = config.getAnalysisFileConfig().getAndroidPlatformDir();
        final String apkFileLocation = config.getAnalysisFileConfig().getTargetAPKFile();

        // Clean up any old Soot instance we may have
        G.reset();

        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_allow_phantom_refs(true);
        if (config.getWriteOutputFiles())
            Options.v().set_output_format(Options.output_format_jimple);
        else
            Options.v().set_output_format(Options.output_format_none);
        Options.v().set_whole_program(true);
        Options.v().set_process_dir(Collections.singletonList(apkFileLocation));
        if (forceAndroidJar)
            Options.v().set_force_android_jar(androidJar);
        else
            Options.v().set_android_jars(androidJar);
        Options.v().set_src_prec(Options.src_prec_apk_class_jimple);
        Options.v().set_keep_offset(false);
        Options.v().set_keep_line_number(config.getEnableLineNumbers());
        Options.v().set_throw_analysis(Options.throw_analysis_dalvik);
        Options.v().set_process_multiple_dex(config.getMergeDexFiles());
        Options.v().set_ignore_resolution_errors(true);

        // Set soot phase option if original names should be used
        if (config.getEnableOriginalNames())
            Options.v().setPhaseOption("jb", "use-original-names:true");

        // Set the Soot configuration options. Note that this will needs to be
        // done before we compute the classpath.
        if (sootConfig != null)
            sootConfig.setSootOptions(Options.v(), config);

        Options.v().set_soot_classpath(getClasspath());
        Main.v().autoSetOptions();
        configureCallgraph();

        // Load whatever we need
        Scene.v().loadNecessaryClasses();

        // Make sure that we have valid Jimple bodies
        PackManager.v().getPack("wjpp").apply();

        // Patch the callgraph to support additional edges. We do this now,
        // because during callback discovery, the context-insensitive callgraph
        // algorithm would flood us with invalid edges.
        LibraryClassPatcher patcher = getLibraryClassPatcher();
        patcher.patchLibraries();
    }

    public void parseAppResources() throws IOException, XmlPullParserException {
        final File targetAPK = new File(this.apkPath);
        this.resources = new ARSCFileParser();
        this.resources.parse(targetAPK.getAbsolutePath());

        this.manifest = createManifestParser(targetAPK);
        Set<String> entryPoints = this.manifest.getEntryPointClasses();
        this.entrypoints = new HashSet<>(entryPoints.size());
        for (String className : entryPoints) {
            SootClass sc = Scene.v().getSootClassUnsafe(className);
            if (sc != null) {
                this.entrypoints.add(sc);
            }
        }
    }

    public HashMap<String, Integer> scanStringFromDex() throws IOException {
        HashMap<String, Integer> permissionCount = new HashMap<>();

        List<String> dexNames = DexFileFactory.loadDexContainer(new File(this.apkPath), Opcodes.getDefault()).getDexEntryNames();
        List<ClassDef> classDefList = new ArrayList<>();
        for (String dexName : dexNames) {
            DexBackedDexFile dexFile = DexFileFactory.loadDexEntry(new File(this.apkPath), dexName, true, Opcodes.getDefault()).getDexFile();
            for (ClassDef classDef : dexFile.getClasses()) {
                classDefList.add(classDef);
                for (Method method : classDef.getMethods()) {
                    Set<String> strings = getStringsFromMethod(method);
                    for (String tmpS : strings) {
                        for (String tmpKey : stringInformation.keySet()) {
                            if (tmpS.startsWith(tmpKey)) {
                                String permission = stringInformation.get(tmpKey);
                                if (permissionCount.containsKey(permission)) {
                                    permissionCount.put(permission, permissionCount.get(permission) + 1);
                                } else {
                                    permissionCount.put(permission, 1);
                                }
                            }
                        }
                    }
                }
            }
        }
        return permissionCount;
    }

    public ArrayList<ArrayList<String>> loadClasses() {
        ArrayList<ArrayList<String>> results = new ArrayList<ArrayList<String>>();
        CallGraph.init();
        DGraph dg = new DGraph();
        List<ValuePoint> allValuePoint = new ArrayList<>();
        ArrayList<String> allStrings = new ArrayList<>();

        for (SootClass sootClass : Scene.v().getClasses()) {
            for (SootMethod method : sootClass.getMethods()) {
                if (method.isConcrete()) {
                    Body body = method.retrieveActiveBody();
                    for (Unit unit : body.getUnits()) {
                        for (ValueBox box : unit.getUseAndDefBoxes()) {
                            if (box.getValue() instanceof StringConstant) {
                                StringConstant strConst = (StringConstant) box.getValue();
                                String value = strConst.value;
                                if (!value.isEmpty())
                                    allStrings.add(value);
                            }
                        }
                    }
                }
            }
        }
        allStrings.sort(Comparator.comparingInt(String::length).reversed());
        ArrayList<String> visitedSignature = new ArrayList<>();

        Map<SootMethod, SootClass> methodToClass = new HashMap<>();

        for (SootClass sootClass : Scene.v().getClasses()) {
            for (SootMethod method : sootClass.getMethods()) {
                if (method.isConcrete()) {
                    try {
                        methodToClass.put(method, sootClass);
                        Body body = method.retrieveActiveBody();
                        UnitGraph graph = new ExceptionalUnitGraph(body);
                        for (Unit unit : graph) {
                            for (ValueBox box : unit.getUseAndDefBoxes()) {
                                Value value = box.getValue();
                                if (value instanceof InvokeExpr) {
                                    InvokeExpr invokeExpr = (InvokeExpr) value;
                                    SootMethod invokedMethod = invokeExpr.getMethod();
                                    if (invokedMethod.getSignature().toLowerCase().contains("log")) {
                                        if (visitedSignature.contains(invokedMethod.getSignature())) {
                                            continue;
                                        }
                                        visitedSignature.add(invokedMethod.getSignature());
                                        List<Integer> paramList = new ArrayList<>();
                                        for (int i = 0; i < invokedMethod.getParameterCount(); ++i) {
                                            Type parameterType = invokedMethod.getParameterType(i);
                                            if (Objects.equals(parameterType.toString(), "java.lang.String")) {
                                                paramList.add(i);
                                            }
                                        }
                                        if (paramList.size() > 0) {
                                            List<ValuePoint> valuePoints = ValuePoint.find(dg, invokedMethod.getSignature(), paramList);
                                            allValuePoint.addAll(valuePoints);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing method " + method.getSignature() + ": " + e.getMessage());
                        throw e;
                    }
                }
            }
        }
        long startTime = System.currentTimeMillis();
        dg.solve(allValuePoint);
        long endTime = System.currentTimeMillis();
        System.out.println("Solve Time: " + (endTime - startTime) + "ms");
        startTime = endTime;
        for (ValuePoint vp : allValuePoint) {
            ValuePointResult vpResult = vp.retrieveResult();
            if (vpResult.hasResult() && !vpResult.isAllEmptyString()) {
                Set<String> possibleValues = vpResult.getPossibleValues();

                for (BackwardContext bc : vp.bcs) {
                    Unit lastUnit = bc.getStmtPathTail();
                    HashMap<String, ArrayList<String>> comeFrom = new HashMap<>();
                    for (Stmt stmt : bc.getStmtPath()) {
                        if (stmt instanceof ParameterTransferStmt) {
                            Value rightop = ((AssignStmt) stmt).getRightOp();
                            if (rightop instanceof InvokeExpr) {
                                InvokeExpr vie = (InvokeExpr) rightop;
                                String msig = vie.getMethod().toString();
                                if (msig.equals("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>")) {
                                    //GlobalStatistics.getInstance().countAppendString();
//                                    System.out.println(leftop);
//                                    System.out.println(((VirtualInvokeExpr) vie).getBase());
//                                    transferValuesAndAppend(stmt, ((VirtualInvokeExpr) vie).getBase(), leftop, vie.getArg(0), true, false);
                                } else if (msig.equals("<android.content.Context: java.lang.String getString(int)>") || msig.equals("<android.content.res.Resources: java.lang.String getString(int)>")) {
                                    //GlobalStatistics.getInstance().countGetString();
//                                    if (vie.getArg(0) instanceof IntConstant) {
//                                        setInitValue(leftop, "parser_todo", false);
//                                    } else if (this.getCurrentValues().get(vie.getArg(0)).size() > 0) {
//                                        for (String str : (HashSet<String>) this.getCurrentValues().get(vie.getArg(0)).clone()) {
//                                            this.getCurrentValues().remove(leftop);
//                                            if (OtherUtility.isInt(str)) {
//                                                setInitValue(leftop, "parser_todo", true);
//                                            } else {
//                                                logger.info(String.format("[%s] [SIMULATE][arg value not int getString(VirtualInvokeExpr)]: %s (%s)", this.hashCode(), stmt, str));
//                                            }
//                                        }
//                                    } else {
//                                        logger.info(String.format("[%s] [SIMULATE][arg not int getString(VirtualInvokeExpr)]: %s (%s)", this.hashCode(), stmt, vie.getArg(0).getClass()));
//                                    }
                                } else if (msig.equals("<java.lang.StringBuilder: java.lang.String toString()>")) {
//                                    transferValues(((VirtualInvokeExpr) vie).getBase(), leftop);
                                } else if (msig.equals("<java.lang.String: java.lang.String trim()>")) {
//                                    transferValues(((VirtualInvokeExpr) vie).getBase(), leftop);
                                } else if (msig.equals("<android.content.Context: java.lang.String getPackageName()>")) {
//                                    setInitValue(leftop, "package_name", false);
                                } else if (msig.equals("<android.content.res.Resources: int getIdentifier(java.lang.String,java.lang.String,java.lang.String)>")) {
//                                    this.getCurrentValues().remove(leftop);
//                                    for (String p1 : this.getContent(vie.getArg(0))) {
//                                        for (String p2 : this.getContent(vie.getArg(1))) {
//                                            // for (String p3 : this.getContent(vie.getArg(2)))
//                                            // {
//                                            setInitValue(leftop, "parser_todo", true);
//                                            // }
//                                        }
//                                    }

                                } else if (msig.equals("<java.lang.String: java.lang.String format(java.lang.String,java.lang.Object[])>")) {
                                    //GlobalStatistics.getInstance().countFormatString();
//                                    FunctionUtility.String_format(this, leftop, vie);
                                } else if (msig.equals("<java.lang.String: byte[] getBytes(java.nio.charset.Charset)>")
                                        || msig.equals("<java.lang.String: byte[] getBytes()>")
                                        || msig.equals("<java.lang.String: byte[] getBytes(java.lang.String)>")) {
//                                    transferValues(((VirtualInvokeExpr) vie).getBase(), leftop);
                                } else {
                                    //logger.info(String.format("[%s] [SIMULATE][right unknown(VirtualInvokeExpr)]: %s (%s)", this.hashCode(), stmt, rightop.getClass()));
                                }
                            } else if (rightop instanceof NewExpr) {

                            } else if (rightop instanceof FieldRef) {

                            } else if (rightop instanceof Local) {

                            } else if (rightop instanceof StringConstant) {

                            } else if (rightop instanceof IntConstant) {

                            }
                        } else {
                            if (stmt instanceof JAssignStmt) {
                                Value leftop = ((JAssignStmt) stmt).getLeftOp();
                                Value rightop = ((JAssignStmt) stmt).getRightOp();
                                if (rightop instanceof InvokeExpr) {
                                    InvokeExpr vie = (InvokeExpr) rightop;
                                    String msig = vie.getMethod().toString();
                                    if (msig.equals("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>")) {
                                        ArrayList<String> from = new ArrayList<>();
                                        String left = leftop.toString();
                                        String base = (((VirtualInvokeExpr) vie).getBase()).toString();
                                        String arg = ((VirtualInvokeExpr) vie).getArg(0).toString();
                                        if (comeFrom.containsKey(base)) {
                                            from.addAll(comeFrom.get(base));
                                        } else {
                                            from.add(base);
                                        }
                                        if (comeFrom.containsKey(arg)) {
                                            from.addAll(comeFrom.get(arg));
                                        } else {
                                            from.add(arg);
                                        }
                                        comeFrom.put(left, from);
                                    }
                                }
                            }
                        }
                        if (stmt == lastUnit) {
                            InvokeExpr invokeExpr = stmt.getInvokeExpr();
                            int parameterCount = invokeExpr.getMethod().getParameterCount();
                            int argCount = invokeExpr.getArgCount();
                            for (int i = 0; i < parameterCount; ++i) {
                                if (i < argCount &&
                                        Objects.equals(invokeExpr.getMethod().getParameterType(i).toString(), "java.lang.String")) {
                                    Value arg = stmt.getInvokeExpr().getArg(i);
                                }
                            }
                        }
                    }
                }
                SootMethod invokedMethod = vp.getMethod_location();
                System.out.println("Log In Method: " + invokedMethod);
                Set<Unit> preLogUnit = new HashSet<>();
                Map<String, List<String>> sources = new HashMap<>();
                if (invokedMethod.isConcrete()) {
                    try {
                        Body body = invokedMethod.retrieveActiveBody();
                        UnitGraph graph = new ExceptionalUnitGraph(body);
                        SimpleLocalDefs defs = new SimpleLocalDefs(graph);

                        List<Local> inputParamList = new ArrayList<>();
                        for (int i = 0; i < invokedMethod.getParameterCount(); i++) {
                            Local param = body.getParameterLocal(i);
                            inputParamList.add(param);
                        }

                        for (Unit unit : graph) {
                            if (!unit.equals(vp.getInstruction_location())) continue;
                            for (ValueBox box : unit.getUseAndDefBoxes()) {
                                Value value = box.getValue();
                                if (value instanceof InvokeExpr invokeExpr) {
                                    TraceLog traceLog = new TraceLog(invokeExpr, defs, graph, unit, preLogUnit, inputParamList, -1, 0, invokedMethod, new HashSet<>());
                                    traceLog.getTrace();
//                                    System.out.println("Method: " + invokeExpr + " Call By " + invokedMethod);
//                                    System.out.println("====== Log Parameter Sources ======");
                                    for (Map.Entry<String, Set<String>> entry : traceLog.getLogParameterLocalSources().entrySet()) {
                                        sources.put(entry.getKey(), entry.getValue().stream().toList());
//                                        System.out.println("Parameter: " + entry.getKey());
//                                        for (String source : entry.getValue()) {
//                                            System.out.println("  - " + source);
//                                        }
                                    }
//                                    System.out.println();
                                    preLogUnit.add(unit);
                                }
                            }
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }


                for (String tmpString : possibleValues) {
                    ArrayList<String> item = new ArrayList<>();
                    if (methodToClass.containsKey(invokedMethod))
                        item.add(methodToClass.get(invokedMethod).getName());
                    else
                        item.add("");
                    item.add(invokedMethod.getSignature());
                    item.add(vp.instruction_location.toString());
                    item.add(tmpString);
                    for (Map.Entry<String, List<String>> k : sources.entrySet()) {
                        if (k.getValue().isEmpty()) continue;
                        item.add(k.getKey() + ": " + String.join("|", k.getValue()));
                    }
                    results.add(item);
                }

            }
        }
        endTime = System.currentTimeMillis();
        System.out.println("Trace Log Time: " + (endTime - startTime) + "ms");
        System.out.println("Result length: " + results.size());
        startTime = endTime;

        ArrayList<ArrayList<String>> newResults = new ArrayList<>();
        for (ArrayList<String> resultItem : results) {
            int index = 0;
            ArrayList<String> newResultItem = new ArrayList<>();
            newResultItem.add(resultItem.get(0));
            newResultItem.add(resultItem.get(1));
            newResultItem.add(resultItem.get(2));
            String str = resultItem.get(3);
            List<String> splitResult = new ArrayList<>();
            String remaining = str;
            while (!remaining.isEmpty()) {
                boolean matched = false;
                for (String pattern : allStrings) {
                    if (remaining.startsWith(pattern)) {
                        splitResult.add(pattern);
                        remaining = remaining.substring(pattern.length());
                        matched = true;
                        break;
                    }
                }
                index++;
                if (index > 20) {
                    System.out.println("Split Result: " + splitResult);
                    System.out.println("Remaining String: " + remaining);
                    System.out.println("Fall In Dead Loop...");
                    break;
                }

                if (!matched) {
                    break;
                }

            }

            newResultItem.add(str);
            if (splitResult.size() > 0) {
                String tmpStr = splitResult.stream()
                        .map(s -> "\"" + s + "\"")
                        .collect(Collectors.joining(" + v + "));
                if (!remaining.isEmpty()) {
                    tmpStr = tmpStr + " + v + \"" + remaining + "\"";
                }
                newResultItem.add(tmpStr);
            } else {
                newResultItem.add(remaining);
            }
            if (resultItem.size() > 4) {
                for (int i = 4; i < resultItem.size(); ++i) {
                    newResultItem.add(resultItem.get(i));
                }
            }
            newResults.add(newResultItem);
        }
        endTime = System.currentTimeMillis();
        System.out.println("Build New Result Time: " + (endTime - startTime) + "ms");
//        System.out.println(newResults);
        System.out.println("Finish Loading Classes...");
//        System.out.println(newResults);
        return newResults;
    }

    private Set<String> getStringsFromMethod(Method method) {
        Set<String> stringSet = new HashSet<>();
        if (method == null || method.getImplementation() == null) {
            return stringSet;
        }

        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (instruction.getOpcode().equals(Opcode.CONST_STRING)) {
                assert instruction instanceof DexBackedInstruction21c;
                stringSet.add(((DexBackedInstruction21c) instruction).getReference().toString());
            } else if (instruction.getOpcode().equals(Opcode.CONST_STRING_JUMBO)) {
                assert instruction instanceof DexBackedInstruction31c;
                stringSet.add(((DexBackedInstruction31c) instruction).getReference().toString());
            }
        }
        return stringSet;
    }

    @Override
    protected IManifestHandler createManifestParser(File targetAPK) throws IOException, XmlPullParserException {
        return super.createManifestParser(targetAPK);
    }
}
