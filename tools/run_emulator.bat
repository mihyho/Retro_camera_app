@echo off
rem 에뮬레이터를 작은 창으로 켠다. 창 크기는 첫 번째 인자(배율)로 정한다. 예: run_emulator.bat 0.5
rem 기본 0.4 = 폰 해상도(1080x2400)의 40%인 약 450x960 창. 화면 해상도는 그대로다.
set SCALE=%1
if "%SCALE%"=="" set SCALE=0.4
"%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd Pixel_8 -scale %SCALE%
