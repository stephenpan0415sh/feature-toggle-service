@echo off
echo ========================================
echo Generating JaCoCo Coverage Reports
echo ========================================
echo.

REM Clean and run tests with coverage
echo [1/3] Running tests with JaCoCo agent...
call mvn clean test -DskipTests=false

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Tests failed! Check the output above.
    pause
    exit /b 1
)

echo.
echo [2/3] Generating individual module reports...
echo.

REM Generate reports for each module
call mvn jacoco:report

echo.
echo [3/3] Aggregating reports...
echo.

REM Create coverage-report directory
if not exist coverage-report mkdir coverage-report

REM Copy reports from each module
if exist ff-server\target\site\jacoco (
    xcopy /E /I /Y ff-server\target\site\jacoco coverage-report\ff-server
    echo ✓ ff-server report copied
)

if exist ff-sdk-java\target\site\jacoco (
    xcopy /E /I /Y ff-sdk-java\target\site\jacoco coverage-report\ff-sdk-java
    echo ✓ ff-sdk-java report copied
)

if exist ff-sdk-core\target\site\jacoco (
    xcopy /E /I /Y ff-sdk-core\target\site\jacoco coverage-report\ff-sdk-core
    echo ✓ ff-sdk-core report copied
)

if exist ff-common\target\site\jacoco (
    xcopy /E /I /Y ff-common\target\site\jacoco coverage-report\ff-common
    echo ✓ ff-common report copied
)

echo.
echo ========================================
echo Coverage reports generated successfully!
echo ========================================
echo.
echo Reports location: coverage-report\
echo.
echo To view reports:
echo   - ff-server: coverage-report\ff-server\index.html
echo   - ff-sdk-java: coverage-report\ff-sdk-java\index.html
echo   - ff-sdk-core: coverage-report\ff-sdk-core\index.html
echo   - ff-common: coverage-report\ff-common\index.html
echo.
echo To push to GitHub:
echo   git add coverage-report
echo   git commit -m "Add coverage reports"
echo   git push origin main
echo.
pause
