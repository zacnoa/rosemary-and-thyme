from typing import BinaryIO

from extraction.recipe_parser import recipe_parser
from extraction.recipe_vision import recipe_vision
from models.extracted_components import ExtractedRecipe
from models.raw_extracted_components import RawExtractedRecipe


def extract_recipe_with_source(
    image_file: BinaryIO,
    vision_model: str = "qwen3-vl:8b-instruct-q4_K_M",
    parser_model: str = "qwen3-vl:8b-instruct-q4_K_M",
) -> tuple[RawExtractedRecipe, ExtractedRecipe]:
    image_bytes = image_file.read()
    if not image_bytes:
        raise ValueError("The image file is empty.")

    raw_recipe = recipe_vision(
        image_bytes,
        model=vision_model,
    )
    recipe = recipe_parser(raw_recipe, model=parser_model)
    return raw_recipe, recipe


def extract_recipe(
    image_file: BinaryIO,
    vision_model: str = "qwen3-vl:8b-instruct-q4_K_M",
    parser_model: str = "qwen3-vl:8b-instruct-q4_K_M",
) -> ExtractedRecipe:
    _raw_recipe, recipe = extract_recipe_with_source(
        image_file,
        vision_model=vision_model,
        parser_model=parser_model,
    )
    return recipe
