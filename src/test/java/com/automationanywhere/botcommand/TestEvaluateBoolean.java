package com.automationanywhere.botcommand;

import java.util.Map;

import com.automationanywhere.botcommand.data.Value;
import com.automationanywhere.botcommand.data.impl.DictionaryValue;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestEvaluateBoolean {

    @Test
    public void returnsProbabilityForUrgentText() {
        EvaluateBoolean action = new EvaluateBoolean();

        Value<?> result = action.execute(
                TestSupport.apiKey(),
                "Hi, I've been trying to connect my Stripe account for 3 days and it keeps failing. "
                        + "I'm losing sales. Please help ASAP.",
                "Does this message express urgency?",
                "jev-latest");

        assertTrue(result instanceof DictionaryValue, "Result should be a DictionaryValue");
        Map<String, Value> fields = ((DictionaryValue) result).get();

        assertEquals(fields.get("status").get(), "success");
        assertEquals(fields.get("error_message").get(), "");
        assertEquals(fields.get("model").get(), "jev-latest");

        double probability = Double.parseDouble((String) fields.get("probability").get());
        assertTrue(probability >= 0.0 && probability <= 1.0, "probability should be in [0,1], was " + probability);
        assertTrue(probability > 0.5, "Urgent message should score a high probability, was " + probability);

        int inputTokens = Integer.parseInt((String) fields.get("input_tokens").get());
        assertTrue(inputTokens > 0);
        assertTrue(Integer.parseInt((String) fields.get("output_tokens").get()) >= 0);
        assertEquals(Integer.parseInt((String) fields.get("billable_tokens").get()), inputTokens,
                "billable_tokens should equal input_tokens (output is free for Jev)");
        assertTrue(Long.parseLong((String) fields.get("elapsed_ms").get()) > 0);
    }

    @Test
    public void returnsErrorEnvelopeOnInvalidApiKey() {
        EvaluateBoolean action = new EvaluateBoolean();

        Value<?> result = action.execute(
                new com.automationanywhere.core.security.SecureString("not-a-real-key".toCharArray()),
                "Some text.",
                "Is this a question?",
                "jev-latest");

        assertTrue(result instanceof DictionaryValue);
        Map<String, Value> fields = ((DictionaryValue) result).get();
        assertEquals(fields.get("status").get(), "error");
        assertTrue(((String) fields.get("error_message").get()).length() > 0);
    }
}
