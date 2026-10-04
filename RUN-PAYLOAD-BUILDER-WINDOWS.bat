@echo off
setlocal
cd /d "%~dp0"
echo.
echo DEADSHOT BRIDGE PAYLOAD BUILDER
echo This creates a local import ZIP from your own DeadShot game files.
echo No game data is downloaded or uploaded.
echo.
where py >nul 2>nul
if %errorlevel%==0 (
  py -3 tools\build_payload.py
) else (
  python tools\build_payload.py
)
echo.
pause
