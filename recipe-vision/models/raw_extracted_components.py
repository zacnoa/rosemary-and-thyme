from pydantic import BaseModel, Field


class RawIngredient(BaseModel):
    source_text: str = Field(
        description="Complete visible text of one ingredient line, copied verbatim."
    )
    confidence: float = Field(ge=0, le=1)
    warnings: list[str]


class RawInstruction(BaseModel):
    source_text: str = Field(
        description=(
            "Complete visible text of one instruction block, copied as faithfully as "
            "possible without grammatical correction."
        )
    )
    confidence: float = Field(ge=0, le=1)
    warnings: list[str]


class RawExtractedRecipe(BaseModel):
    source_title: str | None = Field(
        description=(
            "Visible recipe title, or null when no title can be identified. Exclude "
            "section headings and unrelated document metadata."
        )
    )
    ingredients: list[RawIngredient]
    instructions: list[RawInstruction]
    side_notes: list[str] = Field(
        description="Relevant visible recipe notes that are not preparation steps."
    )
    ignored_text: list[str] = Field(
        description=(
            "Visible non-recipe metadata such as fax headers, phone numbers, page "
            "numbers, signatures, and ownership notes."
        )
    )
    warnings: list[str]
    confidence: float = Field(ge=0, le=1)
    requires_review: bool
