package com.automationanywhere.botcommand;

import java.util.LinkedHashMap;
import java.util.List;

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
import org.json.JSONArray;
import org.json.JSONObject;

import static com.automationanywhere.commandsdk.model.AttributeType.CREDENTIAL;
import static com.automationanywhere.commandsdk.model.AttributeType.LIST;
import static com.automationanywhere.commandsdk.model.AttributeType.TEXT;
import static com.automationanywhere.commandsdk.model.AttributeType.TEXTAREA;
import static com.automationanywhere.commandsdk.model.DataType.DICTIONARY;
import static com.automationanywhere.commandsdk.model.DataType.STRING;

/**
 * Asks a numeric ("score") question about a piece of text and returns a score along
 * the given scale plus a legend describing each level.
 */
@BotCommand
@CommandPkg(
        name = "evaluateScore",
        label = "Evaluate Score",
        description = "Asks a numeric-scale question about text and returns a score along that scale.",
        node_label = "{{question}}",
        icon = "pkg.png",
        comment = true,
        allowed_agent_targets = {AllowedTarget.WINDOWS, AllowedTarget.MAC_OS},
        return_type = DICTIONARY,
        return_sub_type = STRING,
        return_required = true,
        return_description = "A dictionary with score, legend, confidence, model, input_tokens, output_tokens, billable_tokens, elapsed_ms, status, error_message"
)
public class EvaluateScore {

    private static final Logger logger = LogManager.getLogger(EvaluateScore.class);

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
            @Pkg(label = "Question", description = "A scaled question about the text, e.g. \"How frustrated the customer appears\"")
            @NotEmpty
            String question,

            @Idx(index = "4", type = LIST)
            @Pkg(label = "Scale levels", description = "Ordered scale-level descriptions, lowest first, e.g. \"Calm\", \"Frustrated\", \"Very angry\"")
            @NotEmpty
            List<Value> scaleLevels,

            @Idx(index = "5", type = TEXT)
            @Pkg(label = "Model", default_value = "jev-latest", default_value_type = STRING)
            @NotEmpty
            String model
    ) {
        logger.info("EvaluateScore started - model: {}, question: {}", model, question);
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        try {
            JSONArray criteria = new JSONArray();
            for (Value level : scaleLevels) {
                criteria.put(String.valueOf(level.get()));
            }
            JSONObject questionPayload = new JSONObject()
                    .put("type", "score")
                    .put("instructions", question)
                    .put("criteria", criteria);

            JSONObject result = TypeSafeClient.evaluate(apiKey.getInsecureString(), text, model, questionPayload);
            JSONObject answer = result.getJSONObject("answer");
            JSONObject usage = result.getJSONObject("usage");
            long elapsedMs = result.optLong("elapsedMs", 0);

            fields.put("score", new StringValue(String.valueOf(answer.getDouble("score"))));
            fields.put("legend", new StringValue(answer.getJSONObject("legend").toString()));
            fields.put("confidence", new StringValue(String.valueOf(answer.getDouble("confidence"))));

            DictionaryValue dictionary = DictionaryHelper.success(fields, model, usage, elapsedMs);
            logger.info("EvaluateScore completed");
            return dictionary;
        } catch (Exception e) {
            logger.error("EvaluateScore failed", e);
            fields.put("score", new StringValue(""));
            fields.put("legend", new StringValue(""));
            fields.put("confidence", new StringValue(""));
            return DictionaryHelper.error(fields, model, "EvaluateScore failed: " + e.getMessage());
        }
    }
}
