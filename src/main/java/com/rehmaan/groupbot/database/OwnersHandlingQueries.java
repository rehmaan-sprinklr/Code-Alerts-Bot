package com.rehmaan.groupbot.database;

import org.apache.http.ParseException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpRetryException;
import java.util.List;

public class OwnersHandlingQueries {
    public static void assignOwners(List<String> ownerIds, String channelId, String id, boolean isTeamsId) throws IOException, ParseException, HttpRetryException {
        JSONObject alert = null;
        if(isTeamsId) {
            alert = CommonQueries.getByMessageId(id, channelId);
        }
        else {
            alert = CommonQueries.getByESId(id, channelId);
        }
        if(alert == null) {
            // important handle this in bot callback handler
            return;
        }
        String documentId = alert.getString("uniqueId");
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");

        for(String owner : ownerIds) {
            stringBuilder.append( "\"" + owner + "\",");
        }

        if(ownerIds.size() > 0) {
            stringBuilder.deleteCharAt(stringBuilder.length()-1);
        }
        stringBuilder.append("]");

        String stringOfOwnersArray = stringBuilder.toString();

        String query = "{\n" +
                "\"query\": {\n" +
                "    \"match\": {\n" +
                "      \"uniqueId\": \"" + documentId + "\"\n" +
                "    }\n" +
                "  },"+
                "  \"script\": {\n" +
                "    \"inline\": \"ctx._source.owners.addAll(params.value); ctx._source.owners = ctx._source.owners.stream().distinct().collect(Collectors.toList())\",\n" +
                "    \"lang\": \"painless\",\n" +
                "    \"params\": {\n" +
                "      \"value\":" + stringOfOwnersArray +  "\n" +
                "    }\n" +
                "  }\n" +
                "}";
        String endpoint = "http://localhost:9200/" + ESClient.CODE_ALERTS_INDEX_NAME + "/_update_by_query/";
        ESClient.sendElasticsearchHttpRequest(query, endpoint);
    }

    // OK Done
    public static void removeOwners(List<String> ownerIds, String id, String channelId, boolean isTeamsId) throws  IOException, ParseException, HttpRetryException{
        JSONObject alert = null;
        if(isTeamsId) {
            alert = CommonQueries.getByMessageId(id, channelId);
        }
        else {
            alert = CommonQueries.getByESId(id, channelId);
        }
        if(alert == null) {
            // important handle this in bot callback handler
            return;
        }

        String documentId = alert.getString("uniqueId");
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");

        for(String owner : ownerIds) {
            stringBuilder.append( "\"" + owner + "\",");
        }

        if(ownerIds.size() > 0) {
            stringBuilder.deleteCharAt(stringBuilder.length()-1);
        }
        stringBuilder.append("]");

        String stringOfOwnersArray = stringBuilder.toString();

        String query = "{\n" +
                "\"query\": {\n" +
                "    \"match\": {\n" +
                "      \"uniqueId\": \"" + documentId + "\"\n" +
                "    }\n" +
                "  },"+
                "  \"script\": {\n" +
                "    \"inline\": \"ctx._source.owners.removeAll(params.value); ctx._source.owners = ctx._source.owners.stream().distinct().collect(Collectors.toList())\",\n" +
                "    \"lang\": \"painless\",\n" +
                "    \"params\": {\n" +
                "      \"value\":" + stringOfOwnersArray +  "\n" +
                "    }\n" +
                "  }\n" +
                "}";
        String endpoint = "http://localhost:9200/" + ESClient.CODE_ALERTS_INDEX_NAME + "/_update_by_query/";
        ESClient.sendElasticsearchHttpRequest(query, endpoint);
    }




}
