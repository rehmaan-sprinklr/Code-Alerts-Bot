package com.rehmaan.groupbot.authController;

import com.rehmaan.groupbot.database.ESClient;
import com.rehmaan.groupbot.database.ElasticSearchRestClient;
import com.rehmaan.groupbot.database.IndexNames;
import com.rehmaan.groupbot.readAlerts.GetMessages;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.HttpRetryException;
import java.util.*;


/**
 * This class is used to login the user
 * This rest controller is used as a redirect url when the user logs in to microsoft account
 * @author Mohammad Rehmaan
 */
@RestController("")
public class AuthController {
    private static String channelReadingStatusIndex = IndexNames.getChannelReadingStatusIndexName();
    private static HashMap<String, String> AuthCodes = new HashMap<>();
    private static String clientId = "client id";
    private static String clientSecret= "client secret";


    @GetMapping("/auth")
    private String handleAuthAfterLogin(@RequestParam String code) {
        String uuid = UUID.randomUUID().toString();
        // store it in database for a short amount of time
        AuthCodes.put(uuid, code);
        return "Please tag the bot and send this message - " + "login " + uuid + " { your channel link so that we can set you up }";
    }


    /**
     * This function is used to do a request to Microsoft graph api to generate a refresh token for the user that has just signed in
     * The refresh token is then stored in a database
     * @param uuid unique id
     * @param channelId channelId of teams
     * @param groupId groupId of teams
     * @throws IOException throws Exception if API call to graph was not successful
     */
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


    /**
     * This function is used to store the refresh token for a particular channelId after the user has signed in
     * This refresh token will be used later to generate access token and then make api calls to fetch messages from the channel
     * @param refreshToken refresh token
     * @param channelId channelId of teams
     * @param groupId groupId of teams
     * @throws IOException
     */
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



    /**
     * This function checks if the user is already signed in by calling the database and checking whether user exists in it or not
     * When the user attempts a sign in , first this function is run to see if user is already signed in
     * @param channelId channelId of teams
     * @return boolean - whether the user is already signed in or not
     * @throws IOException thrown if call to database is not successful
     * @throws ParseException thrown if response from database cannot be parsed into an JSON Object
     */
    public static Boolean isSignedIn(String channelId) throws IOException,ParseException {
        RestHighLevelClient client = ElasticSearchRestClient.createClient();

        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("channelId", channelId);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().must(matchQuery);

        SearchRequest searchRequest = new SearchRequest(channelReadingStatusIndex);
        searchRequest.source().query(boolQuery);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        if(searchResponse.status().getStatus() != 200) {
            client.close();
            throw new HttpRetryException("error in sign in process", searchResponse.status().getStatus());
        }

        client.close();
        if(searchResponse.getHits().getHits().length == 0) {
            return false;
        }

        return true;
    }

}
