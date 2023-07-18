package com.rehmaan.groupbot.authController;

import com.rehmaan.groupbot.database.ESClient;
import com.rehmaan.groupbot.readAlerts.GetMessages;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;

@RestController("")
public class AuthController {
    private static String channelReadingStatusIndex = "channel_reading_status_index";
    private static HashMap<String, String> AuthCodes = new HashMap<>();
    private static String clientId = "967c973b-c486-4d38-968a-3d964800821b";
    private static String clientSecret= "V4B8Q~R2zD6E~qs7oSCMVvWpK5lX~dkNx4nrWchl";

    @GetMapping("/auth")
    private String handleAuthAfterLogin(@RequestParam String code) {
        String uuid = UUID.randomUUID().toString();
        // store it in database for a short amount of time
        AuthCodes.put(uuid, code);
        return "Please tag the bot and send this message - " + "login " + uuid + " { your channel link so that we can set you up }";
    }

    public static void generateRefreshToken(String uuid, String channelId, String groupId) throws IOException {
        String endpoint= "https://login.microsoftonline.com/common/oauth2/v2.0/token";
        String code= AuthCodes.get(uuid);

        HttpClient httpClient = HttpClients.createDefault();

        HttpPost httpPost = new HttpPost(endpoint);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", clientId));
        params.add(new BasicNameValuePair("scope", "offline_access channelmessage.read.all"));
        params.add(new BasicNameValuePair("client_secret", clientSecret));
        params.add(new BasicNameValuePair("grant_type", "authorization_code"));
        params.add(new BasicNameValuePair("code", code));
        httpPost.setEntity(new UrlEncodedFormEntity(params));

        HttpResponse response = httpClient.execute(httpPost); // throws Ioexception
        HttpEntity responseEntity = response.getEntity();
        String responseBody = EntityUtils.toString(responseEntity);

        JSONObject responseObject = new JSONObject(responseBody);

        deleteTemporaryCode(uuid);

        String refreshToken = responseObject.getString("refresh_token");

        storeRefreshToken(refreshToken, channelId, groupId); // throws IOException

    }

    private static String getLastReadTimestamp(String channelId, String groupId, String refreshToken) throws IOException {
        String endpoint = "https://graph.microsoft.com/v1.0/teams/" + groupId + "/channels/" + channelId + "/messages?top=1";
        JSONObject response = GetMessages.callGraphApi(endpoint, refreshToken);
        return response.getJSONArray("value").getJSONObject(0).getString("lastModifiedDateTime");
    }

    public static void storeRefreshToken(String refreshToken, String channelId, String groupId) throws IOException {
        JSONObject channelReadingStatus = new JSONObject();
        channelReadingStatus.put("refreshToken", refreshToken);
        channelReadingStatus.put("channelId", channelId);
        channelReadingStatus.put("groupId", groupId);

        String lastReadTimestamp = getLastReadTimestamp(channelId, groupId, refreshToken);

        channelReadingStatus.put("lastReadTimestamp", lastReadTimestamp);

        ESClient.insertDocument(channelReadingStatusIndex, channelReadingStatus);
    }

    public static void deleteTemporaryCode(String uuid) {
        AuthCodes.remove(uuid);
    }

    public static Boolean isSignedIn(String channelId) throws IOException,ParseException {
        String endpoint = "http://localhost:9200/" + channelReadingStatusIndex + "/_search";
        String query = "{\n" +
                "    \"query\" : {\n" +
                "        \"bool\" : {\n" +
                "            \"must\" : [\n" +
                "                {\n" +
                "                    \"match\" : {\n" +
                "                        \"channelId\" : \"" + channelId + "\"\n" +
                "                    }\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}";
        JSONObject response= ESClient.sendElasticsearchHttpRequest(query, endpoint);
        if(response.getJSONObject("hits").getJSONObject("total").getInt("value") == 0) {
            return false;
        }
        return true;
    }
}
