from fastapi import FastAPI

app = FastAPI()


@app.get("/recipe-vision")
def main():
    return {"message": "Hello World"}


@app.get("/ping")
def ping():
    return {"message": "pong"}
