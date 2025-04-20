# 🧠 olt manager — n-multifibra

🔧 project by **eduardo tomaz** — internal tool for managing huawei olts at **n-multifibra**

> a simple and intuitive java tool to connect, diagnose and monitor huawei olts — with ssh access, signal analysis, visual outage tracking and pdf report export.

---

## ⚠️ before you start

just a few things to keep in mind before running it:

- ✅ java **version 22 or higher** is required  
- ✅ make sure you're connected to the **vpn** or internal network  
- ✅ no olt should be offline or unreachable
- ✅ **port 22** (ssh) must not be blocked by firewall/antivirus  
- ❗ **credentials and IPs are not included in this repo** – see below 👇

---

## 🔐 where are the secrets?

this repo is public, so:

- all sensitive info (like **olt ip addresses**, **ssh username**, and **password**)  
  are stored in a separate file  
- that file is **ignored by git** via `.gitignore`, so it won’t be uploaded here  
- you'll need to create your own secret file with the required data to run the app correctly
- if you are an n-multifibra employee, contact Eduardo Tomaz cuz he will send you the correct java


## 🚀 what it does

- automatic **ssh connection** to huawei olts (via jsch)  
- **signal check** by gpon interface  
- **visual diagnostics** for detecting outages  
- built-in **terminal** for sending custom commands  
- **pdf report export** with clean layout (openpdf)  
- ui built with **javafx**  
- `.exe` generation with **launch4j**

---

## 📚 libs used

- [`jsch`](http://www.jcraft.com/jsch/) — ssh access in java  
- [`javafx`](https://openjfx.io/) — modern ui toolkit  
- [`openpdf`](https://github.com/LibrePDF/OpenPDF) — generates pdf reports  
- [`launch4j`](http://launch4j.sourceforge.net/) — creates windows executables  

---

## 📞 support

any issues? just reach out to **eduardo tomaz**. 
always happy to help. 😊

