# Required fixes for `aa-automation-builder`'s `scripts/bot_builder.py`

Captured while building a TaskBot against `TypeSafePackage` in this project. Confirmed
against a real saved bot (Control Room file id `101537597`, tenant
`aa-training-sbx.cloud.automationanywhere.digital`) — not guessed. Apply these to
`PROJECT_ROOT/scripts/bot_builder.py` in the actual plugin source repo and re-push.

## 1. `DICTIONARY` has no confirmed mapping — add one

Currently `ATTRIBUTE_TYPE_TO_DATA_TYPE` has no `"DICTIONARY"` entry, so any command with a
`DICTIONARY`-typed attribute (e.g. `TypeSafePackage.evaluateChoice`'s `categories`) raises
`UnmappedAttributeTypeError`.

**Confirmed real shape** (from `evaluateChoice`'s `categories` attribute):

```json
{
  "type": "DICTIONARY",
  "dictionary": [
    {"key": "billing", "value": {"type": "STRING", "string": "Payment or subscription issues"}},
    {"key": "technical", "value": {"type": "STRING", "string": "Bugs or integration problems"}}
  ]
}
```

Add to `ATTRIBUTE_TYPE_TO_DATA_TYPE`:
```python
"DICTIONARY": "DICTIONARY",
```

And a compound-type branch in `make_attribute_value` (alongside the existing `SESSION`
branch), since a dict-of-values needs its own serialization, not a flat `_LITERAL_VALUE_KEY`
entry:

```python
if data_type == "DICTIONARY":
    # value is expected to be a plain dict[str, Any-literal] for now (nested Var/
    # non-STRING values not yet confirmed — extend when a real example is found).
    return {
        "type": "DICTIONARY",
        "dictionary": [
            {"key": k, "value": make_attribute_value("TEXT", v)}
            for k, v in value.items()
        ],
    }
```

## 2. `LIST` has no confirmed mapping — add one

Same issue for `TypeSafePackage.evaluateScore`'s `scaleLevels` (and any other `LIST`
attribute — note this is distinct from the already-confirmed `ENTRYLIST → LIST` mapping,
which is a different AttributeType).

**Confirmed real shape**:

```json
{
  "type": "LIST",
  "list": [
    {"type": "STRING", "string": "Calm, just stating facts"},
    {"type": "STRING", "string": "Frustrated but civil"},
    {"type": "STRING", "string": "Very angry, strong language"}
  ]
}
```

Add:
```python
"LIST": "LIST",
```

And a compound branch:
```python
if data_type == "LIST":
    return {
        "type": "LIST",
        "list": [make_attribute_value("TEXT", item) for item in value],
    }
```

(Same caveat as above — only plain string items confirmed so far.)

## 3. `CREDENTIAL` is mapped but has no literal serialization — add one

`"CREDENTIAL": "CREDENTIAL"` already exists in `ATTRIBUTE_TYPE_TO_DATA_TYPE`, but there's no
branch in `make_attribute_value` to serialize an actual credential reference, so passing one
today raises `UnmappedAttributeTypeError` at the `_LITERAL_VALUE_KEY` lookup.

**Confirmed real shape** (from `evaluateBoolean`'s `apiKey` attribute, referencing a
Credential Vault entry named "TypeSafe", locker "MicahsLocker", attribute "API_KEY"):

```json
{
  "type": "CREDENTIAL",
  "credential": {
    "name": "TypeSafe",
    "lockerName": "MicahsLocker",
    "attributeName": "API_KEY"
  }
}
```

Suggest adding a small marker class next to `Var`:

```python
class Credential:
    """Marks a step-field value as a reference to a Credential Vault entry."""
    def __init__(self, name: str, locker_name: str, attribute_name: str):
        self.name = name
        self.locker_name = locker_name
        self.attribute_name = attribute_name
```

And in `make_attribute_value`, before the `Var` check (or alongside it):
```python
if isinstance(value, Credential):
    return {
        "type": "CREDENTIAL",
        "credential": {
            "name": value.name,
            "lockerName": value.locker_name,
            "attributeName": value.attribute_name,
        },
    }
```

## 4. No way to assign a step's output to a variable at all — biggest gap

`make_node` already accepts a `return_to` param and writes it as `node["returnTo"]`, but
**`compile_bot` never reads any such field off a `step` dict and never passes it through** —
so there is currently no way to capture *any* command's return value into a variable, for
any AttributeType. This blocks essentially every useful multi-step bot (chaining one
action's output into the next, or into a MessageBox).

**Confirmed real shape** — `returnTo` is a **sibling of `"attributes"`** on the node, not
nested inside it:

```json
{
  "uid": "35fec580-...",
  "commandName": "evaluateBoolean",
  "packageName": "TypeSafePackage",
  "disabled": false,
  "attributes": [ ... ],
  "returnTo": {"type": "VARIABLE", "variableName": "dUrgency"}
}
```

The referenced variable must also be pre-declared in the bot's top-level `variables` list,
with a `defaultValue` matching its type. Confirmed shape for a `DICTIONARY`-typed output
variable:

```json
{
  "name": "dUrgency",
  "description": "",
  "type": "DICTIONARY",
  "readOnly": false,
  "input": false,
  "output": false,
  "subtype": "STRING",
  "defaultValue": {"type": "DICTIONARY", "dictionary": []}
}
```

(A plain `STRING` variable's `defaultValue` is `{"type": "STRING", "string": ""}` —
confirmed from the same bot's `sIncomingText` variable.)

**Suggested API change** — let a step optionally specify `"return_to": "<variableName>"`,
and have `compile_bot` both wire `returnTo` onto the node and auto-append the variable
declaration (inferring `type`/`subtype` from `entry["returnType"]`/`entry["returnSubtype"]`
in the catalog entry, which the schema already carries — worth double-checking those keys
are threaded through `build_catalog.py` into each command's catalog entry):

```python
for step in steps:
    ...
    return_to = step.get("return_to")
    node = make_node(entry["commandName"], package_name, attributes,
                      return_to={"type": "VARIABLE", "variableName": return_to} if return_to else None)
    nodes.append(node)
    if return_to and not any(v["name"] == return_to for v in declared_variables):
        declared_variables.append(make_return_variable(return_to, entry))
```

## 5. Bonus: confirmed `$var{key}$` dictionary-property expression syntax

Not a compiler bug, but worth documenting somewhere (design spec or a comment near
`_VARIABLE_TOKEN_PATTERN`): referencing a single field of a `DICTIONARY` variable inside a
`STRING` expression uses `$variableName{fieldName}$` — no quotes around `fieldName`.
Confirmed from the real MessageBox `content` attribute:

```
"Department: $dDepartment{choice}$\nUrgency: $dUrgency{probability}$"
```

## Suggested test fixture

Save `reference/aa_full_command_schema.json` is tenant-specific and gitignored, but the
**bot JSON itself** (`/tmp/real_bot.json` in this session, file id `101537597`) is a clean,
real fixture exercising all four gaps above (`DICTIONARY` input, `LIST` input, `CREDENTIAL`
input, and `returnTo` output) in one bot — worth adding to
`tests/fixtures/real_bots/` as e.g. `TypeSafeTicketTriage.json` so
`tests/test_integration_recompile.py` covers these paths going forward.
