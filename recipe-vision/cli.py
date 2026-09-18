import argparse
import sys
from pathlib import Path

from services.extract_recipe import extract_recipe


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Extract a structured recipe from an image without starting the API server."
    )
    parser.add_argument("image", type=Path, help="Path to a JPEG or PNG recipe image.")
    parser.add_argument(
        "--vision-model",
        default="qwen3-vl:8b-instruct-q4_K_M",
        help="Vision model used for source transcription.",
    )
    parser.add_argument(
        "--parser-model",
        default="qwen3-vl:8b-instruct-q4_K_M",
        help="Text model used to normalize the source transcription.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    image_path = args.image.expanduser().resolve()

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    if not image_path.is_file():
        raise SystemExit(f"Image not found: {image_path}")

    with image_path.open("rb") as image_file:
        recipe = extract_recipe(
            image_file,
            vision_model=args.vision_model,
            parser_model=args.parser_model,
        )

    print(recipe.model_dump_json(indent=2))


if __name__ == "__main__":
    main()
