# recipe-vision

A project created with FastAPI CLI.

## Extract a recipe from the command line

Run the complete preprocessing and extraction flow without starting the API
server:

```bash
uv run python cli.py assets/rotated.jpg
```

The CLI first shows the rotated and threshold images in a preview window. Close
the window (or press `Esc`/`q`) to start extraction. The vision model faithfully
transcribes the visible recipe into a source-grounded intermediate result. A
second, text-only parser model then normalizes amounts, units, language, and
grammar while retaining the original `source_text` for every item.

The default models are Qwen3-VL 8B Instruct for vision and Gemma 3 4B for
parsing. They can be changed independently:

```bash
uv run python cli.py assets/rotated.jpg \
  --vision-model qwen3-vl:8b-instruct-q4_K_M \
  --parser-model gemma3:4b
```

### Batch extraction

Run every image in `assets` through source transcription and parsing:

```bash
uv run python batch_extract.py assets \
  --vision-model qwen3-vl:8b-instruct-q4_K_M \
  --parser-model gemma3:4b \
  --output-dir outputs/batch-vision-parser
```

Each image gets its own JSON file containing both `raw_recipe` and the normalized
`recipe`, together with model names, duration, and any error. `summary.json`
contains the aggregate result.

## Quick Start

### Start the development server

```bash
uv run fastapi dev
```

Visit http://localhost:8000

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
