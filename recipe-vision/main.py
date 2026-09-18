import os
import secrets
import logging
from typing import Annotated

from fastapi import Depends, FastAPI, File, Header, HTTPException, UploadFile, status
from pydantic import BaseModel

from models.extracted_components import ExtractedRecipe
from services.extract_recipe import extract_recipe

app = FastAPI()
logger = logging.getLogger(__name__)
SUPPORTED_IMAGE_TYPES = {"image/jpeg", "image/png"}


class RecipeVisionResponse(BaseModel):
    recipe: ExtractedRecipe


def verify_api_key(
    api_key: Annotated[str | None, Header(alias="X-API-Key")] = None,
) -> None:
    expected_api_key = os.getenv("RECIPE_VISION_API_KEY")
    if expected_api_key and (
        api_key is None or not secrets.compare_digest(api_key, expected_api_key)
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid recipe vision API key.",
        )


@app.post(
    "/recipe-vision",
    response_model=RecipeVisionResponse,
    dependencies=[Depends(verify_api_key)],
)
def process_recipe_image(
    image: Annotated[UploadFile, File()],
) -> RecipeVisionResponse:
    if image.content_type not in SUPPORTED_IMAGE_TYPES:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="Only JPEG and PNG images are supported.",
        )

    try:
        recipe = extract_recipe(image.file)
    except ValueError as error:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(error),
        ) from error
    except Exception as error:
        logger.exception("Recipe vision model inference failed")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Recipe vision could not process this image. Please try again later.",
        ) from error

    return RecipeVisionResponse(recipe=recipe)


@app.get("/ping")
def ping():
    return {"message": "pong"}
