@echo off
setlocal enabledelayedexpansion

set "MAVEN_VERSION=3.9.9"
set "DIST_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"

if defined MAVEN_USER_HOME (
  set "WRAPPER_BASE=%MAVEN_USER_HOME%\wrapper\dists"
) else (
  set "WRAPPER_BASE=%USERPROFILE%\.m2\wrapper\dists"
)

set "DIST_DIR=%WRAPPER_BASE%\apache-maven-%MAVEN_VERSION%-bin"
set "MVN_HOME=%DIST_DIR%\apache-maven-%MAVEN_VERSION%"
set "MVN_CMD=%MVN_HOME%\bin\mvn.cmd"

if exist "%MVN_CMD%" goto run_maven

echo [mvnw] Apache Maven %MAVEN_VERSION% not found locally. Downloading...
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
set "ZIP_FILE=%DIST_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip"

curl.exe -fsSL -o "%ZIP_FILE%" "%DIST_URL%"
if errorlevel 1 (
  echo [mvnw] ERROR: Failed to download %DIST_URL%
  exit /b 1
)

tar.exe -xf "%ZIP_FILE%" -C "%DIST_DIR%"
if errorlevel 1 (
  echo [mvnw] ERROR: Failed to extract %ZIP_FILE%
  exit /b 1
)

del /q "%ZIP_FILE%" >nul 2>&1

if not exist "%MVN_CMD%" (
  echo [mvnw] ERROR: Expected Maven executable not found at %MVN_CMD%
  exit /b 1
)

:run_maven
call "%MVN_CMD%" %*
exit /b %ERRORLEVEL%
