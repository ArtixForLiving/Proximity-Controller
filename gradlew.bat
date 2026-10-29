@echo off
@rem ##########################################################################
@rem  Gradle startup script for Windows (gradlew.bat)
@rem  Compatible with Gradle 5.x wrapper
@rem ##########################################################################

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Use JAVA_HOME if set, otherwise find java on PATH
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo ERROR: JAVA_HOME is not set and no 'java' command found in PATH.
echo Please install Java JDK 8+ and set JAVA_HOME.
exit /b 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto init

echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
exit /b 1

:init
set DEFAULT_JVM_OPTS=-Xmx512m -Xms64m

:execute
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:mainEnd
