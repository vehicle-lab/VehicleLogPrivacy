package droid.hack;


import java.util.List;
import java.util.Map;

public class SensitiveData {
    private String classname;
    private String functionName;
    private String logClassname;
    private String logString;
    private int isSensitive;
    private List<String> sensitiveKeys;
    private String from;
    private Map<String, List<String>> paramToFunctions;
    private Map<String, Boolean> isEncrypted;

    public String getClassname() {
        return classname;
    }

    public void setClassname(String classname) {
        this.classname = classname;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getLogClassname() {
        return logClassname;
    }

    public void setLogClassname(String logClassname) {
        this.logClassname = logClassname;
    }

    public String getLogString() {
        return logString;
    }

    public void setLogString(String logString) {
        this.logString = logString;
    }

    public int getIsSensitive() {
        return isSensitive;
    }

    public void setSensitive(int sensitive) {
        isSensitive = sensitive;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public Map<String, List<String>> getParamToFunctions() {
        return paramToFunctions;
    }

    public void setParamToFunctions(Map<String, List<String>> paramToFunctions) {
        this.paramToFunctions = paramToFunctions;
    }

    public List<String> getSensitiveKeys() {
        return sensitiveKeys;
    }

    public void setSensitiveKeys(List<String> sensitiveKeys) {
        this.sensitiveKeys = sensitiveKeys;
    }

    @Override
    public String toString() {
        return "SensitiveData{" +
                "classname='" + classname + '\'' +
                ", functionName='" + functionName + '\'' +
                ", logClassname='" + logClassname + '\'' +
                ", logString='" + logString + '\'' +
                ", isSensitive=" + isSensitive +
                ", sensitiveKeys=" + sensitiveKeys +
                ", from='" + from + '\'' +
                ", paramToFunctions=" + paramToFunctions +
                '}';
    }

    public Map<String, Boolean> getIsEncrypted() {
        return isEncrypted;
    }

    public void setIsEncrypted(Map<String, Boolean> isEncrypted) {
        this.isEncrypted = isEncrypted;
    }
}
