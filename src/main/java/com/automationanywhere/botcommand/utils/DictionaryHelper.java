package com.automationanywhere.botcommand.utils;

import java.util.LinkedHashMap;

import com.automationanywhere.botcommand.data.Value;
import com.automationanywhere.botcommand.data.impl.DictionaryValue;
import com.automationanywhere.botcommand.data.impl.StringValue;
import org.json.JSONObject;

/**
 * Builds the standard result envelope every EvaluateX action returns: action-specific
 * fields first, then model/usage/status/error_message, in that order.
 */
public class DictionaryHelper {

    private DictionaryHelper() {
    }

    public static DictionaryValue success(LinkedHashMap<String, Value> fields, String model, JSONObject usage,
            long elapsedMs) {
        int inputTokens = usage.optInt("input_tokens", 0);
        fields.put("model", new StringValue(model));
        fields.put("input_tokens", new StringValue(String.valueOf(inputTokens)));
        fields.put("output_tokens", new StringValue(String.valueOf(usage.optInt("output_tokens", 0))));
        // Jev's output tokens are not billed, so input tokens are the whole cost-relevant number.
        fields.put("billable_tokens", new StringValue(String.valueOf(inputTokens)));
        fields.put("elapsed_ms", new StringValue(String.valueOf(elapsedMs)));
        fields.put("status", new StringValue("success"));
        fields.put("error_message", new StringValue(""));
        return new DictionaryValue(fields);
    }

    public static DictionaryValue error(LinkedHashMap<String, Value> emptyFields, String model, String message) {
        emptyFields.put("model", new StringValue(model));
        emptyFields.put("input_tokens", new StringValue("0"));
        emptyFields.put("output_tokens", new StringValue("0"));
        emptyFields.put("billable_tokens", new StringValue("0"));
        emptyFields.put("elapsed_ms", new StringValue("0"));
        emptyFields.put("status", new StringValue("error"));
        emptyFields.put("error_message", new StringValue(message));
        return new DictionaryValue(emptyFields);
    }
}
