@echo off
cd /d %~dp0..\backend
echo Starting course-web (dev profile, port 8080)...
mvn -pl course-web -am spring-boot:run
