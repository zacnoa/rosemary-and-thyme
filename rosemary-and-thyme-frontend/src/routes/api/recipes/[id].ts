import { APIEvent } from "filesystem-routing/api";
import { env } from "node:process";

export async function GET({ request, params }: APIEvent) {
  const id = params?.id
  if (!id) {
    return Response.json(
      {
        error: "Recipe ID missing"
      },
      {
        status: 404
      }
    )
  }
  const url = new URL(`/recipe/${encodeURIComponent(id)}`, env.API_URL)
  const cookie = request.headers.get("cookie")
  return fetch(url, {
    headers: cookie ? { cookie } : {}
  })

}

