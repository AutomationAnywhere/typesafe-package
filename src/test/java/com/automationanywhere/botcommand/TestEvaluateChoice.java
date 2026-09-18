package com.automationanywhere.botcommand;

import java.util.LinkedHashMap;
import java.util.Map;

import com.automationanywhere.botcommand.data.Value;
import com.automationanywhere.botcommand.data.impl.DictionaryValue;
import com.automationanywhere.botcommand.data.impl.StringValue;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestEvaluateChoice {

    @Test
    public void returnsChoiceForBillingIssue() {
        EvaluateChoice action = new EvaluateChoice();

        Map<String, Value> categories = new LinkedHashMap<>();
        categories.put("billing", new StringValue("Payment or subscription issues"));
        categories.put("technical", new StringValue("Bugs or integration problems"));
        categories.put("sales", new StringValue("Pricing or account questions"));

        Value<?> result = action.execute(
                TestSupport.apiKey(),
                "Hi, I've been trying to connect my Stripe account for 3 days and it keeps failing. "
                        + "I'm losing sales. Please help ASAP.",
                "Which team should handle this",
                categories,
                "jev-latest");

        assertTrue(result instanceof DictionaryValue, "Result should be a DictionaryValue");
        Map<String, Value> fields = ((DictionaryValue) result).get();

        assertEquals(fields.get("status").get(), "success");
        assertEquals(fields.get("error_message").get(), "");

        String choice = (String) fields.get("choice").get();
        assertTrue(categories.containsKey(choice), "choice should be one of the given categories, was " + choice);

        String probabilities = (String) fields.get("probabilities").get();
        assertTrue(probabilities.contains("billing") && probabilities.contains("technical")
                && probabilities.contains("sales"));

        double confidence = Double.parseDouble((String) fields.get("confidence").get());
        assertTrue(confidence >= 0.0 && confidence <= 1.0, "confidence should be in [0,1], was " + confidence);
    }
}
