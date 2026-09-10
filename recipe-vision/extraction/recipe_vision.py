from pathlib import Path
from ollama import chat
from constants.agent_message import AgentMessages
from models.extracted_components import ExtractedRecipe


# Get path from user input
def recipe_vision():
    path = Path(input("Enter the path to your image: ")).resolve()

    # Verify the file exists
    if not path.exists():
        raise FileNotFoundError(f"Image not found at: {path}")

    image_bytes = path.read_bytes()  # Read the image bytes

    hint = input("Please provide the language in which the recipe is written: ")

    # Set up chat as usual
    response = chat(
        model="gemma3:12b",
        format=ExtractedRecipe.model_json_schema(),  # Pass in the schema for the response
        messages=[
            {
                "role": "user",
                "content": (
                    AgentMessages.MAIN_PROMPT.value
                    + AgentMessages.COOKING_TIME_EXTRACTION.value
                    + AgentMessages.INGREDIENT_EXTRACTION.value
                    + AgentMessages.SIDENOTE_EXTRACTION.value
                    + AgentMessages.LANGUAGE_EXTRACTION.value
                    + AgentMessages.CONFIDENCE.value
                    + AgentMessages.SPELLING.value
                    + f"\n\nThe recipe is written in {hint} language."
                ),
                "images": [image_bytes],  # Pass the image bytes to the model
            },
        ],
        options={
            "temperature": 0
        },  # Set temperature to 0 for more deterministic output
    )

    # Convert received content to the schema
    image_analysis = ExtractedRecipe.model_validate_json(response.message.content)
    print(
        image_analysis.model_dump_json(indent=4)
    )  # Print the structured output in JSON format


if __name__ == "__main__":
    recipe_vision()
