@echo off
echo Building and starting Chat Application...

REM Build the application
echo Building application...
call mvn clean package

REM Start the server in the background
echo Starting server...
start "Chat Server" cmd /c "%JAVA_HOME%\bin\java -cp target/chat-app-1.0-SNAPSHOT.jar com.chatapp.server.ChatServer"

REM Wait a bit for the server to start
timeout /t 5

REM Start the client
echo Starting client...
"%JAVA_HOME%\bin\java" --module-path "%~dp0javafx-sdk-16\lib" --add-modules javafx.controls,javafx.fxml,javafx.media -cp target/chat-app-1.0-SNAPSHOT.jar com.chatapp.client.ChatGUI

REM Create the regular run script
(
echo @echo off
echo echo Starting Chat Application...
echo "%JAVA_HOME%\bin\java" --module-path "%~dp0javafx-sdk-16\lib" --add-modules javafx.controls,javafx.fxml,javafx.media -cp target/chat-app-1.0-SNAPSHOT.jar com.chatapp.client.ChatGUI
echo pause
) > run.bat

echo Setup complete! Next time, just run run.bat
pause 