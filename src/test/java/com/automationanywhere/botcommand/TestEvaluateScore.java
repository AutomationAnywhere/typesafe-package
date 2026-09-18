package com.automationanywhere.botcommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.automationanywhere.botcommand.data.Value;
import com.automationanywhere.botcommand.data.impl.DictionaryValue;
import com.automationanywhere.botcommand.data.impl.StringValue;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestEvaluateScore {

    @Test
    public void returnsScoreForAngryText() {
        EvaluateScore action = new EvaluateScore();

        List<Value> scaleLevels = new ArrayList<>();
        scaleLevels.add(new StringValue("Calm, just stating facts"));
        scaleLevels.add(new StringValue("Frustrated but civil"));
        scaleLevels.add(new StringValue("Very angry, strong language"));

        Value<?> result = action.execute(
                TestSupport.apiKey(),
                "Hi, I've been trying to connect my Stripe account for 3 days and it keeps failing. "
                        + "I'm losing sales. Please help ASAP.",
                "How frustrated the customer appears",
                scaleLevels,
                "jev-latest");

        assertTrue(result instanceof DictionaryValue, "Result should be a DictionaryValue");
        Map<String, Value> fields = ((DictionaryValue) result).get();

        assertEquals(fields.get("status").get(), "success");
        assertEquals(fields.get("error_message").get(), "");

        double score = Double.parseDouble((String) fields.get("score").get());
        assertTrue(score >= 0.0 && score <= (scaleLevels.size() - 1),
                "score should fall within the scale bounds, was " + score);

        String legend = (String) fields.get("legend").get();
        assertTrue(legend.contains("Calm") && legend.contains("angry"));

        double confidence = Double.parseDouble((String) fields.get("confidence").get());
        assertTrue(confidence >= 0.0 && confidence <= 1.0, "confidence should be in [0,1], was " + confidence);
    }
}
