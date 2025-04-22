# 🧠 olt manager — n-multifibra

🔧 project by **eduardo tomaz** — internal tool for managing huawei olts at **n-multifibra**

> a simple and intuitive java tool to connect, diagnose and monitor huawei olts — with ssh access, signal analysis, visual outage tracking and pdf report export.

---

## 🚀 what it does

- automatic **ssh connection** to huawei olts (via jsch)  
- **signal check** by gpon interface (custom f/s + p input)  
- **visual diagnostics** for detecting fiber cuts / outages  
- built-in **terminal** for sending custom commands straight to the olt  
- **pdf report export** with clean layout (via openpdf)  
- **postgresql integration** for login, user roles and password updates  
- modern ui with **javafx**, styled with css  

---

## 📚 libs used

- [`jsch`](http://www.jcraft.com/jsch/) — ssh access in java  
- [`javafx`](https://openjfx.io/) — for building the ui  
- [`openpdf`](https://github.com/LibrePDF/OpenPDF) — generate nice-looking pdfs  
- [`launch4j`](http://launch4j.sourceforge.net/) — wraps the app into a windows .exe  
- [`postgresql`](https://jdbc.postgresql.org/) — handles login and role control  

---

## 💾 installation

you **don’t need to clone the repo or download javafx sdk** unless you're going to **modify the source code**.

if you're just running the app:

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

## 🛠️ database setup (postgresql)

1. **create the database**

```sql
create database nm_olt_db;
```

2. **create the users table**  
(use the exact names and structure below — all java files expect this format)

```sql
create table usuarios (
  id serial primary key,
  nome varchar(100) not null,
  usuario varchar(100) unique not null,
  senha varchar(100) not null,
  cargo varchar(50) not null
);
```

3. **insert some default users**

```sql
insert into usuarios (nome, usuario, senha, cargo)
values
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

any issues? just reach out here or hit me up on socials: **@tomazdudux**  
always happy to help. 😄
