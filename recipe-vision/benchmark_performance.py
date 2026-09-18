import argparse
import json
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from time import perf_counter
from typing import Any

import cv2
import numpy as np
from cv2.typing import MatLike
from ollama import ChatResponse, chat
from pydantic import ValidationError

from constants.agent_message import ParserAgentMessages, VisionAgentMessages
from models.extracted_components import ExtractedRecipe
from models.raw_extracted_components import RawExtractedRecipe


DEFAULT_IMAGES = ("typed.jpg", "bad-lighting2.jpg", "faded.jpg")
SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


@dataclass(frozen=True)
class VisionVariant:
    name: str
    include_color: bool
    include_threshold: bool
    max_long_edge: int | None
    num_ctx: int


VISION_VARIANTS = (
    VisionVariant("baseline_both_full_ctx16k", True, True, None, 16384),
    VisionVariant("color_only_full_ctx16k", True, False, None, 16384),
    VisionVariant("threshold_only_full_ctx16k", False, True, None, 16384),
    VisionVariant("threshold_only_full_ctx8k", False, True, None, 8192),
    VisionVariant("both_2048_ctx16k", True, True, 2048, 16384),
    VisionVariant("color_only_2048_ctx16k", True, False, 2048, 16384),
    VisionVariant("both_2048_ctx8k", True, True, 2048, 8192),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Benchmark Qwen recipe extraction input and context-size variants, "
            "then benchmark parser context sizes on identical raw results."
        )
    )
    parser.add_argument("--assets-dir", type=Path, default=Path("assets"))
    parser.add_argument(
        "--images",
        nargs="+",
        default=list(DEFAULT_IMAGES),
        help="Image filenames inside --assets-dir.",
    )
    parser.add_argument(
        "--all-images",
        action="store_true",
        help="Benchmark every supported image inside --assets-dir.",
    )
    parser.add_argument(
        "--model",
        default="qwen3-vl:8b-instruct-q4_K_M",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("outputs/performance-benchmark"),
    )
    parser.add_argument(
        "--vision-variants",
        nargs="+",
        choices=[variant.name for variant in VISION_VARIANTS],
        default=None,
        help="Run only the named vision variants (default: all).",
    )
    parser.add_argument(
        "--skip-parser",
        action="store_true",
        help="Skip parser context benchmarks.",
    )
    parser.add_argument(
        "--parser-source-variant",
        choices=[variant.name for variant in VISION_VARIANTS],
        default="baseline_both_full_ctx16k",
        help="Vision variant whose raw output is passed to the parser.",
    )
    parser.add_argument(
        "--parser-contexts",
        nargs="+",
        type=int,
        default=[8192, 4096],
        help="Parser context sizes to benchmark.",
    )
    return parser.parse_args()


def _encode_png(image: MatLike) -> bytes:
    success, encoded = cv2.imencode(".png", image)
    if not success:
        raise ValueError("Could not encode benchmark image as PNG.")
    return encoded.tobytes()


def _decode_color_image(image_bytes: bytes) -> MatLike:
    image = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("The supplied file is not a supported image.")
    return image


def _create_threshold_image(color_image: MatLike) -> MatLike:
    grayscale_image = cv2.cvtColor(color_image, cv2.COLOR_BGR2GRAY)
    _, threshold_image = cv2.threshold(
        grayscale_image,
        0,
        255,
        cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU,
    )
    return threshold_image


def _resize_long_edge(image: MatLike, max_long_edge: int | None) -> MatLike:
    if max_long_edge is None:
        return image

    height, width = image.shape[:2]
    long_edge = max(height, width)
    if long_edge <= max_long_edge:
        return image

    scale = max_long_edge / long_edge
    return cv2.resize(
        image,
        (round(width * scale), round(height * scale)),
        interpolation=cv2.INTER_AREA,
    )


def _seconds(nanoseconds: int | None) -> float | None:
    if nanoseconds is None:
        return None
    return round(nanoseconds / 1_000_000_000, 4)


def _response_metrics(response: ChatResponse, wall_seconds: float) -> dict[str, Any]:
    eval_count = response.eval_count or 0
    eval_seconds = (response.eval_duration or 0) / 1_000_000_000
    return {
        "wall_seconds": round(wall_seconds, 4),
        "total_seconds": _seconds(response.total_duration),
        "load_seconds": _seconds(response.load_duration),
        "prompt_eval_seconds": _seconds(response.prompt_eval_duration),
        "generation_seconds": _seconds(response.eval_duration),
        "prompt_tokens": response.prompt_eval_count,
        "generated_tokens": response.eval_count,
        "generation_tokens_per_second": (
            round(eval_count / eval_seconds, 2) if eval_count and eval_seconds else None
        ),
    }


