package com.rehmaan.groupbot.database;

import com.rehmaan.groupbot.messageParsing.StackTraceCompare;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.*;
import org.elasticsearch.xcontent.XContentType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpRetryException;

@Component
public class ESClient {
    public static final String CODE_ALERTS_INDEX_NAME = "code_alerts_index";
    public static final String PRIORITY_INDEX_NAME = "priority";
    public static final String CHANNEL_READING_STATUS_INDEX = "channel_reading_status_index";

    private static RestHighLevelClient client;
    private static RestHighLevelClient createClient() {
        RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")));
        return client;
    }
    private static void closeClient() throws IOException {
        client.close();
        client = null;
    }

    public static JSONObject sendElasticsearchHttpRequest(String query, String endpoint) throws IOException, ParseException, HttpRetryException {
        JSONObject responseObject = null;
        // Create HttpClient
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Create POST request
            HttpPost httpPost = new HttpPost(endpoint);
            StringEntity entity = new StringEntity(query, ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);

            // Execute the request
            CloseableHttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if(statusCode != 200) {
                throw new HttpRetryException("Request failed", statusCode);
            }
            // Get the response entity
            HttpEntity responseEntity = response.getEntity();
            // Convert the response entity to string
            String responseBody = EntityUtils.toString(responseEntity);
            responseObject = new JSONObject(responseBody);
        }
        return responseObject;
    }

    public static Boolean checkDuplicates(JSONObject data) throws  Exception{
        String query = "{\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"must\": [\n" +
                "        {\n" +
                "          \"term\": {\n" +
                "            \"isDuplicate\": false\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"match\": {\n" +
                "            \"stackTraceForFuzzy\": {\n" +
                "              \"query\": \"" + data.getString("stackTraceForFuzzy") + "\",\n" +
                "              \"fuzziness\": \"100\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}\n";

        String endpoint = "http://localhost:9200/" + CODE_ALERTS_INDEX_NAME +  "/_search";
        JSONObject response = sendElasticsearchHttpRequest(query, endpoint);

        JSONArray hits = response.getJSONObject("hits").getJSONArray("hits");
        for(int i=0; i < hits.length(); i++) {
            JSONObject hit = hits.getJSONObject(i).getJSONObject("_source");
            String stackTraceInDb = hit.getString("stackTrace");
            String stackTraceToInsert = data.getString("stackTrace");
            if(StackTraceCompare.isEqual(stackTraceInDb, stackTraceToInsert)) {
                data.put("uniqueId", hit.getString("uniqueId"));
                return true;
            }
        }
        return false;
    }

    public static void insert(JSONObject data) throws Exception {
        boolean isDuplicate = checkDuplicates(data);
        if(isDuplicate) {
            data.put("isDuplicate", true);
        }

        client = createClient();

        IndexRequest request = new IndexRequest(CODE_ALERTS_INDEX_NAME);
        request.source(data.toString(), XContentType.JSON);
        IndexResponse response= client.index(request, RequestOptions.DEFAULT);

        closeClient();

        if(!isDuplicate) {
            setUniqueId(response.getId(), response.getId());
        }
    }

    public static String insertDocument(String indexName, JSONObject data) throws IOException {
        client = createClient();

        IndexRequest request = new IndexRequest(indexName);
        request.source(data.toString(), XContentType.JSON);
        IndexResponse response= client.index(request, RequestOptions.DEFAULT);
        closeClient();
        return response.getId();
    }

    private static void setUniqueId(String documentId, String uniqueId) throws IOException, ParseException, HttpRetryException {
        String fieldName = "uniqueId";
        String fieldValue = uniqueId;
        String endpoint = "http://localhost:9200/" + CODE_ALERTS_INDEX_NAME + "/_update/" + documentId;
        String query = "{\"doc\": {\"" + fieldName + "\": \"" + fieldValue + "\"}}";
        sendElasticsearchHttpRequest(query, endpoint);
    }
}
