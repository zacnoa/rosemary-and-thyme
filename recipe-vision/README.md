# recipe-vision

A project created with FastAPI CLI.

## Extract a recipe from the command line

Run the complete extraction flow without starting the API server:

```bash
uv run python cli.py assets/rotated.jpg
```

The original color image is sent directly to the vision model without a preview
or preprocessing step. The model faithfully transcribes the visible recipe into
a source-grounded intermediate result. A second parser pass then normalizes
amounts, units, language, and grammar while retaining the original `source_text`
for every item.

The default workflow uses Qwen3-VL 8B Instruct with a 16k context for both
vision and parsing. The vision pass receives one original color image. Models
can still be changed independently:

```bash
uv run python cli.py assets/rotated.jpg \
  --vision-model qwen3-vl:8b-instruct-q4_K_M \
  --parser-model qwen3-vl:8b-instruct-q4_K_M
```

### Batch extraction

Run every image in `assets` through source transcription and parsing:

```bash
uv run python batch_extract.py assets \
  --vision-model qwen3-vl:8b-instruct-q4_K_M \
  --parser-model qwen3-vl:8b-instruct-q4_K_M \
  --output-dir outputs/batch-vision-parser
```

Each image gets its own JSON file containing both `raw_recipe` and the normalized
`recipe`, together with model names, duration, and any error. `summary.json`
contains the aggregate result.

## Quick Start

### Start the development server

```bash
uv sync
uv run fastapi dev --host 0.0.0.0 --port 8000
```

Visit http://localhost:8000

The API accepts one JPEG or PNG in the `image` multipart field:

```bash
curl -X POST http://localhost:8000/recipe-vision \
  -F "image=@assets/typed.jpg"
```

`RECIPE_VISION_API_KEY` is optional for local development. When configured, the
caller must send the same value in the `X-API-Key` header. Set it in production
because a Runpod proxy URL is publicly reachable.

### Deploy to FastAPI Cloud

Sign up and log in at https://fastapicloud.com, then deploy with:

```bash
uv run fastapi deploy
```

## Project Structure

- `main.py` - Your FastAPI application
- `pyproject.toml` - Project dependencies

## Learn More

- [FastAPI Documentation](https://fastapi.tiangolo.com)
- [FastAPI Cloud](https://fastapicloud.com)
