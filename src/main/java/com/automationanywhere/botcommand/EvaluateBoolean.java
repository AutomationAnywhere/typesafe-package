package com.automationanywhere.botcommand;

import java.util.LinkedHashMap;

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
 * Asks a yes/no ("noul") question about a piece of text and returns the probability
 * that the answer is yes.
 */
@BotCommand
@CommandPkg(
        name = "evaluateBoolean",
        label = "Evaluate Boolean",
        description = "Asks a yes/no question about text and returns the probability the answer is yes.",
        node_label = "{{question}}",
        icon = "pkg.png",
        comment = true,
        allowed_agent_targets = {AllowedTarget.WINDOWS, AllowedTarget.MAC_OS},
        return_type = DICTIONARY,
        return_sub_type = STRING,
        return_required = true,
        return_description = "A dictionary with probability, model, input_tokens, output_tokens, billable_tokens, elapsed_ms, status, error_message"
)
public class EvaluateBoolean {

    private static final Logger logger = LogManager.getLogger(EvaluateBoolean.class);

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
            @Pkg(label = "Question", description = "A yes/no question about the text, e.g. \"Does this message express urgency?\"")
            @NotEmpty
            String question,

            @Idx(index = "4", type = TEXT)
            @Pkg(label = "Model", default_value = "jev-latest", default_value_type = STRING)
            @NotEmpty
            String model
    ) {
        logger.info("EvaluateBoolean started - model: {}, question: {}", model, question);
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        try {
            JSONObject questionPayload = new JSONObject()
                    .put("type", "noul")
                    .put("instructions", question);

            JSONObject result = TypeSafeClient.evaluate(apiKey.getInsecureString(), text, model, questionPayload);
            JSONObject answer = result.getJSONObject("answer");
            JSONObject usage = result.getJSONObject("usage");
            long elapsedMs = result.optLong("elapsedMs", 0);

            fields.put("probability", new StringValue(String.valueOf(answer.getDouble("noul"))));

            DictionaryValue dictionary = DictionaryHelper.success(fields, model, usage, elapsedMs);
            logger.info("EvaluateBoolean completed");
            return dictionary;
        } catch (Exception e) {
            logger.error("EvaluateBoolean failed", e);
            fields.put("probability", new StringValue(""));
            return DictionaryHelper.error(fields, model, "EvaluateBoolean failed: " + e.getMessage());
        }
    }
}
