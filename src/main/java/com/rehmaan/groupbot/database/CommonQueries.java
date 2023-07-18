package com.rehmaan.groupbot.database;

import org.apache.http.ParseException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpRetryException;
import java.util.ArrayList;
import java.util.List;

public class CommonQueries {
    public static JSONObject getByMessageId(String messageId, String channelId) throws IOException, ParseException, HttpRetryException{
        String endpoint= "http://localhost:9200/" + ESClient.CODE_ALERTS_INDEX_NAME + "/_search";
        String query = "{\n" +
                "    \"query\" : {\n" +
                "        \"bool\" : {\n" +
                "            \"must\" : [\n" +
                "                {\n" +
                "                    \"match\" : {\n" +
                "                        \"messageId\" : \"" + messageId + "\"\n" +
                "                    }\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"match\" : {\n" +
                "                        \"channelId\" : \"" + channelId + "\"\n" +
                "                    }\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}";
        JSONObject response =  ESClient.sendElasticsearchHttpRequest(query, endpoint);
        JSONArray hits= response.getJSONObject("hits").getJSONArray("hits");
        if(hits.length() == 0) {
            return null;
        }
        return hits.getJSONObject(0).getJSONObject("_source");
    }

    public static JSONObject getByESId(String esId, String channelId) throws IOException, ParseException, HttpRetryException{
        String endpoint= "http://localhost:9200/" + ESClient.CODE_ALERTS_INDEX_NAME + "/_search";
        String query = "{\n" +
                "    \"query\" : {\n" +
                "        \"bool\" : {\n" +
                "            \"must\" : [\n" +
                "                {\n" +
                "                    \"match\" : {\n" +
                "                        \"_id\" : \"" + esId + "\"\n" +
                "                    }\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"match\" : {\n" +
                "                        \"channelId\" : \"" + channelId + "\"\n" +
                "                    }\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}";
        JSONObject response =  ESClient.sendElasticsearchHttpRequest(query, endpoint);
        JSONArray hits= response.getJSONObject("hits").getJSONArray("hits");
        if(hits.length() == 0) {
            return null;
        }
        return hits.getJSONObject(0).getJSONObject("_source");
    }

    public static void deleteByMessageId(String id, boolean isTeamsId, String channelId) throws IOException, ParseException, HttpRetryException {
        JSONObject alert = null;
        if(isTeamsId) {
            alert = getByMessageId(id, channelId);
        }
        else {
            alert = getByESId(id, channelId);
        }
        if(alert == null) {
            return;
        }

        String uniqueId = alert.getString("uniqueId");
        String endpoint = "http://localhost:9200/" + ESClient.CODE_ALERTS_INDEX_NAME + "/_delete_by_query";

        String query= "{\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"must\": [\n" +
                "        {\n" +
                "          \"term\": {\n" +
                "            \"uniqueId\": \"" +  uniqueId + "\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"term\": {\n" +
                "            \"channelId\":\"" +  channelId + "\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}";
        ESClient.sendElasticsearchHttpRequest(query, endpoint);
    }

    public static List<String> getAllChannels() throws IOException , ParseException, HttpRetryException{
        String endpoint = "http://localhost:9200/" + ESClient.CHANNEL_READING_STATUS_INDEX + "/_search";
        JSONObject response = ESClient.sendElasticsearchHttpRequest("", endpoint);
        List<String> allChannelsList = new ArrayList<>();
        JSONArray hits = response.getJSONObject("hits").getJSONArray("hits");
        for(int i=0; i < hits.length(); i++) {
            JSONObject hit = hits.getJSONObject(i).getJSONObject("_source");
            String channelId = hit.getString("channelId");
            allChannelsList.add(channelId);
        }
        return allChannelsList;
    }
}
