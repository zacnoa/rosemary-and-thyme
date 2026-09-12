from typing import Annotated

from fastapi import FastAPI, File, UploadFile

from services.extract_recipe import extract_recipe

app = FastAPI()


@app.post("/recipe-vision")
def process_recipe_image(
    file: Annotated[UploadFile, File()],
):
    return {"recipe": extract_recipe(file.file)}


@app.get("/ping")
def ping():
    return {"message": "pong"}
