import { createContext, createMemo, Errored, Loading, ParentProps } from "solid-js";
import { createRecipeStore, RecipeDTO, RecipeStore } from "./recipeStore";

export const RecipeContext = createContext<RecipeStore>()

export default function RecipeProvider(props: ParentProps<{ id: string }>) {

  const recipe = createMemo<RecipeDTO>(async () => {
    const response = await fetch(`/api/recipes/${encodeURIComponent(props.id)}`)
    if (!response.ok) {
      throw new Error("")
    }
    const body = (await response.json()) as Omit<RecipeDTO, "images"> & {
      images: Record<string, string>
    }

    return {
      ...body,
      images: Object.fromEntries(
        Object.entries(body.images).map(([id, url]) => [id, { id, url }])
      ),
    }
  })

  return (
    <>
      <Loading on={recipe()} fallback={<p>Loading...</p>}>
        <RecipeContext value={createRecipeStore(recipe())}>
          {props.children}
        </RecipeContext>
      </Loading>
    </>
  )

}
