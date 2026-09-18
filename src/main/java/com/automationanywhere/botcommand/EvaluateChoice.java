package com.automationanywhere.botcommand;

import java.util.LinkedHashMap;
import java.util.Map;

import com.automationanywhere.botcommand.data.Value;
import com.automationanywhere.botcommand.data.impl.DictionaryValue;
import com.automationanywhere.botcommand.data.impl.StringValue;
import com.automationanywhere.botcommand.utils.DictionaryHelper;
import com.automationanywhere.botcommand.utils.TypeSafeClient;
import com.automationanywhere.commandsdk.annotations.BotCommand;
import com.automationanywhere.commandsdk.annotations.CommandPkg;
import com.automationanywhere.commandsdk.annotations.Execute;
import com.automationanywhere.commandsdk.annotations.Idx;
import com.automationanywhere.commandsdk.annotations.Pkg;
import com.automationanywhere.commandsdk.annotations.rules.NotEmpty;
import com.automationanywhere.commandsdk.model.AllowedTarget;
import com.automationanywhere.core.security.SecureString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import static com.automationanywhere.commandsdk.model.AttributeType.CREDENTIAL;
import static com.automationanywhere.commandsdk.model.AttributeType.TEXT;
import static com.automationanywhere.commandsdk.model.AttributeType.TEXTAREA;
import static com.automationanywhere.commandsdk.model.DataType.DICTIONARY;
import static com.automationanywhere.commandsdk.model.DataType.STRING;

/**
 * Asks a categorical ("choice") question about a piece of text and returns the
 * selected category plus the probability distribution over all categories.
 */
@BotCommand
@CommandPkg(
        name = "evaluateChoice",
        label = "Evaluate Choice",
        description = "Asks a categorical question about text and returns the selected category.",
        node_label = "{{question}}",
        icon = "pkg.png",
        comment = true,
        allowed_agent_targets = {AllowedTarget.WINDOWS, AllowedTarget.MAC_OS},
        return_type = DICTIONARY,
        return_sub_type = STRING,
        return_required = true,
        return_description = "A dictionary with choice, probabilities, confidence, model, input_tokens, output_tokens, billable_tokens, elapsed_ms, status, error_message"
)
public class EvaluateChoice {

    private static final Logger logger = LogManager.getLogger(EvaluateChoice.class);

    @SuppressWarnings("rawtypes")
    @Execute
    public Value<?> execute(
            @Idx(index = "1", type = CREDENTIAL)
            @Pkg(label = "TypeSafe API Key")
            @NotEmpty
            SecureString apiKey,

            @Idx(index = "2", type = TEXTAREA)
            @Pkg(label = "Text to evaluate")
            @NotEmpty
            String text,

            @Idx(index = "3", type = TEXT)
            @Pkg(label = "Question", description = "A categorical question about the text, e.g. \"Which team should handle this\"")
            @NotEmpty
            String question,

            @Idx(index = "4", type = com.automationanywhere.commandsdk.model.AttributeType.DICTIONARY)
            @Pkg(label = "Categories", description = "Category name mapped to a description of when it applies")
            @NotEmpty
            Map<String, Value> categories,

            @Idx(index = "5", type = TEXT)
            @Pkg(label = "Model", default_value = "jev-latest", default_value_type = STRING)
            @NotEmpty
            String model
    ) {
        logger.info("EvaluateChoice started - model: {}, question: {}", model, question);
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        try {
            JSONObject criteria = new JSONObject();
            for (Map.Entry<String, Value> entry : categories.entrySet()) {
                criteria.put(entry.getKey(), String.valueOf(entry.getValue().get()));
            }
            JSONObject questionPayload = new JSONObject()
                    .put("type", "choice")
                    .put("instructions", question)
                    .put("criteria", criteria);

            JSONObject result = TypeSafeClient.evaluate(apiKey.getInsecureString(), text, model, questionPayload);
            JSONObject answer = result.getJSONObject("answer");
            JSONObject usage = result.getJSONObject("usage");
            long elapsedMs = result.optLong("elapsedMs", 0);

            fields.put("choice", new StringValue(answer.getString("choice")));
            fields.put("probabilities", new StringValue(answer.getJSONObject("probabilities").toString()));
            fields.put("confidence", new StringValue(String.valueOf(answer.getDouble("confidence"))));

            DictionaryValue dictionary = DictionaryHelper.success(fields, model, usage, elapsedMs);
            logger.info("EvaluateChoice completed");
            return dictionary;
        } catch (Exception e) {
            logger.error("EvaluateChoice failed", e);
            fields.put("choice", new StringValue(""));
            fields.put("probabilities", new StringValue(""));
            fields.put("confidence", new StringValue(""));
            return DictionaryHelper.error(fields, model, "EvaluateChoice failed: " + e.getMessage());
        }
    }
}
