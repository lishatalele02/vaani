FROM python:3.12-slim
WORKDIR /srv/backend
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app app
COPY data data
COPY models models
# Secrets come from environment variables / an env file at run time, never from the image.
ENV SAMAJH_DB_PATH=/data/samajh_sakhi.sqlite3 REQUIRE_ACCESS_CODE=true
VOLUME /data
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
