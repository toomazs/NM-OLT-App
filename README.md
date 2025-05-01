
# 🧠 olt manager — n-multifibra

🔧 project by eduardo tomaz — internal tool for managing huawei olts at *n-multifibra*

> a clean & powerful java tool to connect, diagnose and monitor huawei olts — with ssh access, signal analysis, visual outage tracking, and real-time user interaction 🎯

📶 if you're an employee at **n-multifibra**, just reach out to **eduardo tomaz** — he’ll provide you with the ready-to-use version with correct credentials + olt list.

---

## 📦 what it does

- ssh-rsa connection to huawei olt terminal (via jsch)
- real-time signal analysis for each pon: shows tx/rx levels, averages and alerts
- pon summary with all ont details
- search ont by serial (by-sn)
- drop diagnosis: last 10 disconnection events from each ont
- postgresql integration for login and roles
- real-time interaction between the users
- different themes and highlighted terminal

---

## 📚 libs used

- [jsch](http://www.jcraft.com/jsch/) — ssh access  
- [javafx](https://openjfx.io/) — for the ui  
- [openpdf](https://github.com/LibrePDF/OpenPDF) — export to pdf  
- [launch4j](http://launch4j.sourceforge.net/) — windows .exe packaging  
- [postgresql](https://jdbc.postgresql.org/) — database access
- [richtextfx](https://github.com/FXMisc/RichTextFX) — better terminal highlighted
- [json in java](https://mvnrepository.com/artifact/org.json/json/20140107) — compile to .json files

---

## 💾 installing on windows

👉 easiest way: download the latest **SetupOLTApp1.5.3.0.exe** from the *releases section on github*  

✅ comes with everything: javafx, libraries, jdk  
✅ after the setup completed, just run the **OLTApp.exe** and enjoy (will be disponible as shortcut in the desktop too)
❌ no need to install javafx sdk or clone repo unless you’re editing code

---

## 🧑‍💻 for devs

1. clone the repo:

```bash
git clone https://github.com/toomazs/NM-OLT-App.git
cd NM-OLT-App
```

2. make sure java 22+ is installed:

```bash
java -version
```

3. open it in intellij (recommended)  
javafx sdk already included in `lib/javafx-sdk-24/lib`

4. check `lib/` for all jars:  
includes everything you need — javafx, openpdf, jsch, postgresql, json, richtextfx

---

## 🛠 database (postgresql)

1. create the db:

```sql
create database nm_olt_db;
```

2. create users table:

```sql
create table usuarios (
  id serial primary key,
  nome text not null,
  usuario text unique not null,
  senha text not null,
  cargo text not null
);
```

3. insert default users:

```sql
insert into usuarios (nome, usuario, senha, cargo)
values
  ('intern user', 'intern', 'nm12345678', 'estagiario'),
  ('admin user', 'admin', 'nm12345678', 'supervisor');
```

---

## 🔐 secret files you need

### `SecretsDB.java` — db connection

```java
package database;

public class SecretsDB {
    public static final String DB_URL = "jdbc:postgresql://localhost:5432/nm_olt_db";
    public static final String DB_USER = "your_db_user";
    public static final String DB_PASSWORD = "your_db_password";
}
```

📁 put it in: `src/database/`

---

### `Secrets.java` — ssh login + olt list

```java
public class Secrets {
    public static final String SSH_USER = "your_ssh_user";
    public static final String SSH_PASS = "your_ssh_pass";
    public static final String[][] OLT_LIST = {
        {"OLT_NAME_1", "IP_1"},
        {"OLT_NAME_2", "IP_2"}
    };
}
```

📁 put it in: `src/` (next to `Main.java`)

---

## 📞 support

any bugs or suggestions? hit me up here or dm me on insta: [@tomazdudux](https://www.instagram.com/tomazdudux/)  
always down to help 😄
