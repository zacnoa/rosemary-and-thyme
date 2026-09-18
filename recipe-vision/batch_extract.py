import argparse
import json
import sys
from pathlib import Path
from time import perf_counter

from services.extract_recipe import extract_recipe_with_source

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Extract every recipe image in a directory and save a JSON report."
    )
    parser.add_argument(
        "assets_dir",
        nargs="?",
        type=Path,
        default=Path("assets"),
        help="Directory containing recipe images (default: assets).",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("outputs/batch-extraction"),
        help="Directory for per-image results and summary JSON.",
    )
    parser.add_argument(
        "--vision-model",
        default="qwen3-vl:8b-instruct-q4_K_M",
    )
    parser.add_argument(
        "--parser-model",
        default="qwen3-vl:8b-instruct-q4_K_M",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Process only the first N images (useful for a smoke test).",
    )
    return parser.parse_args()


def write_json(path: Path, value: object) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    assets_dir = args.assets_dir.expanduser().resolve()
    output_dir = args.output_dir.expanduser().resolve()

    if not assets_dir.is_dir():
        raise SystemExit(f"Assets directory not found: {assets_dir}")
    if args.limit is not None and args.limit < 1:
        raise SystemExit("--limit must be greater than zero.")

    image_paths = sorted(
        path
        for path in assets_dir.iterdir()
        if path.is_file() and path.suffix.lower() in SUPPORTED_EXTENSIONS
    )
    if args.limit is not None:
        image_paths = image_paths[: args.limit]
    if not image_paths:
        raise SystemExit(f"No supported images found in: {assets_dir}")

    output_dir.mkdir(parents=True, exist_ok=True)
    started_at = perf_counter()
    results = []

    print(
        f"Processing {len(image_paths)} images with "
        f"{args.vision_model} -> {args.parser_model}",
        flush=True,
    )

    for index, image_path in enumerate(image_paths, start=1):
        image_started_at = perf_counter()
        raw_recipe = None
        print(f"[{index}/{len(image_paths)}] {image_path.name}", flush=True)

        try:
            with image_path.open("rb") as image_file:
                raw_recipe, recipe = extract_recipe_with_source(
                    image_file,
                    vision_model=args.vision_model,
                    parser_model=args.parser_model,
                )

            result = {
                "image": image_path.name,
                "success": True,
                "duration_seconds": round(perf_counter() - image_started_at, 2),
                "vision_model": args.vision_model,
                "parser_model": args.parser_model,
                "raw_recipe": raw_recipe.model_dump(mode="json"),
                "recipe": recipe.model_dump(mode="json"),
            }
            print(
                f"  OK in {result['duration_seconds']}s: {recipe.name!r}",
                flush=True,
            )
        except Exception as error:
            result = {
                "image": image_path.name,
                "success": False,
                "duration_seconds": round(perf_counter() - image_started_at, 2),
                "vision_model": args.vision_model,
                "parser_model": args.parser_model,
                "error_type": type(error).__name__,
                "error": str(error),
            }
            if raw_recipe is not None:
                result["raw_recipe"] = raw_recipe.model_dump(mode="json")
            print(
                f"  FAILED in {result['duration_seconds']}s: "
                f"{result['error_type']}: {result['error']}",
                flush=True,
            )

        results.append(result)
        write_json(output_dir / f"{image_path.stem}.json", result)

    succeeded = sum(result["success"] for result in results)
    summary = {
        "assets_directory": str(assets_dir),
        "vision_model": args.vision_model,
        "parser_model": args.parser_model,
        "total_images": len(results),
        "succeeded": succeeded,
        "failed": len(results) - succeeded,
        "duration_seconds": round(perf_counter() - started_at, 2),
        "results": [
            {
                "image": result["image"],
                "success": result["success"],
                "duration_seconds": result["duration_seconds"],
                "name": result.get("recipe", {}).get("name"),
                "error": result.get("error"),
            }
            for result in results
        ],
    }
    write_json(output_dir / "summary.json", summary)

    print(
        f"Finished: {succeeded}/{len(results)} succeeded in "
        f"{summary['duration_seconds']}s. Results: {output_dir}",
        flush=True,
    )
    return 0 if succeeded == len(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
