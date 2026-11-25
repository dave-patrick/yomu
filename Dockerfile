FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    YOMU_DATA_DIR=/var/data/yomu

WORKDIR /app

COPY web/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
RUN python web/build_roadmap.py

RUN mkdir -p /var/data/yomu
EXPOSE 8011

WORKDIR /app/web
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8011"]
