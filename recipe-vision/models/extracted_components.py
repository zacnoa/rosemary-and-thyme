from decimal import Decimal

from pydantic import BaseModel, Field


class ExtractedIngredient(BaseModel):
    name: str | None
    amount: Decimal | None
    measuring_unit: str | None
    confidence: float = Field(ge=0, le=1)
    warnings: list[str]


class ExtractedInstruction(BaseModel):
    text: str | None
    confidence: float = Field(ge=0, le=1)
    warnings: list[str]


class ExtractedRecipe(BaseModel):
    name: str | None
    description: str | None
    portions: int | None
    cook_time: str | None
    difficulty: int | None = Field(ge=1, le=5)
    side_notes: str | None

    ingredients: list[ExtractedIngredient]
    instructions: list[ExtractedInstruction]

    warnings: list[str]
    confidence: float = Field(ge=0, le=1)
    requires_review: bool
