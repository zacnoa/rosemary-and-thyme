/**
 * Welcome to Cloudflare Workers! This is your first worker.
 *
 * - Run `npm run dev` in your terminal to start a development server
 * - Open a browser tab at http://localhost:8787/ to see your worker in action
 * - Run `npm run deploy` to publish your worker
 *
 * Bind resources to your worker in `wrangler.jsonc`. After adding bindings, a type definition for the
 * `Env` object can be regenerated with `npm run cf-typegen`.
 *
 * Learn more at https://developers.cloudflare.com/workers/
 */

import { errors } from "jose";
import { verifyMediaToken } from "./verifyMediaToken";

const imagePath =
  /^\/recipe-images\/([A-Za-z0-9-]+)\/([A-Za-z0-9-]+)\/([A-Za-z0-9-]+)$/;

export default {
  async fetch(request, env): Promise<Response> {
    const url = new URL(request.url);
    const match = imagePath.exec(url.pathname);
    if (!match) return new Response(null, { status: 404 });

    if (request.method !== "GET") {
      return new Response(null, { status: 405, headers: { Allow: "GET" } });
    }

    const token = url.searchParams.get("token");
    if (!token) return new Response(null, { status: 401 });

    const [, userId, recipeId, imageId] = match;

    try {
      const claims = await verifyMediaToken(token);

      if (
        claims.userId !== userId ||
        claims.recipeId !== recipeId ||
        claims.imageId !== imageId
      ) {
        return new Response(null, { status: 401 });
      }
    } catch (error) {
      if (error instanceof errors.JOSEError) {
        return new Response(null, { status: 401 });
      }
      throw error;
    }

    const key = `users/${userId}/recipes/${recipeId}/images/${imageId}`;
    const image = await env.MY_BUCKET.get(key);
    if (!image) return new Response(null, { status: 404 });

    return new Response(image.body, {
      headers: {
        "Content-Type": "image/png",
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
      },
    });
  },
} satisfies ExportedHandler<Env>;
