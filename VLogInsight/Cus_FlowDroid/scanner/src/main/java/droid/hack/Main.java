package droid.hack;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            return;
        }
        HackApplication application = new HackApplication(args[0], args[1]);
        String outputPath = args[2];
        try {
            application.parseAppResources();
            HashMap<String, Integer> stringResults = application.scanStringFromDex();

            ArrayList<ArrayList<String>> executeResults = application.loadClasses();

            HashMap<String, ArrayList> results = new HashMap<>();
            ArrayList<HashMap<String, String>> normalizedResults = new ArrayList<>();
            for (ArrayList<String> resultItem: executeResults) {
                System.out.println(resultItem);
                HashMap<String, String> tmpItem = new HashMap<>();
                tmpItem.put("classname", resultItem.get(0));
                tmpItem.put("function_name", resultItem.get(1));
                tmpItem.put("log_classname", resultItem.get(2));
                tmpItem.put("log_string", resultItem.get(4));
                if (resultItem.size() > 5) {
                    tmpItem.put("from", "[" + String.join("; ", resultItem.subList(5, resultItem.size())) + "]");
                }
                normalizedResults.add(tmpItem);
            }
            results.put("results", normalizedResults);

            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputPath),"UTF-8");
            JSONObject obj = new JSONObject();
            obj.put("results", results);
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