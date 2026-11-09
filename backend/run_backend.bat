@echo off
REM Starts the Samajh Sakhi backend on your laptop (Windows).
cd /d %~dp0
if not exist .venv (
  echo Creating virtual environment...
  python -m venv .venv
)
call .venv\Scripts\activate
pip install -r requirements.txt
if not exist .env (
  copy .env.example .env >nul
  echo.
  echo A new .env file was created. Paste your Gnani API key after GNANI_API_KEY= , save, close Notepad.
  notepad .env
)
echo.
echo Backend starting on port 8000. Keep this window open.
echo Phone test: open http://YOUR_LAPTOP_IP:8000/health in the phone browser.
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
pause
