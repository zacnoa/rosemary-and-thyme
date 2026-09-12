from typing import BinaryIO

from extraction.recipe_parser import recipe_parser
from extraction.recipe_vision import recipe_vision, show_preview
from image_preprocessing.process_image import process_image
from models.extracted_components import ExtractedRecipe
from models.raw_extracted_components import RawExtractedRecipe


def extract_recipe_with_source(
    image_file: BinaryIO,
    preview: bool = False,
    vision_model: str = "qwen3-vl:8b-instruct-q4_K_M",
    parser_model: str = "gemma3:4b",
) -> tuple[RawExtractedRecipe, ExtractedRecipe]:
    _original_image, rotated_image, grayscale_image = process_image(image_file)

    if preview:
        show_preview(rotated_image, grayscale_image)

    raw_recipe = recipe_vision(
        rotated_image,
        grayscale_image,
        model=vision_model,
    )
    recipe = recipe_parser(raw_recipe, model=parser_model)
    return raw_recipe, recipe


def extract_recipe(
    image_file: BinaryIO,
    preview: bool = False,
    vision_model: str = "qwen3-vl:8b-instruct-q4_K_M",
    parser_model: str = "gemma3:4b",
) -> ExtractedRecipe:
    recipe = extract_recipe_with_source(
        image_file,
        preview=preview,
        vision_model=vision_model,
        parser_model=parser_model,
    )
    return recipe
