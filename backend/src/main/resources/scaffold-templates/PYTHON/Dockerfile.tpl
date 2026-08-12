# ---- Build stage: lint + test --------------------------------------------
FROM python:3.12-slim AS build
WORKDIR /app
COPY pyproject.toml ruff.toml README.md ./
COPY src ./src
COPY tests ./tests
RUN pip install -e ".[dev]" \
 && ruff check . \
 && pytest -q

# ---- Runtime stage --------------------------------------------------------
FROM python:3.12-slim
WORKDIR /app
COPY --from=build /app /app
ENV PYTHONPATH=/app/src
EXPOSE 8000
CMD ["uvicorn", "{{packageName}}.main:app", "--host", "0.0.0.0", "--port", "8000"]