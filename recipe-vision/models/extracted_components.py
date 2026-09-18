from pydantic import BaseModel, Field


class ExtractedIngredient(BaseModel):
    source_text: str = Field(
        description="Exact source ingredient line used to produce this item."
    )
    name: str | None = Field(
        description="Ingredient name only, without its numeric amount or measuring unit."
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
    confidence: float = Field(ge=0, le=1)
    warnings: list[str]


class ExtractedInstruction(BaseModel):
    source_text: str = Field(
        description="Exact source instruction block used to produce this item."
    )
    text: str | None
    confidence: float = Field(ge=0, le=1)
    warnings: list[str]


class ExtractedRecipe(BaseModel):
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
    description: str | None = Field(
        description="Description explicitly present in the recipe; never generate one."
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

    warnings: list[str]
    confidence: float = Field(ge=0, le=1)
    requires_review: bool
