# Recipe Vision

Recipe Vision is an internal FastAPI service that turns one JPEG or PNG recipe
image into one validated digital recipe. The Spring backend calls its
`/recipe-vision` endpoint; this service sends the image and its recipe-specific
instructions to OpenRouter.

There is one extraction call, using
`qwen/qwen3-vl-30b-a3b-instruct` by default. The model is constrained to the
`ExtractedRecipe` JSON Schema and its result is validated by Pydantic before it
is returned. Unclear handwriting is represented by empty name or text fields,
rather than guessed content.

## Configure and run

```bash
cp .env.example .env
# Set OPENROUTER_API_KEY in .env.
uv sync
uv run fastapi dev --host 0.0.0.0 --port 8000
```

The API accepts one JPEG or PNG in the multipart `image` field:

```bash
curl -X POST http://localhost:8000/recipe-vision \
  -F "image=@assets/typed.jpg"
```

## Local single-image check

The CLI uses the same function and prompt as the API; it is not a separate
extraction workflow:

```bash
uv run python cli.py assets/typed.jpg
```
