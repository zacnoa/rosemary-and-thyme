import argparse
import sys
from pathlib import Path

from providers.openrouter import recipe_vision
from providers.openrouter import vision_model as default_vision_model


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Extract a structured recipe from an image without starting the API server."
    )
    parser.add_argument("image", type=Path, help="Path to a JPEG or PNG recipe image.")
    parser.add_argument(
        "--model",
        default=default_vision_model(),
        help="OpenRouter model; defaults to OPENROUTER_VISION_MODEL.",
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
        recipe = recipe_vision(image_file.read(), model=args.model)

    print(recipe.model_dump_json(indent=2))


if __name__ == "__main__":
    main()
