# Rosemary & Thyme project instructions

## Frontend rewrite

Apply these project decisions when working in `rosemary-and-thyme-frontend`. Use the [frontend AGENTS.md](rosemary-and-thyme-frontend/AGENTS.md) for Solid-specific implementation guidance.

- Use Solid 2.0 with the `fullstack` project shape and `@solidjs/vite-plugin` in start mode. Keep filesystem routing under `src/routes`.
- Enable SSR for public recipe pages and informational pages such as `/privacy`. Render public recipe content and page metadata in the HTML response. Render the editor, dashboard, authentication screens, and search UI in the browser. Use `clientOnly` at the screen boundary and keep the screen's data loading inside that boundary.
- Use the Kotlin API for recipe data, writes, sessions, and authorization. The API decides whether a viewer can read a private recipe or edit a recipe. Keep private recipe responses out of shared caches.

## Page organization

Give each page its own directory under `src/pages`. Keep its components, types, and other page-specific files together. Keep route modules focused on routing and let them delegate the page UI to `src/pages`. Put code shared across pages in shared modules.

## Recipe routes

| URL | Route file under `src/routes` | Behavior |
| --- | --- | --- |
| `/recipes/:id` | `recipes/[id]/index.tsx` | Show the recipe in reading mode to any authorized viewer, including the owner. Show the owner an **Edit** action. |
| `/recipes/:id/edit` | `recipes/[id]/edit.tsx` | Open the editor for the owner. Send the owner directly here after creating or importing a recipe. |

Keep the reading view and editor as separate components selected by their routes. Share the recipe model, formatting, and presentational components where they serve both screens. Keep form state, editing controls, and save behavior in the editor.
