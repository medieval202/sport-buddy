@echo off
setlocal
set "APP_JAR=%~dp0sports-buddy-0.1.0.jar"
set "APP_PORT=8081"

if /I "%~1"=="--check" goto check_ok
if not exist "%APP_JAR%" goto missing_jar

set "JAVA_EXE=C:\Users\RedmiBook\.jdks\ms-17.0.19\bin\java.exe"
if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA_EXE%" (
  where java >nul 2>nul
  if not errorlevel 1 set "JAVA_EXE=java"
)

if /I "%JAVA_EXE%"=="java" goto run_from_path
if not exist "%JAVA_EXE%" goto missing_java

:run_local_java
echo Starting Sports Buddy...
echo URL: http://127.0.0.1:%APP_PORT%
start "" powershell -NoProfile -WindowStyle Hidden -Command "Start-Sleep -Seconds 10; Start-Process 'http://127.0.0.1:%APP_PORT%'"
"%JAVA_EXE%" -jar "%APP_JAR%" --server.address=127.0.0.1 --server.port=%APP_PORT%
goto stopped

:run_from_path
echo Starting Sports Buddy...
echo URL: http://127.0.0.1:%APP_PORT%
start "" powershell -NoProfile -WindowStyle Hidden -Command "Start-Sleep -Seconds 10; Start-Process 'http://127.0.0.1:%APP_PORT%'"
java -jar "%APP_JAR%" --server.address=127.0.0.1 --server.port=%APP_PORT%
goto stopped

:missing_jar
echo JAR not found:
echo %APP_JAR%
pause
exit /b 1

:missing_java
echo Java 17 was not found.
echo Install JDK 17 or set JAVA_HOME, then run this file again.
pause
exit /b 1

:check_ok
if not exist "%APP_JAR%" goto missing_jar
echo run.cmd check OK
exit /b 0

:stopped
echo.
echo Server stopped. Read the error above.
pause
exit /b 1
