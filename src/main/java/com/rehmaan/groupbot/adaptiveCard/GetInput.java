package com.rehmaan.groupbot.adaptiveCard;

import com.microsoft.bot.builder.TurnContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


/**
 * A class that helps to extract the input data as [key, value] pairs from an adaptive card form and store it in a hashmap.
 *
 * @author mohammad rehmaan
 */
@Component
public class GetInput {

    /**
     * A user fills an adaptive card form.
     * This method is used to extract the input data as [key , value] pairs and store it in a hashmap
     * @param turnContext the turnContext object for the incoming activity triggered by form submit
     * @return user input in a hashmap
     */

    public static HashMap<String, String> getReceive(TurnContext turnContext) {
        HashMap<String, String> userInput = new HashMap<>();
        if (turnContext.getActivity().getValue() != null) {
            Object value = turnContext.getActivity().getValue();
            if (value instanceof Map) {
                Map<?, ?> mapData = (Map<?, ?>) value;
                for(Map.Entry entry : mapData.entrySet()) {
                    String key = (String) entry.getKey();
                    String val = (String) entry.getValue();
                    userInput.put(key, val);
                }
            }
        }
        return userInput;
    }
}