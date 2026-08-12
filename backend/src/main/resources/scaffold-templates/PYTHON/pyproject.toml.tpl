[build-system]
requires = ["setuptools>=69"]
build-backend = "setuptools.build_meta"

[project]
name = "{{repoName}}"
version = "0.1.0"
description = "{{description}}"
requires-python = ">=3.12"
dependencies = [
    "fastapi==0.110.0",
    "uvicorn[standard]==0.28.0",
]

[project.optional-dependencies]
dev = [
    "pytest==8.1.1",
    "httpx==0.27.0",
    "ruff==0.4.2",
]

[tool.setuptools.packages.find]
where = ["src"]

[tool.pytest.ini_options]
testpaths = ["tests"]

[tool.ruff]
line-length = 100
target-version = "py312"

[tool.ruff.lint]
select = ["E", "F", "W", "I", "UP", "B"]