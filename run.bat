@echo off
chcp 65001 > nul
echo [ChatServer] 빌드 및 서버 시작 중...
gradlew.bat runServer
pause
