@echo off
title OLT Manager
set FX_PATH=%~dp0lib\javafx-sdk-24\lib
java --module-path "%FX_PATH%" --add-modules javafx.controls,javafx.fxml,javafx.graphics --enable-native-access=javafx.graphics -jar OLTApp.jar
pause