from ollama import chat
from pydantic import ValidationError

from constants.agent_message import ParserAgentMessages
from models.extracted_components import ExtractedRecipe
from models.raw_extracted_components import RawExtractedRecipe


def recipe_parser(
    raw_recipe: RawExtractedRecipe,
    model: str = "qwen3-vl:8b-instruct-q4_K_M",
) -> ExtractedRecipe:
    source_json = raw_recipe.model_dump_json(indent=2)

    for attempt in range(2):
        retry_instruction = ""
        if attempt == 1:
            retry_instruction = "\n\n" + ParserAgentMessages.RETRY.value

        options = {
            "temperature": 0,
            "repeat_penalty": 1.1,
        }
        if model.startswith("qwen3-vl:"):
            options["num_ctx"] = 16384

        response = chat(
            model=model,
            format=ExtractedRecipe.model_json_schema(),
            messages=[
                {
                    "role": "system",
                    "content": ParserAgentMessages.SYSTEM.value + retry_instruction,
                },
                {
                    "role": "user",
                    "content": ParserAgentMessages.USER_PREFIX.value + source_json,
                },
            ],
            options=options,
        )

        try:
            recipe = ExtractedRecipe.model_validate_json(response.message.content)
            return recipe
        except ValidationError:
            if attempt == 1:
                raise

    raise RuntimeError("Recipe parsing failed unexpectedly.")
