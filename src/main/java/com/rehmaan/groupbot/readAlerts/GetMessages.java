package com.rehmaan.groupbot.readAlerts;


import com.rehmaan.groupbot.database.ESClient;
import com.rehmaan.groupbot.database.ElasticSearchRestClient;
import com.rehmaan.groupbot.database.IndexNames;
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
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpRetryException;
import java.time.*;
import java.util.ArrayList;
import java.util.List;


/**
 * A class that reads messages from a channel and stores alerts in elastic search index
 *
 * @author mohammad rehmaan
 */


public class GetMessages {
    private static String channelReadingStatusIndex = IndexNames.getChannelReadingStatusIndexName();

    private static String clientId = "967c973b-c486-4d38-968a-3d964800821b";
    private static String clientSecret = "V4B8Q~R2zD6E~qs7oSCMVvWpK5lX~dkNx4nrWchl";


    /**
     * Refreshes the alerts in the Elasticsearch index from the messages in the channel.
     *
     * @param channelId The channel ID.
     */
    public static void refreshAlertsGetMessages(String channelId) {
        try {
            List<JSONObject> alerts = GetMessages.readAlertFromChannel(channelId);
            for (JSONObject alert : alerts) {
                try {
                    JSONObject alertObject = AlertParser.getAlertObject(alert);
                    ESClient.insert(alertObject);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    /**
     * Compares two timestamps.
     *
     * @param timestampString1 The first timestamp.
     * @param timestampString2 The second timestamp.
     * @return -1 if the first timestamp is less than the second timestamp, 1 if the first timestamp is greater than the second timestamp, and 0 if they are equal.
     */
    private static int compareTimestamps(String timestampString1, String timestampString2) {
        Instant timestamp1 = Instant.parse(timestampString1);
        Instant timestamp2 = Instant.parse(timestampString2);
        int ans = timestamp1.compareTo(timestamp2);

        if (ans < 0) {
            return -1;
        } else if (ans > 0) {
            return 1;
        }
        return 0;
    }


    private static Boolean isAlert(String body) {
        if (body.contains("Stacktrace") && body.contains("com.spr") && body.contains("Environment") && body.contains("Partner")) {
            return true;
        }
        return false;
    }


    /**
     * Reads the alerts from the channel.
     *
     * @param channelId The channel ID.
     * @return The list of alerts.
     * @throws IOException If the Graph API call failed.
     */
    public static List<JSONObject> readAlertFromChannel(String channelId) throws IOException {
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

        while (response.getJSONArray("value").length() > 0) {
            JSONArray messages = response.getJSONArray("value");
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.getJSONObject(i);
                if (compareTimestamps(message.getString("lastModifiedDateTime"), lastReadTimestamp) <= 0) {
                    continue;
                }
                if (message.has("body") && message.getJSONObject("body").has("content")) {
                    JSONObject alert = new JSONObject();
                    String bodyContent = message.getJSONObject("body").getString("content");
                    if (isAlert(bodyContent)) {
                        alert.put("body", bodyContent);

                        String lastModifiedDateTime = message.getString("lastModifiedDateTime");
                        alert.put("createdTime", lastModifiedDateTime);

                        alert.put("messageUrl", message.getString("webUrl"));

                        alert.put("messageId", message.getString("id"));

                        alert.put("channelId", channelId);

                        newLastReadTimeStamp = message.getString("lastModifiedDateTime");
                        retrievedMessages.add(alert);
                    }
                }
            }
            if (response.has("@odata.nextLink")) {
                endpoint = response.getString("@odata.nextLink");
                response = callGraphApi(endpoint, refreshToken);
            } else {
                break;
            }
        }

        updateChannelReadingStatus(channelId, newLastReadTimeStamp, documentId);

        return retrievedMessages;
    }


    /**
     * Updates the channel reading status.
     *
     * @param channelId         The channel ID.
     * @param lastReadTimestamp The last read timestamp.
     * @param documentId        The document ID.
     * @throws IOException        If the Elasticsearch request failed.
     * @throws ParseException     If the JSON query could not be parsed.
     * @throws HttpRetryException If the Elasticsearch request failed due to a retryable error.
     */
    private static void updateChannelReadingStatus(String channelId, String lastReadTimestamp, String documentId) throws IOException, ParseException, HttpRetryException {
        String endpoint = ElasticSearchRestClient.endPoint + channelReadingStatusIndex + "/_update/" + documentId;
        String query = "{\n" +
                "  \"doc\": {\n" +
                "    \"lastReadTimestamp\": \"" + lastReadTimestamp + "\"\n" +
                "  }\n" +
                "}";
        ESClient.sendElasticsearchHttpRequest(query, endpoint);

    }




    /**
     * Gets the access token from the refresh token.
     *
     * @param refreshToken The refresh token.
     * @return The access token.
     * @throws IOException If the HTTP request failed.
     */
    private static String getAccessToken(String refreshToken) throws IOException {
        String endpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
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
        if (response.getStatusLine().getStatusCode() == 200) {
            HttpEntity responseEntity = response.getEntity();
            String responseBody = EntityUtils.toString(responseEntity);

            JSONObject responseObject = new JSONObject(responseBody);
            return responseObject.getString("access_token");
        }
        // now status code is not 200 so we have to try again
        return getAccessToken(refreshToken);
    }



    /**
     * Calls the Graph API.
     *
     * @param endpoint     The Graph API endpoint.
     * @param refreshToken The refresh token.
     * @return The JSON response from the Graph API.
     * @throws IOException        If the HTTP request failed.
     * @throws HttpRetryException If the HTTP request failed due to a retryable error.
     */
    public static JSONObject callGraphApi(String endpoint, String refreshToken) throws IOException, HttpRetryException {

        String accessToken = getAccessToken(refreshToken);

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(endpoint);

        httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            throw new HttpRetryException("error occured", statusCode);
        }

        String responseString = EntityUtils.toString(entity);
        httpClient.close();
        return new JSONObject(responseString);
    }

    private static JSONObject getChannelReadingStatus(String channelId) throws IOException, ParseException, HttpRetryException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(QueryBuilders.termQuery("channelId", channelId));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);

        SearchRequest searchRequest = new SearchRequest(channelReadingStatusIndex);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        int code = searchResponse.status().getStatus();

        if (code != 200) {
            client.close();
            throw new HttpRetryException("error: status code " + code + " get channel reading status", code);
        }

        SearchHit[] hits = searchResponse.getHits().getHits();
        if (hits.length == 0) {
            return null;
        }

        client.close();
        return new JSONObject(hits[0].getSourceAsString());
    }
}
