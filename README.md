# 🧠 OLT Manager — N-Multifibra

> Internal network management tool developed by Eduardo Tomaz for Huawei OLTs at **N-Multifibra**.

A robust and efficient Java-based application for secure SSH access, diagnostics, signal analysis, and real-time collaboration across Huawei OLT devices.

---

## 📦 Features

- Secure SSH-RSA connection to Huawei OLTs (via JSCH)
- Real-time optical signal analysis: TX/RX levels, averages, and alert system
- PON summary with detailed ONT information
- Serial-based ONT search (ONT/ONU by SN)
- Disconnection diagnosis: historical log of the last 10 ONT drops
- Integrated MariaDB (MySQL) database for login and user roles
- Real-time interaction among connected users
- Modern user interface with theme support and syntax-highlighted terminal

---

## 📚 Libraries & Tools

- [JSCH](http://www.jcraft.com/jsch/) — SSH communication
- [JavaFX](https://openjfx.io/) — graphical user interface
- [OpenPDF](https://github.com/LibrePDF/OpenPDF) — PDF export support
- [Launch4j](http://launch4j.sourceforge.net/) — Windows executable packaging
- [MariaDB Connector/J](https://mariadb.com/kb/en/mariadb-connector-j/) — database integration
- [RichTextFX](https://github.com/FXMisc/RichTextFX) — syntax highlighting in terminal
- [JSON in Java](https://mvnrepository.com/artifact/org.json/json/20140107) — JSON file handling

---

## 💾 Installation (Windows)

**Recommended:** Download the latest **SetupOLTApp1.5.4.0.exe** from the *Releases* section on GitHub.

✅ Bundled with JavaFX, all libraries, and the required JDK  
✅ Desktop shortcut is created after setup  
❌ No manual configuration or JavaFX SDK installation required

---

## 🧑‍💻 For Developers

1. Clone the repository:

```bash
git clone https://github.com/toomazs/NM-OLT-App.git
cd NM-OLT-App
```

2. Ensure Java 22 or later is installed:

```bash
java -version
```

3. Open the project in **IntelliJ IDEA** (recommended).  
JavaFX SDK is already bundled in `lib/javafx-sdk-24/lib`.

4. Check the `lib/` directory for all required `.jar` files (JavaFX, JSCH, RichTextFX, OpenPDF, MariaDB, etc).

---

## 🛠 Database Setup (MariaDB 11.7)

1. Create the database:

```sql
CREATE DATABASE nm_olt_db;
```

2. Create the user table:

```sql
CREATE TABLE usuarios (
  id SERIAL PRIMARY KEY,
  nome TEXT NOT NULL,
  usuario TEXT UNIQUE NOT NULL,
  senha TEXT NOT NULL,
  cargo TEXT NOT NULL
);
```

3. Insert default users:

```sql
INSERT INTO usuarios (nome, usuario, senha, cargo)
VALUES
  ('intern user', 'intern', 'nm12345678', 'estagiario'),
  ('admin user', 'admin', 'nm12345678', 'supervisor');
```

---

## 🔐 Required Secret Files

### `SecretsDB.java` – MariaDB Credentials

```java
package database;

public class SecretsDB {
    public static final String DB_URL = "jdbc:mariadb://localhost:3306/nm_olt_db";
    public static final String DB_USER = "your_db_user";
    public static final String DB_PASSWORD = "your_db_password";
}
```

📁 Place in: `src/database/`

---

### `Secrets.java` – SSH Login & OLT List

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

📁 Place in: `src/` (next to `Main.java`)

---

## 📞 Support

For bug reports or feature suggestions, feel free to contact Eduardo Tomaz directly.  
[@tomazdudux](https://www.instagram.com/tomazdudux/)
