# Recipe Vision

## Motivation

Many recipes are stored in handwritten notes and cookbooks passed down through generations.
Digitizing them makes them easier to preserve, search, and share, but entering each recipe
manually becomes tedious as the collection grows. Recipe Vision is the part of
`Rosemary & Thyme` that simplifies this process by turning a photographed recipe into
structured data.

## Problem

Recipes come in varied forms, including handwritten notes, printed pages, and old
cookbooks. Their layouts differ, so identifying the title, ingredients, instructions,
and side notes requires more than extracting individual words.

We initially explored OCR (Optical Character Recognition) to extract text and its
position on the page. Poor handwriting and image quality can reduce recognition
accuracy, while the extracted text still needs to be mapped to recipe fields. We also
explored CNN-based text recognition. A CNN (Convolutional Neural Network) is an
architecture that can be used within an OCR system, rather than a separate alternative
to OCR; recognizing text alone does not solve the mapping problem.

Rule-based parsing would be difficult to maintain across varied recipe layouts.
We therefore chose a vision-language model to read the image and use its layout and
context to organize the content into recipe fields in the same request.

The prompts define how to handle unclear handwriting, unrelated annotations, and
different languages. The model is instructed to normalize only unambiguous text while
preserving the original language and meaning. Unreadable ingredient names or instruction
text are left empty, and uncertain quantities are returned as `null`, rather than guessed.

Model responses can vary even with the same image and prompt. The service requests
structured output using the `ExtractedRecipe` JSON Schema and validates the response
with Pydantic. If JSON parsing or schema validation fails, it makes one additional
attempt with a retry instruction. If validation fails again, the service returns an
error instead of a recipe. This checks the structure and declared constraints, but
does not guarantee that the recipe was transcribed correctly.

Another consideration was the choice of model and deployment: running models locally
with tools such as Ollama or vLLM, or accessing hosted models through OpenRouter.

## Solution

Our initial experiments compared Gemma 3 and Qwen3-VL models of different sizes using
the same prompts. The recipe images are available in [`assets/`](assets/). Detailed
comparison measurements are no longer available, so the observations below are
qualitative rather than a reproducible benchmark or a measured accuracy ranking.

We explored a two-stage approach: first extracting the text with only unambiguous
corrections, then mapping that text to the recipe schema in a second model call.
Qwen3-VL 8B performed best in our initial qualitative tests of this approach.
However, two sequential calls add latency and token usage.

We then used `qwen/qwen3-vl-30b-a3b-instruct` to extract and structure the recipe in a
single call. This gave the most promising results in our initial qualitative tests.
We chose OpenRouter to avoid operating the infrastructure for the larger model ourselves.
The current implementation normally makes one extraction call, with a second call only
if JSON parsing or schema validation fails.

The [OpenRouter model page](https://openrouter.ai/qwen/qwen3-vl-30b-a3b-instruct)
lists prices starting at $0.13 per million input tokens and $0.52 per million output
tokens, as checked on September 27, 2026. At those rates, a request with 9,000 input
tokens and 1,000 output tokens would cost approximately $0.00169, excluding retries.
This is an illustrative calculation, not a measured average cost per recipe. Actual
cost depends on input and output token usage, the provider, and any retry.

As checked on September 27, 2026, the
[OpenRouter model catalog](https://openrouter.ai/models?fmt=cards&input_modalities=image&output_modalities=text&q=qwen)
marks this model as going away on **October 9, 2026**. It remains the model used in
these experiments and the current code default, but a replacement must be evaluated
before that date. The model can be changed through `OPENROUTER_VISION_MODEL`; any
replacement should be checked with the same recipe images and JSON Schema.

We refined the prompts based on errors observed during testing. They are defined in
[`constants/agent_message.py`](constants/agent_message.py). Further improvements should
focus on reducing omitted, misread, or invented content as well as invalid output
formats. Extracted recipes still need user review, especially when the source is unclear.

## Configure and run

Use Python 3.13 or later and install the dependencies:

```bash
uv sync
```

Create a `.env` file in the `recipe-vision` directory and set your OpenRouter API key:

```dotenv
OPENROUTER_API_KEY=your-openrouter-api-key
OPENROUTER_VISION_MODEL=qwen/qwen3-vl-30b-a3b-instruct
```

Replace the model above with an evaluated alternative before its scheduled removal.

Start the FastAPI service from this directory:

```bash
uv run fastapi dev --host 0.0.0.0 --port 8000
```

The Spring backend calls `POST /recipe-vision`. The endpoint accepts one JPEG or PNG
in the multipart `image` field and returns the validated recipe inside a `recipe` object:

```bash
curl -X POST http://localhost:8000/recipe-vision \
  -F "image=@assets/typed.jpg"
```

## Local single-image check

The CLI uses the same extraction function and prompt as the API and prints the recipe
directly as JSON:

```bash
uv run python cli.py assets/typed.jpg
```

Use `--model` to override `OPENROUTER_VISION_MODEL` for a single run.
