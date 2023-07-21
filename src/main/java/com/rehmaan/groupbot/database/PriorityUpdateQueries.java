package com.rehmaan.groupbot.database;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpRetryException;
import java.util.Collections;


/**
 * A class that helps to update priorities of keywords in the priority index.
 *
 * @author mohammad rehmaan
 */
public class PriorityUpdateQueries {

    private static final String priorityIndexName = IndexNames.getPriorityIndexNameIndexName();

    /**
     * Gets the old priority value for a keyword in the priority index of a channel
     *
     * @param keyword The keyword of the priority value to get.
     * @param documentId The ID of the document to get the priority value for.
     * @return The old priority value, or 0 if the document does not exist or the keyword is not found.
     * @throws Exception If an error occurs.
     */
    public static int getPriorityOldValue(String keyword, String documentId) throws Exception {
        String endpoint = ElasticSearchRestClient.endPoint + priorityIndexName + "/_doc/" + documentId;

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


    /**
     * Gets the ID of the priority map for the specified channel ID.
     *
     * @param channelId The channel ID.
     * @return The ID of the priority map, or null if no priority map exists for the channel ID.
     * @throws IOException If an I/O error occurs.
     * @throws ParseException If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static String getPriorityMapId(String channelId) throws IOException, ParseException, HttpRetryException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();

        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("channelId", channelId);

        SearchRequest searchRequest = new SearchRequest(priorityIndexName);
        searchRequest.source().query(matchQuery);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        if(searchResponse.status().getStatus() != 200) {
            client.close();
            throw new HttpRetryException(  "error cannot update priority",searchResponse.status().getStatus());
        }

        if(searchResponse.getHits().getHits().length == 0) {
            JSONObject priorityMap = new JSONObject();
            priorityMap.put("channelId", channelId);
            return ESClient.insertDocument(priorityIndexName, priorityMap);
        }

        client.close();
        return searchResponse.getHits().getHits()[0].getId();
    }


    /**
     * Gets the priority map for the specified channel ID.
     *
     * @param channelId The channel ID.
     * @return The priority map, or an empty JSON object if no priority map exists for the channel ID.
     * @throws IOException If an I/O error occurs.
     * @throws ParseException If a parsing error occurs.
     * @throws HttpRetryException If an HTTP error occurs.
     */
    public static JSONObject getPriorityMap(String channelId) throws IOException, ParseException, HttpRetryException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();


        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("channelId", channelId);

        SearchRequest searchRequest = new SearchRequest(priorityIndexName);
        searchRequest.source().query(matchQuery);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        if(searchResponse.status().getStatus() != 200) {
            client.close();
            throw new HttpRetryException("error cannot update priority", searchResponse.status().getStatus());
        }
        if(searchResponse.getHits().getHits().length == 0) {
            JSONObject priorityMap = new JSONObject();
            priorityMap.put("channelId", channelId);
            ESClient.insertDocument(priorityIndexName, priorityMap);
            return new JSONObject();
        }
        client.close();
        return new JSONObject(searchResponse.getHits().getHits()[0].getSourceAsString());
    }


    /**
     * Updates the priority of the specified keyword for the specified channel ID.
     *
     * @param keyword The keyword to update the priority of.
     * @param value The new priority value.
     * @param channelId The channel ID.
     * @throws Exception If an error occurs.
     */
    public static void updatePriority(String keyword, int value, String channelId) throws Exception {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();
        String documentId = getPriorityMapId(channelId);
        int oldValue = getPriorityOldValue(keyword, documentId);
        int delta = value-oldValue;

        XContentBuilder docBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .field(keyword, value)
                .endObject();

        UpdateRequest updateRequest = new UpdateRequest(priorityIndexName, documentId)
                .doc(docBuilder)
                .fetchSource(true);

        UpdateResponse updateResponse = client.update(updateRequest, RequestOptions.DEFAULT);

        if(updateResponse.status().getStatus() != 200) {
            client.close();
            throw new HttpRetryException( "error cannot update priority", updateResponse.status().getStatus());
        }


        MatchQueryBuilder channelIdMatchQuery = QueryBuilders.matchQuery("channelId", channelId);
        MatchQueryBuilder environmentMatchQuery = QueryBuilders.matchQuery("environment", keyword);
        MatchQueryBuilder stackTraceMatchQuery = QueryBuilders.matchQuery("stackTrace", keyword);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .filter(channelIdMatchQuery)
                .should(environmentMatchQuery)
                .should( stackTraceMatchQuery)
                .minimumShouldMatch(1);

        String script = "ctx._source.priority += params.value";

        UpdateByQueryRequest updateByQueryRequest = new UpdateByQueryRequest("code_alerts_index")
                .setQuery(boolQuery)
                .setScript(
                        new Script(
                                ScriptType.INLINE,
                                "painless",
                                script,
                                Collections.singletonMap("value", delta)
                        )
                );

        BulkByScrollResponse updateByQueryResponse = client.updateByQuery(updateByQueryRequest, RequestOptions.DEFAULT);
        if(updateByQueryResponse.getStatus().hashCode() != 200) {
            client.close();
            throw  new HttpRetryException( "error cannot update priority", updateByQueryResponse.getStatus().hashCode());
        }
        client.close();
    }

}
