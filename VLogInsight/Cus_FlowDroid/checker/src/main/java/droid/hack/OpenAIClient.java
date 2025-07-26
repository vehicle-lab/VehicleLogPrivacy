package droid.hack;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenAIClient {
    private static final String API_KEY = "";

    private static final String API_URL = "";
    private static final String MODEL = "";
    private static final String prompt = "你现在是一个精通flowdroid和soot框架的静态分析专家，我这里有个函数的方法体，我希望你能帮我判断该方法中是否进行了加密操作？你只需回答yes或no，你要分析的数据为：";

    public String getResponse(String methodBody) {
        try {
            JSONObject promptMessage = new JSONObject();
            promptMessage.put("role", "system");
            promptMessage.put("content", prompt);
            JSONObject bodyMessage = new JSONObject();
            bodyMessage.put("role", "user");
            bodyMessage.put("content", methodBody);
            JSONArray messages = new JSONArray();
            messages.put(promptMessage);
            messages.put(bodyMessage);
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", MODEL);
            requestBody.put("messages", messages);

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();
            Thread.sleep(500);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
                System.err.println("Request failed with message: " + response);
            JSONObject responseBody = new JSONObject(response.body());
            System.out.println("gpt response: " + responseBody);
            return responseBody.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

        } catch (IOException e) {
            System.err.println("process open ai API error..." + e.getMessage());
            System.out.println(e.getStackTrace());
        } catch (InterruptedException e) {
            System.err.println("process open ai API error..." + e.getMessage());
            System.out.println(e.getStackTrace());
        } catch (Exception e) {
            System.out.println(e);
        }
        return null;
    }

}
