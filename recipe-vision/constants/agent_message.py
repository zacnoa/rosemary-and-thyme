from enum import Enum


class AgentMessages(Enum):

    MAIN_PROMPT = """Look at the image and describe what you see if you can describe the recipe in as much detail as possible.
    Then return the recipe in the JSON format specified in the schema. If you cannot describe the recipe, return a schema with all fields set to null.
    """

    COOKING_TIME_EXTRACTION = """ Cook time should be in the format of Xh Ym where X is the number of hours and Y is the number of minutes,
    if the cook time is less than 1 hour, return only in Ym format.
    """

    INGREDIENT_EXTRACTION = """ Ingredients should be separated from their measuring unit and amount.
    You can confidently assume that the amount is always a number, decimal  or fraction and that the measuring unit is always a string and after the amount.
    The amount should always be inserted as a decimal number in the JSON output, if the amount is a fraction, convert it to decimal.
    """

    SIDENOTE_EXTRACTION = """ Side notes should be any additional information that is not part of the recipe but is relevant to the recipe.
    For example, if the recipe is for a cake, side notes could include information about the type of cake,
    the occasion for which the cake is suitable, or any other relevant information.
    But you should not write the side notes yourself, you should only extract them from the image if they are present.
    If there are no side notes, return null for the side notes field.
    """
    LANGUAGE_EXTRACTION = """You should expect that the text in the image can be in any language,
    you should not translate the text but rather transcribe it in its original language.
    There shall be a hint about the language in which the recipe is written, you should use this hint to help you transcribe the text in the correct language.
    """

    CONFIDENCE = """ Do not assign high confidence merely because the output matches the schema.
    Confidence above 0.9 is allowed only when the complete handwritten line is clearly readable.
    """

    SPELLING = """ You should correct any spelling mistakes either from the original language or from your transcription.
    You should make sure that the spelling is correct in the original language and not in english.
    Always strive to find the closest correct spelling keepping in mind the context in which the word is used,
    if you are not sure about the spelling, return the word as it is in the original language. 
    """
