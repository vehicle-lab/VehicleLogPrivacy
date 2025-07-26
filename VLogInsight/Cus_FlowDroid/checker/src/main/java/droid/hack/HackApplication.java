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
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;
import soot.Main;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.manifest.IManifestHandler;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.cfg.LibraryClassPatcher;
import soot.options.Options;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class HackApplication extends SetupApplication {
    private String apkPath;
    private String inputPath;
    private List<SensitiveData> sensitiveDataList = new ArrayList<>();
    private Map<String, Boolean> isEncryptedCache = new HashMap<>();
    private final OpenAIClient openAIClient = new OpenAIClient();

    private Map<String, String> stringInformation = Map.of(
            "content://call_log", "READ_CALL_LOG",
            "content://com.android.contacts", "READ_CONTACTS",
            "content://com.android.calendar", "READ_CALENDER",
            "content://sms", "READ_SMS"
    );

    public HackApplication(String androidJar, String apkFileLocation, String inputPath) {
        super(androidJar, apkFileLocation);
        this.apkPath = apkFileLocation;
        this.inputPath = inputPath;
        initializeSoot();
        inputData();
    }

    private void inputData() {
        try {
            FileReader reader = new FileReader(inputPath);
            StringBuilder jsonContent = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1) {
                jsonContent.append((char) ch);
            }
            JSONObject jsonObject = new JSONObject(jsonContent.toString());

            JSONArray results = jsonObject.getJSONArray("results");

            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);

                String classname = result.getString("classname");
                String functionName = result.getString("function_name");
                String logClassname = result.getString("log_classname");
                String logString = result.getString("log_string");
                int isSensitive = result.getInt("is_sensitive");


                SensitiveData sensitiveData = new SensitiveData();
                sensitiveData.setClassname(classname);
                sensitiveData.setFunctionName(functionName);
                sensitiveData.setLogClassname(logClassname);
                sensitiveData.setLogString(logString);
                sensitiveData.setSensitive(isSensitive);
                JSONArray sensitiveKeyArray = result.getJSONArray("sensitive_key");
                List<String> sensitiveKeys = new ArrayList<>();

                for (int j = 0; j < sensitiveKeyArray.length(); j++) {
                    sensitiveKeys.add(sensitiveKeyArray.getString(j));
                }
                sensitiveData.setSensitiveKeys(sensitiveKeys);

                if (result.has("from")) {
                    String from = result.getString("from");
                    sensitiveData.setFrom(from);
                    String[] firstSplit = from.substring(1, from.length() - 1).split("; ");
                    Map<String, List<String>> resultMap = new HashMap<>();

                    for (String item : firstSplit) {
                        int colonIndex = item.indexOf(":");
                        if (colonIndex != -1) {
                            String key = item.substring(0, colonIndex);

                            String[] valueArray = item.substring(colonIndex + 1).split("\\|");
                            List<String> functions = new ArrayList<>();

                            for (String value : valueArray) {
                                if (!value.isBlank() && value.trim().startsWith("Function call")) {
                                    functions.add(value.substring("Function call: ".length()));
                                }
                            }
                            resultMap.put(key, functions);
                        }
                    }
                    sensitiveData.setParamToFunctions(resultMap);
                }


                sensitiveDataList.add(sensitiveData);
            }
        } catch (IOException e) {
            System.err.println("Input Json File Error: " + e.getMessage());
        }
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
//                    System.out.println("============== " + method.getName() + " =============");
//                    System.out.println(strings);
                    for (String tmpS : strings) {
                        for (String tmpKey : stringInformation.keySet()) {
                            if (tmpS.startsWith(tmpKey)) {
                                // Has this string
                                String permission = stringInformation.get(tmpKey);
                                if (permissionCount.containsKey(permission)) {
                                    permissionCount.put(permission, permissionCount.get(permission) + 1);
                                } else {
                                    permissionCount.put(permission, 1);
                                }
                            }
                        }
                    }
//                    System.out.println(strings);
                }
            }
        }
        return permissionCount;
    }

    public List<SensitiveData> extractAndVerifyFunction() {
        CallGraph.init();

        for (SensitiveData sensitiveData : sensitiveDataList) {
            if (sensitiveData.getFrom() == null) continue;
            Map<String, List<String>> paramToFunctions = sensitiveData.getParamToFunctions();
            sensitiveData.setIsEncrypted(new HashMap<>());
            for (String param : paramToFunctions.keySet()) {
                boolean res = false;
                List<String> functions = paramToFunctions.get(param);
                for (String invokeExprStr : functions) {
                    String invokeExprSignature = invokeExprStr.substring(invokeExprStr.indexOf('<'), invokeExprStr.indexOf('>') + 1);
                    if (isEncryptedCache.containsKey(invokeExprSignature)) {
                        res |= isEncryptedCache.get(invokeExprSignature);
                        continue;
                    }
                    String[] split = invokeExprSignature.split(":");
                    String className = split[0].trim().substring(1);
                    SootClass sootClass = Scene.v().getSootClass(className);

                    if (sootClass != null) {
                        for (SootMethod method : sootClass.getMethods()) {
                            if (method.getSignature().equals(invokeExprSignature)) {
                                Boolean cur = dfsCheck(method, new HashSet<>());
                                res |= cur;
                            }
                        }
                    } else {
                        System.out.println("Class not found: " + className);
                    }
                }
                sensitiveData.getIsEncrypted().put(param, res);
            }
        }
        return sensitiveDataList;
    }

    private Boolean dfsCheck(SootMethod method, Set<SootMethod> visited) {
        if (isEncryptedCache.containsKey(method.getSignature()))
            return isEncryptedCache.get(method.getSignature());
        visited.add(method);
        Body body;
        if (method.isConcrete()) {
            body = method.retrieveActiveBody();
        } else if (method.isStatic() && method.hasActiveBody()) {
            body = method.getActiveBody();
        } else {
//            System.out.println("the method is not concrete or static");
            return Boolean.FALSE;
        }
//        System.out.println("method: " + method.getSignature() + " body: ");
//        System.out.println(body);

        String response = openAIClient.getResponse(body.toString());
//        System.out.println("method: " + method.getSignature() + " gpt response is encrypted: " + response);
        Boolean cur = "yes".equals(response);

        if (Boolean.TRUE.equals(cur)) {
            isEncryptedCache.put(method.getSignature(), Boolean.TRUE);
            return Boolean.TRUE;
        }

        CallGraphNode node = CallGraph.nodes.get(method.toString());
        HashSet<CallGraphNode> callTo = node.getCallTo();
        for (CallGraphNode callGraphNode : callTo) {
            SootMethod callToMethod = callGraphNode.smthd;
            if (visited.contains(callToMethod)) continue;
            visited.add(callToMethod);
            if (isEncryptedCache.containsKey(callToMethod.getSignature()))
                cur |= isEncryptedCache.get(callToMethod.getSignature());
            else
                cur |= dfsCheck(callToMethod, visited);
            if (Boolean.TRUE.equals(cur)) break;
        }
        isEncryptedCache.put(method.getSignature(), cur);
        return cur;
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
