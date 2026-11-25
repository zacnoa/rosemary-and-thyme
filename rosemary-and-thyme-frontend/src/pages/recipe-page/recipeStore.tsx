import { createStore} from "solid-js"

export interface RecipeDTO {
  id: string,
  userId: string,
  userName: string,
  title: string,
  description: string,
  likes: number,
  timesMade: number,
  portions: number,
  difficulty: number,
  cookTime: string,
  sideNote: string,
  createdDate: string,

  images: Record<string, ImageDTO>,
  ingredients: Record<string, IngredientDTO>,
  instructions: Record<string, InstructionDTO>,

  ingredientsOrder: Array<string>,
  instructionsOrder: Array<string>,
  heroImagesOrder: Array<string>,

  isPrivate: Boolean,
  version: number
}

interface IngredientDTO {
  id: string,
  name: string,
  amount: number
}
interface InstructionDTO {
  id: string,
  text: string,
  images: Array<string>
}

interface ImageDTO {
  id: string,
  url?: string,
  fileBlob?: Blob,
  previewURL?: string
}
export function createRecipeStore(initialRecipe: RecipeDTO) {
  const [recipe, setRecipe] = createStore(initialRecipe)

  const setTitle = (title: string) => {
    setRecipe(draft => { draft.title = title })
  }
  const addHeroImage = (file: Blob) => {
    setRecipe(draft => {
      const uuid=crypto.randomUUID()
      const previewURL=URL.createObjectURL(file)
      draft.images[uuid] = {id:uuid,fileBlob:file,previewURL:previewURL}
      draft.heroImagesOrder.push(uuid)
    })
  }
  const removeHeroImage = (imageId: string) => {
    setRecipe(draft => {
      draft.heroImagesOrder = draft.heroImagesOrder.filter(id => id !== imageId)
      const previewURL = draft.images[imageId]?.previewURL
      if (previewURL) URL.revokeObjectURL(previewURL)
      delete draft.images[imageId]
    })
  }
  const setDescription = (description: string) => {
    setRecipe(draft => { draft.description = description })
  }
  const setLikes = (likes: number) => {
    setRecipe(draft => { draft.likes = likes })
  }
  const setTimesMade = (made: number) => {
    setRecipe(draft => { draft.timesMade = made })
  }
  const setPortions = (portions: number) => {
    setRecipe(draft => { draft.portions = portions })
  }
  const setDifficulty = (difficulty: number) => {
    setRecipe(draft => {
      draft.difficulty = difficulty
    })
  }
  const setCookTime = (cookTime: string) => {
    setRecipe(draft => {
      draft.cookTime = cookTime
    })
  }
  const setSideNote = (sideNote: string) => {
    setRecipe(draft => {
      draft.sideNote = sideNote
    })
  }
  const addIngredient = () => {
    setRecipe(draft => {
      const uuid = crypto.randomUUID()
      draft.ingredients[uuid] = { id: uuid, name: "", amount: 0 }
      draft.ingredientsOrder.push(uuid)
    })
  }
  const editIngredient = (ingredient: IngredientDTO) => {
    setRecipe(draft => { draft.ingredients[ingredient.id] = ingredient })
  }

  const removeIngredient = (ingredientId: string) => {
    setRecipe(draft => {
      delete draft.ingredients[ingredientId]
      draft.ingredientsOrder = draft.ingredientsOrder.filter(id => id != ingredientId)
    })
  }
  const addInstruction = () => {
    setRecipe(draft => {
      const uuid = crypto.randomUUID()
      draft.instructions[uuid] = { id: uuid, text: "", images: [] }
      draft.instructionsOrder.push(uuid)
    })
  }
  const addInstructionImage = (instructionId: string, file: Blob) => {
    setRecipe(draft => {
      const uuid=crypto.randomUUID()
      draft.instructions[instructionId].images.push(uuid)
      const previewURL=URL.createObjectURL(file)

      draft.images[uuid] ={id:uuid,fileBlob:file,previewURL:previewURL}
    })
  }
  const removeInstructionImage = (instructionId: string, imageId: string) => {
    setRecipe(draft => {
      const instruction = draft.instructions[instructionId]
      if (!instruction?.images.includes(imageId)) return

      instruction.images = instruction.images.filter(id => id !== imageId)
      const previewURL = draft.images[imageId]?.previewURL
      if (previewURL) URL.revokeObjectURL(previewURL)
      delete draft.images[imageId]
    })
  }
  const editInstruction = (instruction: InstructionDTO) => {
    setRecipe(draft => {
      draft.instructions[instruction.id] = instruction
    })
  }
  const removeInstruction = (instructionId: string) => {
    setRecipe(draft => {
      const instruction = draft.instructions[instructionId]
      for (const imageId of instruction?.images ?? []) {
        const previewURL = draft.images[imageId]?.previewURL
        if (previewURL) URL.revokeObjectURL(previewURL)
        delete draft.images[imageId]
      }
      delete draft.instructions[instructionId]
      draft.instructionsOrder = draft.instructionsOrder.filter(id => id !== instructionId)
    })
  }
  return {
    recipe,
    setTitle,
    addHeroImage,
    removeHeroImage,
    setDescription,
    setLikes,
    setTimesMade,
    setPortions,
    setDifficulty,
    setCookTime,
    setSideNote,
    addIngredient,
    editIngredient,
    removeIngredient,
    addInstruction,
    addInstructionImage,
    removeInstructionImage,
    editInstruction,
    removeInstruction
  }
}

export type RecipeStore = ReturnType<typeof createRecipeStore>