def _vision_call(
    *,
    model: str,
    color_image: MatLike,
    threshold_image: MatLike,
    variant: VisionVariant,
) -> tuple[RawExtractedRecipe, dict[str, Any]]:
    prepared_color = _resize_long_edge(color_image, variant.max_long_edge)
    prepared_threshold = _resize_long_edge(
        threshold_image,
        variant.max_long_edge,
    )
    encoded_images = []
    image_shapes = []
    if variant.include_color:
        encoded_images.append(_encode_png(prepared_color))
        image_shapes.append(list(prepared_color.shape))
    if variant.include_threshold:
        encoded_images.append(_encode_png(prepared_threshold))
        image_shapes.append(list(prepared_threshold.shape))
    if not encoded_images:
        raise ValueError("A vision benchmark variant must include at least one image.")

    attempt_metrics = []
    for attempt in range(2):
        retry_instruction = (
            "\n\n" + VisionAgentMessages.RETRY.value if attempt else ""
        )
        started_at = perf_counter()
        response = chat(
            model=model,
            format=RawExtractedRecipe.model_json_schema(),
            messages=[
                {
                    "role": "system",
                    "content": VisionAgentMessages.SYSTEM.value + retry_instruction,
                },
                {
                    "role": "user",
                    "content": VisionAgentMessages.USER.value,
                    "images": encoded_images,
                },
            ],
            options={
                "temperature": 0,
                "repeat_penalty": 1.1,
                "num_ctx": variant.num_ctx,
                "num_predict": 4096,
            },
            keep_alive="30m",
        )
        metrics = _response_metrics(response, perf_counter() - started_at)
        metrics["attempt"] = attempt + 1
        attempt_metrics.append(metrics)
        try:
            raw_recipe = RawExtractedRecipe.model_validate_json(
                response.message.content
            )
            return raw_recipe, {
                "image_shapes": image_shapes,
                "encoded_image_bytes": [len(image) for image in encoded_images],
                "attempts": attempt_metrics,
                "wall_seconds": round(
                    sum(item["wall_seconds"] for item in attempt_metrics), 4
                ),
            }
        except ValidationError:
            if attempt == 1:
                raise

    raise RuntimeError("Vision benchmark failed unexpectedly.")


def _parser_call(
    *,
    model: str,
    raw_recipe: RawExtractedRecipe,
    num_ctx: int,
) -> tuple[ExtractedRecipe, dict[str, Any]]:
    source_json = raw_recipe.model_dump_json(indent=2)
    attempt_metrics = []

    for attempt in range(2):
        retry_instruction = (
            "\n\n" + ParserAgentMessages.RETRY.value if attempt else ""
        )
        started_at = perf_counter()
        response = chat(
            model=model,
            format=ExtractedRecipe.model_json_schema(),
            messages=[
                {
                    "role": "system",
                    "content": ParserAgentMessages.SYSTEM.value + retry_instruction,
                },
                {
                    "role": "user",
                    "content": ParserAgentMessages.USER_PREFIX.value + source_json,
                },
            ],
            options={
                "temperature": 0,
                "repeat_penalty": 1.1,
                "num_ctx": num_ctx,
                "num_predict": 4096,
            },
            keep_alive="30m",
        )
        metrics = _response_metrics(response, perf_counter() - started_at)
        metrics["attempt"] = attempt + 1
        attempt_metrics.append(metrics)
        try:
            recipe = ExtractedRecipe.model_validate_json(response.message.content)
            return recipe, {
                "source_json_characters": len(source_json),
                "attempts": attempt_metrics,
                "wall_seconds": round(
                    sum(item["wall_seconds"] for item in attempt_metrics), 4
                ),
            }
        except ValidationError:
            if attempt == 1:
                raise

    raise RuntimeError("Parser benchmark failed unexpectedly.")


def _write_json(path: Path, value: object) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def _raw_summary(raw_recipe: RawExtractedRecipe) -> dict[str, Any]:
    return {
        "source_title": raw_recipe.source_title,
        "ingredient_count": len(raw_recipe.ingredients),
        "instruction_count": len(raw_recipe.instructions),
        "warning_count": len(raw_recipe.warnings),
        "confidence": raw_recipe.confidence,
        "requires_review": raw_recipe.requires_review,
    }


def _recipe_summary(recipe: ExtractedRecipe) -> dict[str, Any]:
    return {
        "name": recipe.name,
        "language": recipe.language,
        "ingredient_count": len(recipe.ingredients),
        "instruction_count": len(recipe.instructions),
        "warning_count": len(recipe.warnings),
        "confidence": recipe.confidence,
        "requires_review": recipe.requires_review,
    }


