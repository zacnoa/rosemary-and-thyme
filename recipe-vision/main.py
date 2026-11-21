import logging
from typing import Annotated

from fastapi import FastAPI, File, HTTPException, UploadFile, status
from pydantic import BaseModel

from models.extracted_components import ExtractedRecipe
from providers.openrouter import recipe_vision

app = FastAPI()
logger = logging.getLogger(__name__)
SUPPORTED_IMAGE_TYPES = {"image/jpeg", "image/png"}


class RecipeVisionResponse(BaseModel):
    recipe: ExtractedRecipe


@app.post(
    "/recipe-vision",
    response_model=RecipeVisionResponse,
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
        recipe = recipe_vision(image.file.read())
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
