"""OpenRouter adapter for Recipe Vision.

OpenRouter implements the OpenAI chat-completions API, so this adapter remains
portable to another OpenAI-compatible provider (for example a future vLLM
deployment) by changing only configuration.
"""

import base64
import os
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from openai import OpenAI
from pydantic import ValidationError

from constants.agent_message import AgentMessages
from models.extracted_components import ExtractedRecipe


# FastAPI does not load .env files itself. Loading the file beside this project
# keeps local development convenient; production supplies the same variables as
# service environment variables, which take precedence over the file.
load_dotenv(Path(__file__).resolve().parents[1] / ".env")

DEFAULT_MODEL = "qwen/qwen3-vl-30b-a3b-instruct"
DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"


class OpenRouterConfigurationError(RuntimeError):
    """Raised when this service has no usable OpenRouter configuration."""


class OpenRouterResponseError(RuntimeError):
    """Raised when OpenRouter returns no structured completion content."""


def vision_model() -> str:
    return os.getenv("OPENROUTER_VISION_MODEL", DEFAULT_MODEL)


def _client() -> OpenAI:
    api_key = os.getenv("OPENROUTER_API_KEY", "").strip()
    if not api_key or api_key == "replace-with-openrouter-api-key":
        raise OpenRouterConfigurationError(
            "OPENROUTER_API_KEY must be configured for Recipe Vision."
        )

    return OpenAI(
        api_key=api_key,
        base_url=os.getenv("OPENROUTER_BASE_URL", DEFAULT_BASE_URL).rstrip("/"),
        # Schema retries are explicitly controlled by the extraction stages so a
        # malformed answer cannot cause invisible provider retries and extra cost.
        max_retries=0,
    )


def _image_data_url(image_bytes: bytes) -> str:
    if image_bytes.startswith(b"\xff\xd8\xff"):
        mime_type = "image/jpeg"
    elif image_bytes.startswith(b"\x89PNG\r\n\x1a\n"):
        mime_type = "image/png"
    else:
        raise ValueError("Only JPEG and PNG recipe images are supported.")

    encoded = base64.b64encode(image_bytes).decode("ascii")
    return f"data:{mime_type};base64,{encoded}"


def _completion_content(response: Any) -> str:
    if not response.choices:
        raise OpenRouterResponseError("OpenRouter returned no completion choices.")

    content = response.choices[0].message.content
    if not isinstance(content, str) or not content.strip():
        raise OpenRouterResponseError("OpenRouter returned an empty completion.")
    return content


def recipe_vision(
    image_bytes: bytes,
    model: str | None = None,
) -> ExtractedRecipe:
    """Extract one validated digital recipe from a JPEG or PNG document image."""
    if not image_bytes:
        raise ValueError("The image file is empty.")

    user_content: list[dict[str, Any]] = [
        {"type": "text", "text": AgentMessages.USER.value},
        {
            "type": "image_url",
            "image_url": {"url": _image_data_url(image_bytes)},
        },
    ]

    for attempt in range(2):
        response = _client().chat.completions.create(
            model=model or vision_model(),
            temperature=0,
            max_tokens=int(os.getenv("OPENROUTER_MAX_OUTPUT_TOKENS", "4096")),
            response_format={
                "type": "json_schema",
                "json_schema": {
                    "name": "extracted_recipe",
                    "strict": True,
                    "schema": ExtractedRecipe.model_json_schema(),
                },
            },
            # Never silently route to a provider that ignores structured output.
            extra_body={"provider": {"require_parameters": True}},
            messages=[
                {
                    "role": "system",
                    "content": AgentMessages.system_prompt(retry=attempt == 1),
                },
                {"role": "user", "content": user_content},
            ],
            timeout=float(os.getenv("OPENROUTER_TIMEOUT_SECONDS", "120")),
        )

        try:
            return ExtractedRecipe.model_validate_json(_completion_content(response))
        except ValidationError:
            if attempt == 1:
                raise

    raise RuntimeError("Recipe extraction failed unexpectedly.")
