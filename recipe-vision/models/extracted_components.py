from pydantic import BaseModel, ConfigDict, Field


class StrictRecipeModel(BaseModel):
    """Forbid unrecognised fields in provider output and JSON Schema."""

    model_config = ConfigDict(extra="forbid")


class ExtractedIngredient(StrictRecipeModel):
    name: str = Field(
        default="",
        description=(
            "Ingredient name only, without its numeric amount or measuring unit. "
            "Use an empty string when it cannot be read safely."
        )
    )
    amount: float | None = Field(
        ge=0,
        description=(
            "Normalized numeric source quantity. Convert an unambiguous fraction to "
            "its decimal value; null when absent, ambiguous, or expressed as a range."
        )
    )
    measuring_unit: str | None = Field(
        description=(
            "Normalized measuring unit, such as kg, dkg, g, l, or ml; null when "
            "the source does not state a unit."
        )
    )


class ExtractedInstruction(StrictRecipeModel):
    text: str = Field(
        default="",
        description="Instruction text, or an empty string when it cannot be read safely.",
    )


class ExtractedRecipe(StrictRecipeModel):
    source_name: str | None = Field(
        description="Exact source recipe title before normalization."
    )
    name: str | None = Field(
        description=(
            "Normalized recipe title derived from source_name. A handwritten heading is "
            "valid when it belongs to a handwritten recipe. Never use a personal name, "
            "phone number, fax header, timestamp, or ownership annotation as the title."
        )
    )
    language: str | None = Field(
        description=(
            "ISO 639-1 code for the majority language of the ingredient and "
            "instruction text, or null when uncertain."
        )
    )
    portions: int | None
    cook_time: str | None = Field(
        description=(
            "Time stated in the recipe in Xh Ym or Ym format; preserve ranges such as "
            "2h-3h or 2-3h without inventing a midpoint."
        ),
    )
    difficulty: int | None = Field(ge=1, le=5)
    side_notes: str | None

    ingredients: list[ExtractedIngredient]
    instructions: list[ExtractedInstruction]
