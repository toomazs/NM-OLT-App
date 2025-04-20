# 🧠 olt manager — n-multifibra

🔧 project by **eduardo tomaz** — internal use only for **n-multifibra employees**

> a simple and intuitive tool to manage and troubleshoot huawei olts, with ssh access, signal analysis, outage diagnostics, and report exports.

---

## ⚠️ before you start

make sure you’ve checked the following:

- ✅ java **version 22 or higher** is installed  
- ✅ vpn or internal network connection is active  
- ✅ no olt should be offline or unreachable  
- ✅ firewall/antivirus isn’t blocking port 22 (ssh)  
- ✅ olts are already set up with ip, username, and password  

---

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

any issues? just reach out to **eduardo tomaz** from the tech support team.  
always happy to help. 😊

---

> ⚠️ heads up: this project is for **internal use only** by the n-multifibra team.  
> don’t share it outside, pls.

