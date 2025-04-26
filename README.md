
# 🧠 OLT Manager — N-Multifibra

🔧 project by Eduardo Tomaz — internal tool for managing Huawei OLTs at *N-Multifibra*

> a simple and intuitive Java tool to connect, diagnose and monitor Huawei OLTs — with SSH access, signal analysis, visual outage tracking and more!

📶 if you're an employee at **N-Multifibra**, just reach out to **Eduardo Tomaz** — he'll provide you with the ready-to-use project with all correct credentials and olt lists.

---

## 🚀 what it does

- SSH-RSA connection to huawei olt terminal (via jsch)
- real-time signal analysis for each PON: captures Tx/Rx levels, calculates averages, and alerts for critical or borderline levels
- PON summary: shows all ont details for the selected primary interface
- search by serial (by-sn): type the ONT/ONU serial and get full info instantly
- drop diagnosis: displays the last 10 disconnection events from each ONT/ONU
- postgresql integration: for user login, roles, and permissions
- clean and responsive UI with JavaFX, styled with CSS

---

## 📚 libs used

- [jsch](http://www.jcraft.com/jsch/) — SSH access in Java  
- [javafx](https://openjfx.io/) — for building the UI  
- [openpdf](https://github.com/LibrePDF/OpenPDF) — generate nice-looking PDFs  
- [launch4j](http://launch4j.sourceforge.net/) — wraps the app into a windows .exe  
- [postgresql](https://jdbc.postgresql.org/) — handles login and role control  

---

## 💾 installation on windows

👉 *recommended*: just download the latest **SetupOLTApp1.5.2.1.exe** installer from the *releases section here on github* — everything is pre-packaged and ready to run!

you don’t need to clone the repo or download JavaFX SDK unless you're going to modify the source code.

✅ everything is already packed, including JavaFX and other libs  
✅ just run the compiled OLTApp.exe and have fun

---

## 🧑‍💻 for devs

1. clone the repo:

```bash
git clone https://github.com/toomazs/NM-OLT-App.git
cd NM-OLT-App
```

2. make sure Java 22+ is installed:

```bash
java -version
```

3. open the project in your IDE (IntelliJ recommended)  
JavaFX SDK is already included in `lib/javafx-sdk-24/lib`

4. check `lib/` folder for dependencies  
includes all required .jar files for:
- javafx
- openpdf
- jsch
- postgresql jdbc

make sure they’re added to your module path.

---

## 🛠 database setup (postgresql)

1. create the database:

```sql
CREATE DATABASE nm_olt_db;
```

2. create the users table:

```sql
CREATE TABLE usuarios (
  id serial primary key,
  nome text not null,
  usuario text unique not null,
  senha text not null,
  cargo text not null
);
```

3. insert some default users:

```sql
INSERT INTO usuarios (nome, usuario, senha, cargo)
VALUES
  ('intern user', 'intern', 'nm12345678', 'estagiario'),
  ('admin user', 'admin', 'nm12345678', 'supervisor');
```

---

## 🔐 secrets setup

you’ll need two secret files for the app to work:

### `SecretsDB.java` — database connection

```java
package database;

public class SecretsDB {
    public static final String DB_URL = "jdbc:postgresql://localhost:5432/nm_olt_db";
    public static final String DB_USER = "your_db_user";
    public static final String DB_PASSWORD = "your_db_password";
}
```

📁 save it inside: `src/database/`

---

### `Secrets.java` — ssh credentials + olt list

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

📁 save it inside: `src/` (next to `Main.java`)

---

## 📞 support

any issues? just reach out here or hit me up on instagram: [@tomazdudux](https://www.instagram.com/tomazdudux/)  
always happy to help 😄