def main() -> int:
    args = parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    assets_dir = args.assets_dir.expanduser().resolve()
    output_dir = args.output_dir.expanduser().resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.all_images:
        image_paths = sorted(
            path
            for path in assets_dir.iterdir()
            if path.is_file() and path.suffix.lower() in SUPPORTED_EXTENSIONS
        )
    else:
        image_paths = [assets_dir / filename for filename in args.images]
    missing_paths = [path for path in image_paths if not path.is_file()]
    if missing_paths:
        missing = ", ".join(str(path) for path in missing_paths)
        raise SystemExit(f"Benchmark images not found: {missing}")

    benchmark_started_at = perf_counter()
    results: list[dict[str, Any]] = []
    parser_raw_by_image: dict[str, RawExtractedRecipe] = {}
    selected_variant_names = set(args.vision_variants or [])
    active_variants = tuple(
        variant
        for variant in VISION_VARIANTS
        if not selected_variant_names or variant.name in selected_variant_names
    )

    if not args.skip_parser and not any(
        variant.name == args.parser_source_variant for variant in active_variants
    ):
        raise SystemExit(
            f"Parser benchmark requires {args.parser_source_variant}; select it or "
            "use --skip-parser."
        )

    print(
        f"Vision benchmark: {len(image_paths)} images x "
        f"{len(active_variants)} variants",
        flush=True,
    )
    for image_path in image_paths:
        preprocessing_started_at = perf_counter()
        image_bytes = image_path.read_bytes()
        color_image = _decode_color_image(image_bytes)
        threshold_image = _create_threshold_image(color_image)
        preprocessing_seconds = perf_counter() - preprocessing_started_at

        for variant in active_variants:
            print(f"[vision] {image_path.name} / {variant.name}", flush=True)
            result: dict[str, Any] = {
                "stage": "vision",
                "image": image_path.name,
                "model": args.model,
                "variant": asdict(variant),
                "preprocessing_seconds": round(preprocessing_seconds, 4),
            }
            try:
                raw_recipe, metrics = _vision_call(
                    model=args.model,
                    color_image=color_image,
                    threshold_image=threshold_image,
                    variant=variant,
                )
                result.update(
                    {
                        "success": True,
                        "metrics": metrics,
                        "summary": _raw_summary(raw_recipe),
                        "raw_recipe": raw_recipe.model_dump(mode="json"),
                    }
                )
                if variant.name == args.parser_source_variant:
                    parser_raw_by_image[image_path.name] = raw_recipe
                print(
                    f"  OK {metrics['wall_seconds']}s, "
                    f"title={raw_recipe.source_title!r}",
                    flush=True,
                )
            except Exception as error:
                result.update(
                    {
                        "success": False,
                        "error_type": type(error).__name__,
                        "error": str(error),
                    }
                )
                print(
                    f"  FAILED {type(error).__name__}: {error}",
                    flush=True,
                )

            results.append(result)
            _write_json(
                output_dir / f"vision--{image_path.stem}--{variant.name}.json",
                result,
            )

    if not args.skip_parser:
        print(
            f"Parser benchmark: {args.parser_source_variant} raw results at "
            f"contexts {args.parser_contexts}",
            flush=True,
        )
        for image_path in image_paths:
            raw_recipe = parser_raw_by_image.get(image_path.name)
            if raw_recipe is None:
                print(
                    f"[parser] skipping {image_path.name}: source vision failed",
                    flush=True,
                )
                continue

            for num_ctx in args.parser_contexts:
                variant_name = f"ctx{num_ctx // 1024}k"
                print(f"[parser] {image_path.name} / {variant_name}", flush=True)
                result = {
                    "stage": "parser",
                    "image": image_path.name,
                    "model": args.model,
                    "variant": {"name": variant_name, "num_ctx": num_ctx},
                }
                try:
                    recipe, metrics = _parser_call(
                        model=args.model,
                        raw_recipe=raw_recipe,
                        num_ctx=num_ctx,
                    )
                    result.update(
                        {
                            "success": True,
                            "metrics": metrics,
                            "summary": _recipe_summary(recipe),
                            "recipe": recipe.model_dump(mode="json"),
                        }
                    )
                    print(
                        f"  OK {metrics['wall_seconds']}s, name={recipe.name!r}",
                        flush=True,
                    )
                except Exception as error:
                    result.update(
                        {
                            "success": False,
                            "error_type": type(error).__name__,
                            "error": str(error),
                        }
                    )
                    print(
                        f"  FAILED {type(error).__name__}: {error}",
                        flush=True,
                    )

                results.append(result)
                _write_json(
                    output_dir / f"parser--{image_path.stem}--{variant_name}.json",
                    result,
                )

    successful_results = [result for result in results if result.get("success")]
    summary = {
        "model": args.model,
        "images": [path.name for path in image_paths],
        "vision_variants": [asdict(variant) for variant in active_variants],
        "parser_source_variant": (
            None if args.skip_parser else args.parser_source_variant
        ),
        "parser_contexts": [] if args.skip_parser else args.parser_contexts,
        "duration_seconds": round(perf_counter() - benchmark_started_at, 2),
        "total_runs": len(results),
        "successful_runs": len(successful_results),
        "failed_runs": len(results) - len(successful_results),
        "results": [
            {
                "stage": result["stage"],
                "image": result["image"],
                "variant": result["variant"],
                "success": result["success"],
                "wall_seconds": result.get("metrics", {}).get("wall_seconds"),
                "summary": result.get("summary"),
                "error": result.get("error"),
            }
            for result in results
        ],
    }
    _write_json(output_dir / "summary.json", summary)
    print(
        f"Finished {len(successful_results)}/{len(results)} runs in "
        f"{summary['duration_seconds']}s. Results: {output_dir}",
        flush=True,
    )
    return 0 if len(successful_results) == len(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
