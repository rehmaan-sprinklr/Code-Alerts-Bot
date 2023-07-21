package com.rehmaan.groupbot.database;

import com.rehmaan.groupbot.messageParsing.StackTraceCompare;
import org.apache.http.HttpEntity;
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
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.*;
import org.elasticsearch.xcontent.XContentType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpRetryException;

/**
 * A class that helps to interact with the Elasticsearch database.
 *
 * @author mohammad rehmaan
 */
@Component
public class ESClient {
    public static final String codeAlertsIndexName = IndexNames.getCodeAlertsIndexName();

    /**
     * Makes a request to elastic search
     *
     * @param query    String that describes the query that needs to be performed (this will be the body of the request)
     * @param endpoint endpoint at which we need to make request
     * @return response sent by elastic search as JSONObject
     * @throws IOException        thrown if elastic search call was not successful
     * @throws ParseException     thrown if response received was not parsed to json object
     * @throws HttpRetryException thrown if status code was not 200 while database call
     */

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
            if (statusCode != 200) {
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


    /**
     * checks whether an alert contains a duplicate alert already in the database
     *
     * @param alert this is the alert JSON object that is to be checked whether it has any duplicated in database or not
     * @return true if a duplicate alert exists . false if no duplicate alert exists in database
     * @throws Exception thrown if call to elastic search database not successful
     */

    public static Boolean checkDuplicates(JSONObject alert) throws Exception {
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
                "          \"term\": {\n" +
                "            \"channelId\": \"" + alert.getString("channelId") + "\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"match\": {\n" +
                "            \"stackTraceForFuzzy\": {\n" +
                "              \"query\": \"" + alert.getString("stackTraceForFuzzy") + "\",\n" +
                "              \"fuzziness\": \"100\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}\n";


        String endpoint = ElasticSearchRestClient.endPoint + codeAlertsIndexName + "/_search";
        JSONObject response = sendElasticsearchHttpRequest(query, endpoint);

        JSONArray hits = response.getJSONObject("hits").getJSONArray("hits");
        for (int i = 0; i < hits.length(); i++) {
            JSONObject hit = hits.getJSONObject(i).getJSONObject("_source");
            String stackTraceInDb = hit.getString("stackTrace");
            String stackTraceToInsert = alert.getString("stackTrace");
            if (StackTraceCompare.isEqual(stackTraceInDb, stackTraceToInsert)) {
                alert.put("uniqueId", hit.getString("uniqueId"));
                return true;
            }
        }
        return false;
    }


    /**
     * Inserts an alert in the elastic search database
     *
     * @param alert alert is the alert that needs to be inserted
     * @throws Exception thrown if call to elastic search database was not successful
     */

    public static void insert(JSONObject alert) throws Exception {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();
        boolean isDuplicate = checkDuplicates(alert);
        if (isDuplicate) {
            alert.put("isDuplicate", true);
        }

        IndexRequest request = new IndexRequest(codeAlertsIndexName);
        request.source(alert.toString(), XContentType.JSON);
        IndexResponse response = client.index(request, RequestOptions.DEFAULT);

        client.close();

        if (!isDuplicate) {
            JSONObject updateResponse = setUniqueId(response.getId(), response.getId());
        }
    }

    /**
     * Insert any JSONObject data , in elastic search indexName
     *
     * @param indexName name of index in which data has to be inserted
     * @param data      JSONObject that has to be inserted
     * @return elastic search id of the document after it was inserted
     * @throws IOException thrown if call to elastic search database not successful
     */

    public static String insertDocument(String indexName, JSONObject data) throws IOException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();

        IndexRequest request = new IndexRequest(indexName);
        request.source(data.toString(), XContentType.JSON);
        IndexResponse response = client.index(request, RequestOptions.DEFAULT);


        client.close();
        return response.getId();
    }


    /**
     * Sets the root id of an alert that is inserted in the elastic search index
     *
     * @param documentId document id of alert (elastic search)
     * @param uniqueId   root id
     * @throws IOException        thrown if call to database not successful
     * @throws ParseException     thrown if response was not parsed successfully
     * @throws HttpRetryException thrown if status code was not 200
     */
    private static JSONObject setUniqueId(String documentId, String uniqueId) throws IOException, ParseException, HttpRetryException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();
        UpdateRequest updateRequest = new UpdateRequest(codeAlertsIndexName, documentId).doc("{\"uniqueId\": \"" + uniqueId + "\"}", XContentType.JSON);
        UpdateResponse response = client.update(updateRequest, RequestOptions.DEFAULT);
        int code = response.status().getStatus();
        if (code != 200) {
            client.close();
            throw new HttpRetryException("error occured while setting root id of the alert", code);
        }
        client.close();
        return new JSONObject(response.toString());
    }
}
