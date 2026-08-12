"""Golden-path FastAPI entrypoint for {{projectName}}."""

from fastapi import FastAPI

from {{packageName}} import __version__

app = FastAPI(title="{{projectName}}", version=__version__)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}