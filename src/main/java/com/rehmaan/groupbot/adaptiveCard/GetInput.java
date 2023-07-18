package com.rehmaan.groupbot.adaptiveCard;

import com.microsoft.bot.builder.TurnContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class GetInput {

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