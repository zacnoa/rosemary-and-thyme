from ollama import chat
from pydantic import ValidationError

from constants.agent_message import VisionAgentMessages
from models.raw_extracted_components import RawExtractedRecipe


def recipe_vision(
    image_bytes: bytes,
    model: str = "qwen3-vl:8b-instruct-q4_K_M",
) -> RawExtractedRecipe:
    if not image_bytes:
        raise ValueError("The image file is empty.")

    for attempt in range(2):
        retry_instruction = ""
        if attempt == 1:
            retry_instruction = "\n\n" + VisionAgentMessages.RETRY.value

        options = {
            "temperature": 0,
            "repeat_penalty": 1.1,
        }
        if model.startswith("qwen3-vl:"):
            options["num_ctx"] = 16384

        response = chat(
            model=model,
            format=RawExtractedRecipe.model_json_schema(),
            messages=[
                {
                    "role": "system",
                    "content": VisionAgentMessages.SYSTEM.value
                    + retry_instruction,
                },
                {
                    "role": "user",
                    "content": VisionAgentMessages.USER.value,
                    "images": [image_bytes],
                },
            ],
            options=options,
        )

        try:
            return RawExtractedRecipe.model_validate_json(response.message.content)
        except ValidationError:
            if attempt == 1:
                raise

    raise RuntimeError("Recipe extraction failed unexpectedly.")
