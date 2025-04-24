# 🧠 olt manager — n-multifibra

🔧 project by **Eduardo Tomaz** — internal tool for managing huawei olts at **n-multifibra**

> a simple and intuitive java tool to connect, diagnose and monitor huawei olts — with ssh access, signal analysis, visual outage tracking and pdf report export.

---

## 🚀 what it does

- **ssh-rsa connection** to huawei olt terminal (via jsch)  
- **real-time signal analysis** for each pon: captures TX/RX levels, calculates averages, and alerts for critical or borderline levels  
- **pon summary**: shows all ONT details for the selected primary interface  
- **search by serial (by-sn)**: type the ONT/ONU serial and get full info instantly  
- **drop diagnosis**: displays the last 10 disconnection events from each ONT/ONU  
- **breakages tab**: every 30 mins, all registered olts are automatically scanned for fiber cuts or suspicious signal drops  
- **postgresql integration**: for user login, roles, and permissions  
- clean and responsive UI with **javafx**, styled with **css** 

---

## 📚 libs used

- [`jsch`](http://www.jcraft.com/jsch/) — ssh access in java  
- [`javafx`](https://openjfx.io/) — for building the ui  
- [`openpdf`](https://github.com/LibrePDF/OpenPDF) — generate nice-looking pdfs  
- [`launch4j`](http://launch4j.sourceforge.net/) — wraps the app into a windows .exe  
- [`postgresql`](https://jdbc.postgresql.org/) — handles login and role control  

---

## 💾 installation on windows

if you're a employee at **n-multifibra**, just reach out to **Eduardo Tomaz** — he'll provide you with all the ready-to-use `Secrets.java`, `SecretsDB.java` autoconfigured and a fully working version of the code — literally the project with all correct credentials and olts lists. <br>

if you're just a stranger running by here, you **don’t need to clone the repo or download javafx sdk** unless you're going to **modify the source code**.

✅ everything is already packed, including javafx and other libs.  
✅ just run the compiled `OLTApp.exe` provided and have fun.

if you're a dev and want to tweak the project:

1. **clone the repo**

```bash
git clone https://github.com/toomazs/NM-OLT-App.git
cd NM-OLT-App
```

2. **make sure you have java 22+ installed**

```bash
java -version
```

3. **open the project in your ide** (intellij recommended)  
javafx sdk is already included in `lib/javafx-sdk-24/lib` — no need to install it manually.

4. **check `lib/` folder for dependencies**  
includes all required `.jar` files for:
- javafx
- openpdf
- jsch
- postgresql jdbc

make sure they’re added to your module path.

---

## 🐧 installation on linux

you got two easy ways to run it on linux:

### 1. manual launcher  
just double-click `run_oltapp.sh` or use the shortcut `OLTApp.desktop`.  
(make sure the `.sh` file is executable)

### 2. .deb installer  
super simple — just run:

```bash
sudo dpkg -i oltapp_1.0_all.deb
```

that’s it. everything’s bundled and ready to go.

---

## 🛠️ database setup (postgresql)

1. **create the database**

```sql
CREATE DATABASE nm_olt_db;
```

2. **create the users table**  
(use the exact names and structure below — all java files expect this format)

```sql
CREATE TABLE usuarios (
  id SERIAL PRIMARY KEY,
  nome TEXT NOT NULL,
  usuario TEXT UNIQUE NOT NULL,
  senha TEXT NOT NULL,
  cargo TEXT NOT NULL
);
```

3. **insert some default users**

```sql
INSERT INTO usuarios (nome, usuario, senha, cargo)
VALUES
  ('intern user', 'intern', 'nm12345678', 'estagiario'),
  ('admin user', 'admin', 'nm12345678', 'supervisor');
```

4. **set up the secrets**

you’ll need **two secret files** for the app to run properly:

### 🔐 `SecretsDB.java` — database connection

```java
package database;

public class SecretsDB {
    public static final String DB_URL = "jdbc:postgresql://localhost:5432/nm_olt_db";
    public static final String DB_USER = "your_db_user";
    public static final String DB_PASSWORD = "your_db_password";
}
```

📁 **save this file inside:** `src/database/`  
(it must be in the same folder as `DatabaseManager.java`)

---

### 🔐 `Secrets.java` — ssh credentials + olt list

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

📁 **save this file inside:** `src/` (next to `Main.java`)

---

## 📞 support

any issues? just reach out here or hit me up on socials: [**@tomazdudux**](https://www.instagram.com/tomazdudux/) <br>
always happy to help. 😄
