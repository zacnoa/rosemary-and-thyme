import argparse
import json
import sys
from pathlib import Path
from time import perf_counter

from extraction.recipe_parser import recipe_parser
from models.raw_extracted_components import RawExtractedRecipe


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Parse raw_recipe results from an existing vision batch without running "
            "the vision model again."
        )
    )
    parser.add_argument(
        "source_dir",
        type=Path,
        help="Batch output directory containing per-image raw_recipe JSON.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        required=True,
        help="Directory for parser results.",
    )
    parser.add_argument("--parser-model", required=True)
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

    source_dir = args.source_dir.expanduser().resolve()
    output_dir = args.output_dir.expanduser().resolve()
    if not source_dir.is_dir():
        raise SystemExit(f"Source batch directory not found: {source_dir}")

    source_paths = sorted(
        path
        for path in source_dir.glob("*.json")
        if path.name != "summary.json"
    )
    if not source_paths:
        raise SystemExit(f"No per-image JSON files found in: {source_dir}")

    output_dir.mkdir(parents=True, exist_ok=True)
    started_at = perf_counter()
    results = []
    print(
        f"Parsing {len(source_paths)} cached vision results with {args.parser_model}",
        flush=True,
    )

    for index, source_path in enumerate(source_paths, start=1):
        item_started_at = perf_counter()
        source_result = json.loads(source_path.read_text(encoding="utf-8"))
        image_name = source_result.get("image", f"{source_path.stem}.unknown")
        raw_payload = source_result.get("raw_recipe")
        print(f"[{index}/{len(source_paths)}] {image_name}", flush=True)

        if raw_payload is None:
            result = {
                "image": image_name,
                "success": False,
                "duration_seconds": 0,
                "vision_model": source_result.get("vision_model"),
                "parser_model": args.parser_model,
                "error_type": "MissingRawRecipe",
                "error": "Source batch result does not contain raw_recipe.",
            }
        else:
            try:
                raw_recipe = RawExtractedRecipe.model_validate(raw_payload)
                recipe = recipe_parser(raw_recipe, model=args.parser_model)
                result = {
                    "image": image_name,
                    "success": True,
                    "duration_seconds": round(perf_counter() - item_started_at, 2),
                    "vision_model": source_result.get("vision_model"),
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
                    "image": image_name,
                    "success": False,
                    "duration_seconds": round(perf_counter() - item_started_at, 2),
                    "vision_model": source_result.get("vision_model"),
                    "parser_model": args.parser_model,
                    "raw_recipe": raw_payload,
                    "error_type": type(error).__name__,
                    "error": str(error),
                }
                print(
                    f"  FAILED in {result['duration_seconds']}s: "
                    f"{result['error_type']}: {result['error']}",
                    flush=True,
                )

        results.append(result)
        write_json(output_dir / source_path.name, result)

    succeeded = sum(result["success"] for result in results)
    summary = {
        "source_directory": str(source_dir),
        "vision_model": next(
            (result.get("vision_model") for result in results if result.get("vision_model")),
            None,
        ),
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
