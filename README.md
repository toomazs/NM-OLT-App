# 🧠 olt manager — n-multifibra

🔧 project by **eduardo tomaz** — internal tool for managing huawei olts at **n-multifibra**

> a simple and intuitive java tool to connect, diagnose and monitor huawei olts — with ssh access, signal analysis, visual outage tracking and pdf report export.

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

## 🖼️ screenshots

### 📡 olt connection

<img src="https://i.imgur.com/G2CAmJV.png" width="535"/>

---

### 🔍 signal diagnostics

<img src="https://i.imgur.com/EsNE742.png" width="535"/>

---

### 📊 pon summary

<img src="https://i.imgur.com/rbIGZUm.png" width="535"/>

---

### 📌 by-sn query

<img src="https://i.imgur.com/h9RWPQR.png" width="535"/>

---

### ⚠️ ont drops detection

<img src="https://i.imgur.com/HqOWTtJ.png" width="535"/>

---

### 🚨 fiber breakage tracker

<img src="https://i.imgur.com/43zIu1E.png" width="535"/>
> ⏱️ every 30 mins, the app performs a full scan on all olts and updates this screen with critical fiber breakages

---

## 📚 libs used

- [`jsch`](http://www.jcraft.com/jsch/) — ssh access in java  
- [`javafx`](https://openjfx.io/) — modern ui toolkit  
- [`openpdf`](https://github.com/LibrePDF/OpenPDF) — generates pdf reports  
- [`launch4j`](http://launch4j.sourceforge.net/) — creates windows executables  

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
- if you are an **n-multifibra** employee, contact **eduardo tomaz** cuz he will send you the correct file :D

### 📄 use the `Example.txt` as a template!

to make it easier:

- there's a file named `Example.txt` included in this repo, is located on `src/Example.txt`
- it shows **exactly** how your secret file should look  
- just follow the structure and replace the placeholders (`SSH_USER`, `SSH_PASS`, IPs, etc.)
- save it as a `.java` file (like `Secrets.java`) and put it in `/src` next to `Main.java`

---

## 📞 support

any issues? just reach out me here or in my social medias: **@tomazdudux** <br>
always happy to help. 😊
