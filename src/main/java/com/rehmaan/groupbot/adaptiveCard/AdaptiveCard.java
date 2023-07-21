package com.rehmaan.groupbot.adaptiveCard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.Attachment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * this class is used to create an adaptive card activity from the JSON string that represents the adaptive card
 * @author Mohammad Rehmaan
 */
@Component
public class AdaptiveCard {

    /**
     *
     * @param adaptiveCardJson A string representing the json of the adaptive card that needs to be sent to the user.
     * @return an adaptive card activity made from the string adaptiveCardJson. this activity can be directly sent via turnContext.sendActivity()
     */
    public static Activity createAdaptiveCard(String adaptiveCardJson) {
        Attachment cardAttachment = new Attachment();
        cardAttachment.setContentType("application/vnd.microsoft.card.adaptive");
        try {
            cardAttachment.setContent(new ObjectMapper().readTree(adaptiveCardJson));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        Activity reply = MessageFactory.attachment(cardAttachment);

        Map<String, Object> channelData = new HashMap<>();
        channelData.put("isAdaptiveCard", true);
        reply.setChannelData(channelData);

        return reply;
    }
}
