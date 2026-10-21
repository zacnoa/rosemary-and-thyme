# Architecture and Models

## Application Interaction

```mermaid
flowchart LR
    User[User] --> UI

    subgraph Frontend[Frontend]
        UI[UI] --> State[State Management]
        State --> API[API Client]
    end

    API --> REST

    subgraph Backend[Backend]
        REST[REST API] --> Service[Service Layer]
        REST --> Auth[Authentication Layer]
        Auth --> Service
        Service --> Postgres[(Postgres)]
    end
```

## Backend Endpoints

```mermaid
flowchart TB
    Client[Frontend / Client] --> Auth
    Client --> Recipe
    Client --> User

    subgraph Auth[Auth — /auth]
        A1["POST /register<br/>Returns: 201 Created, no body"]
        A2["POST /verify-email<br/>Returns: 200 OK, session cookie"]
        A3["POST /verify-email/resend<br/>Returns: 200 OK, no body"]
        A4["POST /login<br/>Returns: 200 OK, session cookie"]
        A5["GET /google<br/>Returns: 302 Found, redirect to Google OAuth"]
        A6["POST /password-reset/request<br/>Returns: 200 OK, no body"]
        A7["POST /password-reset/confirm<br/>Returns: 200 OK, session cookie"]
        A8["POST /logout<br/>Returns: 200 OK, cleared session cookie"]
    end

    subgraph Recipe[Recipe — /recipe]
        R1["POST /extract<br/>Returns: RecipeDTO"]
        R2["GET /{id}<br/>Returns: RecipeDTO"]
        R3["POST /<br/>Returns: RecipeDTO"]
        R4["PUT /{id}<br/>Returns: RecipeDTO"]
        R5["GET /search<br/>Returns: RecipeFeedPage"]
        R6["POST /{id}/like<br/>Returns: LikeStatusDTO"]
        R7["DELETE /{id}/like<br/>Returns: LikeStatusDTO"]
        R8["POST /{id}/made<br/>Returns: MadeStatusDTO"]
        R9["DELETE /{id}<br/>Returns: 204 No Content"]
        R10["PATCH /{id}/private<br/>Returns: 200 OK, no body"]
    end

    subgraph User[User — /user]
        U1["GET /aboutme<br/>Returns: UserDTO"]
        U2["PUT /password<br/>Returns: 200 OK, session cookie"]
        U3["POST /delete<br/>Returns: 200 OK, no body"]
        U4["DELETE /delete<br/>Returns: 200 OK, no body"]
        U5["GET /recipes/feed<br/>Returns: RecipeFeedPage"]
        U6["GET /recipes/liked<br/>Returns: RecipeFeedPage"]
    end
```

Endpoint paths are relative to their group prefix. `RecipeDTO` is the recipe response model; feed endpoints return a page of recipes.

## Recipe Models

```mermaid
classDiagram
    class Recipe {
        +UUID id
        +UUID userId
        +String userName
        +Date createDate
        +String name
        +String description
        +number likes
        +number timesMade
        +boolean liked
        +number portions
        +String cookTime
        +number difficulty
        +String sideNotes
        +Record~UUID, RecipeImage~ images
        +Record~UUID, Ingredient~ ingredients
        +Record~UUID, Instruction~ instructions
        +UUID[] ingredientsOrder
        +UUID[] instructionsOrder
        +UUID[] heroImagesOrder
        +boolean isPrivate
    }

    class RecipeDTO {
        +UUID id
        +UUID userId
        +String userName
        +LocalDate createDate
        +String name
        +String description
        +Long likes
        +Long timesMade
        +boolean liked
        +Int portions
        +String cookTime
        +Int difficulty
        +String sideNotes
        +Map~UUID, RecipeImageDTO~ images
        +Map~UUID, IngredientDTO~ ingredients
        +Map~UUID, InstructionDTO~ instructions
        +List~UUID~ ingredientsOrder
        +List~UUID~ instructionsOrder
        +List~UUID~ heroImagesOrder
        +boolean isPrivate
    }

    class Ingredient {
        +UUID id
        +String name
        +number amount
        +String measuringUnit
    }

    class IngredientDTO {
        +UUID id
        +String name
        +BigDecimal amount
        +String measuringUnit
    }

    class Instruction {
        +UUID id
        +String text
        +UUID[] images
    }

    class InstructionDTO {
        +UUID id
        +String text
        +List~UUID~ images
    }

    class RecipeImage {
        +UUID id
        +String url
        +File blob
        +String blobURL
    }

    class RecipeImageDTO {
        +UUID id
        +String url
    }

    Recipe "1" *-- "*" Ingredient : ingredients
    Recipe "1" *-- "*" Instruction : instructions
    Recipe "1" *-- "*" RecipeImage : images
    RecipeDTO "1" *-- "*" IngredientDTO : ingredients
    RecipeDTO "1" *-- "*" InstructionDTO : instructions
    RecipeDTO "1" *-- "*" RecipeImageDTO : images
    Recipe .. RecipeDTO : equivalent data shape on frontend
```
