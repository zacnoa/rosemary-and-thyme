import base64

import cv2
from cv2.typing import MatLike
from ollama import chat
from pydantic import ValidationError

from constants.agent_message import VisionAgentMessages
from models.raw_extracted_components import RawExtractedRecipe


def _encode_png(image: MatLike) -> bytes:
    success, encoded_image = cv2.imencode(".png", image)
    if not success:
        raise ValueError("Could not encode the processed image.")
    return encoded_image.tobytes()


def _resize_for_preview(image: MatLike, max_width: int, max_height: int) -> MatLike:
    height, width = image.shape[:2]
    scale = min(max_width / width, max_height / height, 1.0)

    if scale == 1.0:
        return image

    return cv2.resize(
        image,
        (round(width * scale), round(height * scale)),
        interpolation=cv2.INTER_AREA,
    )


def show_preview(rotated_image: MatLike, grayscale_image: MatLike) -> None:
    import tkinter as tk
    from tkinter import ttk

    window = tk.Tk()
    window.title("Recipe preprocessing preview — close to start extraction")

    max_width = max((window.winfo_screenwidth() - 120) // 2, 300)
    max_height = max(window.winfo_screenheight() - 180, 300)
    preview_images = []

    for column, (title, image) in enumerate(
        (
            ("Rotated image", rotated_image),
            ("Threshold image", grayscale_image),
        )
    ):
        resized_image = _resize_for_preview(image, max_width, max_height)
        png_base64 = base64.b64encode(_encode_png(resized_image)).decode("ascii")
        preview_image = tk.PhotoImage(data=png_base64, format="png")
        preview_images.append(preview_image)

        ttk.Label(window, text=title).grid(row=0, column=column, pady=(10, 5))
        ttk.Label(window, image=preview_image).grid(
            row=1,
            column=column,
            padx=10,
            pady=(0, 10),
        )

    ttk.Label(
        window,
        text="Close this window, or press Esc/q, to start recipe extraction.",
    ).grid(row=2, column=0, columnspan=2, pady=(0, 10))

    window.bind("<Escape>", lambda _event: window.destroy())
    window.bind("q", lambda _event: window.destroy())
    window.mainloop()


def recipe_vision(
    rotated_image: MatLike,
    grayscale_image: MatLike,
    model: str = "qwen3-vl:8b-instruct-q4_K_M",
) -> RawExtractedRecipe:
    rotated_bytes = _encode_png(rotated_image)
    greyscale_bytes = _encode_png(grayscale_image)

    for attempt in range(2):
        retry_instruction = ""
        if attempt == 1:
            retry_instruction = "\n\n" + VisionAgentMessages.RETRY.value

        options = {
            "temperature": 0,
            "repeat_penalty": 1.1,
        }
        if model.startswith("qwen3-vl:"):
            options["num_ctx"] = 16384

        response = chat(
            model=model,
            format=RawExtractedRecipe.model_json_schema(),
            messages=[
                {
                    "role": "system",
                    "content": VisionAgentMessages.SYSTEM.value
                    + retry_instruction,
                },
                {
                    "role": "user",
                    "content": VisionAgentMessages.USER.value,
                    "images": [rotated_bytes, greyscale_bytes],
                },
            ],
            options=options,
        )

        try:
            return RawExtractedRecipe.model_validate_json(response.message.content)
        except ValidationError:
            if attempt == 1:
                raise

    raise RuntimeError("Recipe extraction failed unexpectedly.")
