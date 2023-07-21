package com.rehmaan.groupbot.messageParsing;


import com.rehmaan.groupbot.database.PriorityUpdateQueries;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * A class that makes JSON object for each alert
 *
 * @author mohammad rehmaan
 */

public class AlertParser {

    /**
     * Gets the alert object that has to be inserted in elastic search from the received alert.
     *
     * @param alertReceived The received alert.
     * @return The alert object.
     * @throws Exception If the stack trace could not be parsed.
     */
    public static JSONObject getAlertObject(JSONObject alertReceived) throws Exception{

        String message = alertReceived.getString("body");
        String messageUrl= alertReceived.getString("messageUrl");
        String messageId = alertReceived.getString("messageId");
        String createdTime = alertReceived.getString("createdTime");
        String channelId = alertReceived.getString("channelId");

        String stackTrace = HTMLContentParser.getStackTrace(message);
        HashMap<String, String> alertFieldValue = HTMLContentParser.convertHTMLTableToJSON(message);
        JSONObject alertObject = new JSONObject();

        if(stackTrace.length() > 0) {
            alertObject.put("stackTrace", stackTrace);
            alertObject.put("stackTraceForService", getServices(stackTrace));
            alertObject.put("stackTraceForFuzzy", getStackTraceForFuzzy(stackTrace));
        }

        alertObject.put("createdTime", createdTime);
        alertObject.put("isDuplicate", false);
        alertObject.put("messageUrl", messageUrl);
        alertObject.put("priority", calculatePriority(channelId, stackTrace));
        alertObject.put("messageId", messageId);
        alertObject.put("channelId", channelId);

        List<String> owners = new ArrayList<>();
        alertObject.put("owners", owners);


        for(Map.Entry<String, String> entry : alertFieldValue.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            key = modifyKey(key);
            alertObject.put(key, value);
        }

        return alertObject;
    }


    /**
     * Gets the stack trace for fuzzy search.
     *
     * @param stacktrace The stack trace.
     * @return The stack trace for fuzzy search.
     * @throws Exception If the stack trace could not be parsed.
     */
    public static String getStackTraceForFuzzy(String stacktrace) throws Exception{
        List<StackTraceElement> elements = StackTraceParser.parse(stacktrace).getStackTraceLines();
        StringBuilder stringBuilder = new StringBuilder();
        for(StackTraceElement element : elements) {
            if(element.getClassName().startsWith("com.spr")) {
                stringBuilder.append(element.toString() + " ");
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Gets the services from the stack trace.
     *
     * @param stackTrace The stack trace.
     * @return The services from the stack trace.
     * @throws Exception If the stack trace could not be parsed.
     */
    private static List<String> getServices(String stackTrace) throws Exception {
        List<StackTraceElement> elements = StackTraceParser.parse(stackTrace).getStackTraceLines();

        List<String> services= new ArrayList<>();
        for(StackTraceElement element : elements) {
            if(element.getClassName().startsWith("com.spr")) {
               String className = element.getClassName().toLowerCase();
               String[] arr = className.split("\\.");
               for(String keyword : arr) {
                   if(keyword.contains("service")) {
                       int index = keyword.indexOf("service");
                       String service = keyword.substring(0, index);
                       if(service.length() > 0) {
                           services.add(service);
                       }
                   }
               }
            }
        }
        return services.stream().distinct().collect(Collectors.toList());
    }

    public static String modifyKey(String key) {
        StringBuilder stringBuilder = new StringBuilder();
        key = key.toLowerCase();
        Boolean capital = false;
        for(Character c : key.toCharArray()) {
            if(c >='a' && c <='z') {
                if(capital) {
                    stringBuilder.append(Character.toUpperCase(c));
                    capital = false;
                }
                else {
                    stringBuilder.append(c);
                }
            }
            else if(c == ' ') {
                capital= true;
            }
        }
        return stringBuilder.toString();
    }

    public static int calculatePriority(String channelId, String stackTrace) throws IOException {
        int priorityValueForAlert=0;
        Map<String, Object> priorityMap = PriorityUpdateQueries.getPriorityMap(channelId).toMap();

        for(Map.Entry entry : priorityMap.entrySet()) {
            String key= (String) entry.getKey();
            String value = entry.getValue().toString();
            if(!key.equals("channelId")) {
                String keyword = key;
                int priorityValueForKeyword = Integer.parseInt(value);
                if(stackTrace.contains(keyword)) {
                    priorityValueForAlert += priorityValueForKeyword;
                }
            }
        }
        return priorityValueForAlert;
    }

}
