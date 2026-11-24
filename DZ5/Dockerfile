FROM python:3.12-slim@sha256:191083cdf6c7bda8c76158983163ead5d5cf7b77cf3033e805bb0f5296f51b4b
WORKDIR /app
COPY requirements.txt constraints.txt .
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements.txt -c constraints.txt
COPY app ./app
COPY schemas ./schemas
COPY migrations ./migrations
COPY tests ./tests
RUN useradd --create-home cinema
USER cinema
ENV PYTHONUNBUFFERED=1
CMD ["uvicorn", "app.producer:app", "--host", "0.0.0.0", "--port", "8000"]
