package com.rehmaan.groupbot.readAlerts;


import com.rehmaan.groupbot.database.ESClient;
import com.rehmaan.groupbot.messageParsing.AlertParser;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpRetryException;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

public class GetMessages {
    private static String indexName = "channel_reading_status_index";

    private static int maxTryToGetRefreshToken = 5;

    private static String clientId = "967c973b-c486-4d38-968a-3d964800821b";
    private static String clientSecret= "V4B8Q~R2zD6E~qs7oSCMVvWpK5lX~dkNx4nrWchl";


    public static void refreshAlertsGetMessages(String channelId) {
        try {
            List<JSONObject> alerts = GetMessages.readAlertFromChannel(channelId);
            for(JSONObject alert : alerts) {
                try {
                    JSONObject alertObject = AlertParser.getAlertObject(alert);
                    ESClient.insert(alertObject);
                }
                catch(Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
    }



    private static int compareTimestamps(String timestampString1, String timestampString2) {
        Instant timestamp1 = Instant.parse(timestampString1);
        Instant timestamp2 = Instant.parse(timestampString2);
        int ans= timestamp1.compareTo(timestamp2);

        if(ans < 0) {
            return -1;
        }
        else if(ans > 0) {
            return 1;
        }
        return 0;
    }


    private static Boolean isAlert(String body) {
        if(body.contains("Stacktrace") && body.contains("com.spr") && body.contains("Environment") && body.contains("Partner")) {
            return true;
        }
        return false;
    }

    public static List<JSONObject> readAlertFromChannel(String channelId) throws IOException{
        JSONObject channelReadingStatus = getChannelReadingStatus(channelId);
        String documentId = channelReadingStatus.getString("_id");

        channelReadingStatus = channelReadingStatus.getJSONObject("_source");

        String refreshToken = channelReadingStatus.getString("refreshToken");
        String lastReadTimestamp = channelReadingStatus.getString("lastReadTimestamp");
        String groupId = channelReadingStatus.getString("groupId");

        // decreases a bit of time and then make an api call
        String endpoint = "https://graph.microsoft.com/v1.0/teams/" + groupId + "/channels/" + channelId + "/messages/delta?$filter=lastModifiedDateTime%20gt%20" + lastReadTimestamp;

        JSONObject response = callGraphApi(endpoint, refreshToken);

        String newLastReadTimeStamp = new String(lastReadTimestamp);

        List<JSONObject> retrievedMessages = new ArrayList<>();

        while(response.getJSONArray("value").length() > 0) {
            JSONArray messages = response.getJSONArray("value");
            for(int i=0; i < messages.length(); i++) {
                JSONObject message = messages.getJSONObject(i);
                if(compareTimestamps(message.getString("lastModifiedDateTime"), lastReadTimestamp) <= 0) {
                    continue;
                }
                if(message.has("body") && message.getJSONObject("body").has("content")) {
                    JSONObject alert = new JSONObject();
                    String bodyContent = message.getJSONObject("body").getString("content");
                    if(isAlert(bodyContent)) {
                        alert.put("body", bodyContent);

                        String lastModifiedDateTime  = message.getString("lastModifiedDateTime");
                        alert.put("createdTime", lastModifiedDateTime);

                        alert.put("messageUrl", message.getString("webUrl"));

                        alert.put("messageId", message.getString("id"));

                        alert.put("channelId", channelId);

                        newLastReadTimeStamp = message.getString("lastModifiedDateTime");
                        retrievedMessages.add(alert);
                    }
                }
            }
            if(response.has("@odata.nextLink")) {
                endpoint = response.getString("@odata.nextLink");
                response = callGraphApi(endpoint, refreshToken);
            }

            else {
                break;
            }
        }

        System.out.println("updated timestamp is " + newLastReadTimeStamp);
        // update this timestamp into the channel_reading_status
        updateChannelReadingStatus(channelId, newLastReadTimeStamp, documentId);

        return retrievedMessages;
    }

    private static void updateChannelReadingStatus(String channelId, String lastReadTimestamp, String documentId) throws IOException, ParseException, HttpRetryException {
        String endpoint = "http://localhost:9200/" + indexName + "/_update/" + documentId;
        String query = "{\n" +
                "  \"doc\": {\n" +
                "    \"lastReadTimestamp\": \"" + lastReadTimestamp + "\"\n" +
                "  }\n" +
                "}";
        ESClient.sendElasticsearchHttpRequest(query, endpoint);
    }

    private static String getAccessToken(String refreshToken) throws IOException {
        String endpoint= "https://login.microsoftonline.com/common/oauth2/v2.0/token";
        HttpClient httpClient = HttpClients.createDefault();

        HttpPost httpPost = new HttpPost(endpoint);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", clientId));
        params.add(new BasicNameValuePair("scope", "offline_access channelmessage.read.all"));
        params.add(new BasicNameValuePair("client_secret", clientSecret));
        params.add(new BasicNameValuePair("grant_type", "refresh_token"));
        params.add(new BasicNameValuePair("refresh_token", refreshToken));
        httpPost.setEntity(new UrlEncodedFormEntity(params));

        HttpResponse response = httpClient.execute(httpPost);
        if(response.getStatusLine().getStatusCode() == 200) {
            HttpEntity responseEntity = response.getEntity();
            String responseBody = EntityUtils.toString(responseEntity);

            JSONObject responseObject = new JSONObject(responseBody);
            return responseObject.getString("access_token");
        }
        // now status code is not 200 so we have to try again
        return getAccessToken(refreshToken);
    }


    public static JSONObject callGraphApi(String endpoint, String refreshToken) throws IOException, HttpRetryException{

        String accessToken = getAccessToken(refreshToken);

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(endpoint);

        httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();

        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("status code is : " + statusCode);
        if(statusCode != 200) {
            System.out.println("into the status not ok");
            throw new HttpRetryException("error occured", statusCode);
        }

        String responseString = EntityUtils.toString(entity);
        System.out.println("everything is ok in make graph api call");
        httpClient.close();
        return new JSONObject(responseString);
    }

    private static JSONObject getChannelReadingStatus(String channelId) throws IOException, ParseException, HttpRetryException {
        String endpoint = "http://localhost:9200/" + indexName + "/_search";
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
        JSONObject response=  ESClient.sendElasticsearchHttpRequest(query, endpoint);
        return response.getJSONObject("hits").getJSONArray("hits").getJSONObject(0);
    }
}
