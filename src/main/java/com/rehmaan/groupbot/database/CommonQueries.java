package com.rehmaan.groupbot.database;

import org.apache.http.ParseException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpRetryException;
import java.util.ArrayList;
import java.util.List;


/**
 * this class contains some common queries that cannot be attributed to a single feature of the bot
 *
 * @author mohammad rehmaan
 */
public class CommonQueries {
    private static String codeAlertsIndexName = IndexNames.getCodeAlertsIndexName();
    private static String channelReadingStatusIndexName = IndexNames.getChannelReadingStatusIndexName();



    /**
     * returns an alert by messageId
     *
     * @param messageId messageId of teams
     * @param channelId channelId of the teams
     * @return JSONObject for the alert. If no alert found then returns null
     * @throws IOException        thrown if call to database not successful
     * @throws ParseException     thrown if response from database cannot be parsed into a json object
     * @throws HttpRetryException thrown if status code returned was not 200 for the database call
     */
    public static JSONObject getByMessageId(String messageId, String channelId) throws IOException, ParseException, HttpRetryException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(QueryBuilders.termQuery("messageId", messageId));
        boolQueryBuilder.must(QueryBuilders.termQuery("channelId", channelId));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);

        SearchRequest searchRequest = new SearchRequest(codeAlertsIndexName);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        int code = searchResponse.status().getStatus();

        if (code != 200) {
            client.close();
            throw new HttpRetryException("error: status code " + code + " in getByMessageIdNew", code);
        }

        SearchHit[] hits = searchResponse.getHits().getHits();
        if (hits.length == 0) {
            return null;
        }

        client.close();
        return new JSONObject(hits[0].getSourceAsString());
    }




    /**
     * returns an alert by elastic search document id
     *
     * @param esId      elastic search document id
     * @param channelId channelId of teams
     * @return JSON object for the alert with the corresponding document id. if not found then returns null
     * @throws IOException        thrown if call to database not successful
     * @throws ParseException     thrown if response from database cannot be parsed into a json object
     * @throws HttpRetryException thrown if status code returned was not 200 for the database call
     */

    public static JSONObject getByESId(String esId, String channelId) throws IOException, ParseException, HttpRetryException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(QueryBuilders.termQuery("_id", esId));
        boolQueryBuilder.must(QueryBuilders.termQuery("channelId", channelId));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);

        SearchRequest searchRequest = new SearchRequest(codeAlertsIndexName);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        int code = searchResponse.status().getStatus();

        if (code != 200) {
            client.close();
            throw new HttpRetryException("error: status code " + code + " in getByEsIdNew", code);
        }

        SearchHit[] hits = searchResponse.getHits().getHits();
        if (hits.length == 0) {
            return null;
        }
        client.close();
        return new JSONObject(hits[0].getSourceAsString());
    }



    /**
     * Deletes an alert from the database
     *
     * @param id        Elastic search document id or the teams Message Id
     * @param isTeamsId boolean value to determine whether the id is teams message id or an elastic search document id
     * @param channelId channelId of teams
     * @throws IOException        thrown if call to database not successful
     * @throws ParseException     thrown if response from database cannot be parsed into a json object
     * @throws HttpRetryException thrown if status code returned was not 200 for the database call
     */

    public static void deleteByMessageId(String id, boolean isTeamsId, String channelId) throws IOException, ParseException, HttpRetryException {
        JSONObject alert = isTeamsId ? getByMessageId(id, channelId) : getByESId(id, channelId);
        if (alert == null) {
            return; // already deleted
        }

        RestHighLevelClient client = ElasticSearchRestClient.createClient();
        DeleteByQueryRequest request = new DeleteByQueryRequest(codeAlertsIndexName);

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(QueryBuilders.termQuery("uniqueId", alert.getString("uniqueId")));

        request.setQuery(boolQueryBuilder);
        BulkByScrollResponse response = client.deleteByQuery(request, RequestOptions.DEFAULT);
        int code = response.getStatus().hashCode();

        if (code != 200) {
            client.close();
            throw new HttpRetryException("error while deleting alerts from database", code);
        }
        client.close();
    }



    /**
     * returns a list of channels in which our bot is installed and user is signed in
     *
     * @return a list of strings that contains channelId of all channels in which our bot is installed and user is signed in
     * @throws IOException        thrown if call to database not successful
     * @throws ParseException     thrown if response from database cannot be parsed into a json object
     * @throws HttpRetryException thrown if status code returned was not 200 for the database call
     */
    public static List<String> getAllChannels() throws IOException, ParseException, HttpRetryException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);

        SearchRequest searchRequest = new SearchRequest(channelReadingStatusIndexName);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        int code = searchResponse.status().getStatus();

        if (code != 200) {
            client.close();
            throw new HttpRetryException("error status code : " + code + " in getAllChannelsNew function", code);
        }

        SearchHit[] hits = searchResponse.getHits().getHits();
        List<String> allChannels = new ArrayList<>();
        for (SearchHit hit : hits) {
            allChannels.add(hit.getSourceAsMap().get("channelId").toString());
        }

        client.close();
        return allChannels;
    }

}
