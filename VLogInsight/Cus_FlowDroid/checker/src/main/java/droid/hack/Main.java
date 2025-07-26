package droid.hack;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 4) {
            return;
        }
        HackApplication application = new HackApplication(args[0], args[1], args[2]);
        String outputPath = args[3];
        try {
            application.parseAppResources();
            HashMap<String, Integer> stringResults = application.scanStringFromDex();

            List<SensitiveData> executeResults = application.extractAndVerifyFunction();
            ArrayList<HashMap<String, Object>> normalizedResults = new ArrayList<>();
            for (SensitiveData resultItem: executeResults) {
                HashMap<String, Object> tmpItem = new HashMap<>();
                tmpItem.put("classname", resultItem.getClassname());
                tmpItem.put("function_name", resultItem.getFunctionName());
                tmpItem.put("log_classname", resultItem.getLogClassname());
                tmpItem.put("log_string", resultItem.getLogString());
                tmpItem.put("is_sensitive", resultItem.getIsSensitive());
                tmpItem.put("sensitive_key", resultItem.getSensitiveKeys().toArray());
                if (resultItem.getFrom() != null) {
                    tmpItem.put("from", resultItem.getFrom());
                    List<String> isEncrypted = new ArrayList<>();
                    for (String param : resultItem.getIsEncrypted().keySet()) {
                        isEncrypted.add(param + ": " + resultItem.getIsEncrypted().get(param));
                    }
                    tmpItem.put("isEncrypted", isEncrypted.toArray());
                }
                normalizedResults.add(tmpItem);
            }
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputPath),"UTF-8");
            JSONObject obj = new JSONObject();
            obj.put("results", normalizedResults);
            osw.write(obj.toString());
            osw.flush();
            osw.close();

        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw e;
        }
    }
}