package com.rehmaan.groupbot.database;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpRetryException;

public class PriorityUpdateQueries {
    public static int getPriorityOldValue(String keyword, String documentId) throws Exception {
        String endpoint = "http://localhost:9200/" + ESClient.PRIORITY_INDEX_NAME + "/_doc/" + documentId;

        HttpClient httpClient = HttpClients.createDefault();

        HttpGet httpGet = new HttpGet(endpoint);

        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        int statusCode = response.getStatusLine().getStatusCode();
        if(statusCode == 200) {
            String responseBody = EntityUtils.toString(entity);
            JSONObject obj = new JSONObject(responseBody);
            if (obj.getJSONObject("_source").has(keyword)) {
                return obj.getJSONObject("_source").getInt(keyword);
            }
        } else {
            throw new Exception("could not update priority");
        }
        return 0;
    }

    public static String getPriorityMapId(String channelId) throws IOException, ParseException, HttpRetryException {
        String query = "{\n" +
                "  \"query\" : {\n" +
                "      \"match\": {\n" +
                "          \"channelId\" : \"" + channelId + "\"\n" +
                "      }\n" +
                "  }\n" +
                "}";
        String endpoint = "http://localhost:9200/" + ESClient.PRIORITY_INDEX_NAME + "/_search";
        JSONObject response= ESClient.sendElasticsearchHttpRequest(query, endpoint);
        if(response.getJSONObject("hits").getJSONArray("hits").length() == 0) {
            // add a new map for this channel id and get the id
            JSONObject priorityMap = new JSONObject();
            priorityMap.put("channelId", channelId);
            return ESClient.insertDocument("priority", priorityMap);
        }

        return response.getJSONObject("hits").getJSONArray("hits").getJSONObject(0).getString("_id");
    }

    public static JSONObject getPriorityMap(String channelId) throws IOException, ParseException, HttpRetryException {
        String query = "{\n" +
                "  \"query\" : {\n" +
                "      \"match\": {\n" +
                "          \"channelId\" : \"" + channelId + "\"\n" +
                "      }\n" +
                "  }\n" +
                "}";
        String endpoint = "http://localhost:9200/" + ESClient.PRIORITY_INDEX_NAME + "/_search";
        JSONObject response= ESClient.sendElasticsearchHttpRequest(query, endpoint);
        if(response.getJSONObject("hits").getJSONArray("hits").length() == 0) {
            // add a new map for this channel id and get the id
            JSONObject priorityMap = new JSONObject();
            priorityMap.put("channelId", channelId);
            ESClient.insertDocument("priority", priorityMap);
            return new JSONObject();
        }

        return response.getJSONObject("hits").getJSONArray("hits").getJSONObject(0).getJSONObject("_source");
    }

    public static void updatePriority(String keyword, int value, String channelId) throws Exception {
        String documentId =  getPriorityMapId(channelId);
        String endpoint = "http://localhost:9200/" + ESClient.PRIORITY_INDEX_NAME + "/_update/" + documentId;
        int oldValue = getPriorityOldValue(keyword, documentId);

        int delta = value-oldValue;
        String query = "{\n" +
                "  \"doc\": {\n" +
                "    \"" + keyword + "\": \"" + value + "\"\n" +
                "  }\n" +
                "}";

        ESClient.sendElasticsearchHttpRequest(query, endpoint);

        endpoint = "http://localhost:9200/" + ESClient.CODE_ALERTS_INDEX_NAME + "/_update_by_query";

        query = "{\n" +
                "    \"query\" : {\n" +
                "        \"bool\" : {\n" +
                "            \"filter\" : {\n" +
                "               \"match\": {\"channelId\" : \"" + channelId + "\"}\n" +
                "            },\n" +
                "            \"should\" : [\n" +
                "                {\"match\" : {\"environment\": \"" + keyword + "\"}}, \n" +
                "                {\"match\" : {\"stackTrace\": \"" + keyword + "\"}}\n" +
                "            ], \n" +
                "            \"minimum_should_match\" : 1\n" +
                "        }\n" +
                "    }, \n" +
                "    \"script\" : {\n" +
                "        \"source\": \"ctx._source.priority += params.value\",\n" +
                "        \"lang\": \"painless\",\n" +
                "        \"params\" : {\n" +
                "            \"value\" : " + delta+ "\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ESClient.sendElasticsearchHttpRequest(query, endpoint);
    }
}
