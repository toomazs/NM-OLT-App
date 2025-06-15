# 💙 NM OLT App v1.6.0.0

*Desenvolvido por **Eduardo Tomaz** para **N-Multifibra***

---

## 📋 Sobre o Projeto

O **NM OLT App** é uma solução completa desenvolvida em Java que oferece acesso SSH seguro, diagnósticos avançados e monitoramento em tempo real para gestão centralizada de ONTs dos clientes dentro das OLTs, reduzindo o tempo de resposta e facilitando o trabalho por meio de automações internas.<br>
A ferramenta foi criada especificamente para otimizar o trabalho das equipes de Suporte e T.I. da ***N-Multifibra***, proporcionando uma interface intuitiva, automática e confortavel para gerenciamento dos clientes na rede.

<br>

---

## 🎯 Recursos Principais

### 🔐 **Login & Alteração de Senha**
- Autenticação segura com controle de permissões por cargo
- Alteração de senha integrada, com senha padrão inicial protegida e criptografada
- Verificação em tempo real do status das credenciais
- Sessão protegida com logout forçado ao fechar o aplicativo

<details>

<summary> Imagens </summary>

- Login *(com último user salvo via .json)* <br>
  <img src="https://i.imgur.com/xgSbtHO.jpeg" width="300"/><br><br>
- Alteração de Senha <br>
  <img src="https://i.imgur.com/TdPSwSV.jpeg" width="300"/> <br>

</details> <br>



### 🌐 **OLTs**
- Acesso direto às OLTs em tempo real via autenticação SSH
- Conexão e desconexão seguras, sem cache e sem sobrecarga das OLTs
- Status Online/Offline por protocolos TCP e ICMP *(fallback automático)*
- Adição manual e dinâmica de OLTs pelo usuário final
<details>
<summary> Imagens </summary>

- Aba OLTs <br>
  <img src="https://i.imgur.com/oR2ljOI.jpeg" width="400"/><br><br>
- Inside-Terminal *(ssh)*<br>
  <img src="https://i.imgur.com/rj8uKrH.png" width="400"/> <br>

</details> <br>


### 📡 **Consulta de Sinal**
- Análise detalhada dos sinais RX e TX de cada ONT/ONU na PON
- Cálculo automático das médias de sinais RX e TX
- Alertas para sinais críticos (abaixo de -29.00 dBm)
- Identificação de sinais incompatíveis com a média da PON (entre -27 e -29 dBm)
<details>
<summary> Imagens </summary>

- Consulta de Sinal <br>
  <img src="https://i.imgur.com/AXUHqa1.png" width="400"/><br><br>
- Média dos Sinais e Alertas <br>
  <img src="https://i.imgur.com/WVFEP25.png" width="400"/> <br>

</details> <br>

### 📋 **Summary**
- Visão geral das PONs (F/S/P) com detalhes completos de cada ONT/ONU
- Autoverificação de rompimentos e drops rompidos
- Status em tempo real de todos os dispositivos
<details>

<summary> Imagens </summary>

- Consulta de Summary <br>
  <img src="https://i.imgur.com/UoDPWuG.png" width="400"/><br><br>
- Análise de Rompimentos ou Drops <br>
  <img src="https://i.imgur.com/4HUAuw6.png" width="400"/> <br>

</details> <br>

### 🔍 **By-SN**
- Localização rápida de ONT/ONU por número de série (SN)
- Acesso prioritário ao IP remoto via DHCP
- Informações completas do dispositivo (T-CONT, GEM PORT, TR-069, etc.)
<details>
<summary> Imagens </summary>

- Consulta de By-SN <br>
  <img src="https://i.imgur.com/6MUeqsG.png" width="400"/><br>

</details> <br>

### 📉 **Quedas**
- Registro detalhado das 10 últimas desconexões por ONT/ONU
- Horários precisos de cada ocorrência
- Resumo de cada incidente e respectiva causa
<details>
<summary> Imagens </summary>

- Consulta de Quedas <br>
  <img src="https://i.imgur.com/DHpL4wK.png" width="400"/><br>

</details> <br>

### 📈 **Tráfego**
- Monitoramento em tempo real do consumo por até 2 minutos
- Conversão automática de Kbps para Mbps
- Opção de interrupção do monitoramento a qualquer momento
<details>
<summary> Imagens </summary>

- Consulta em tempo real de Tráfego <br>
  <img src="https://i.imgur.com/FryBWL9.png" width="400"/><br>

</details> <br>

### ⚙️ **Serviços**
- Acompanhamento dos serviços autorizados por ONT/ONU
- Classificação por VLANs (100, 101, 102)
- Status detalhado de cada serviço ativo (Internet, Acesso Remoto, VoIP, etc.)
<details>
<summary> Imagens </summary>

- Consulta de Serviços <br>
  <img src="https://i.imgur.com/DVw1YD2.png" width="400"/><br><br>

</details> <br>

> 💡 Bastando informar apenas o **F/S/P** ou o **ID da ONT** em cada aba! 

<br>

### 🎫 **Chamados** (Admin e Devs)
- Acompanhamento dos tickets internos abertos
- Respostas em tempo real aos tickets (em manutenção, resolvido, etc.)
- Exclusão de tickets desnecessários
<details>
<summary> Imagens </summary>

- Gerenciamento de Chamados <br>
  <img src="https://i.imgur.com/hFZXcog.jpeg" width="400"/><br><br>

</details> <br>

---

## 🚀 Recursos Secundários


### 🧠 **Diagnósticos Inteligentes**
- **Pattern Matching com Regex Avançado** em todas as abas
- **Análise Automática** de sinais e detectação de anomalias
- **Resumos Inteligentes** de dados complexos
- **Alertas Proativos** para problemas críticos
<details>
<summary> Patterns Matchings e Regex </summary>
  <br>

## 🧑‍💻 Regex Globais

| **Regex / Padrão**       | **Finalidade**                                                                                                                                           | **Exemplo Prático em `olt_teste_01`**                                                                                                                                                  |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `IP_REGEX`               | Encontrar e destacar endereços IP no texto do terminal.                                                                                                  | Em uma mensagem como `Conectado ao host 10.0.40.10`, o padrão localiza e permite destacar o `10.0.40.10`.                                                                           |
| `CR_PROMPT_PATTERN`      | Detectar prompts que exigem que a tecla "Enter" (`<cr>`) seja pressionada para confirmar uma ação ou completar um comando.                              | `olt_teste_01(config-if-gpon-0/1)# commit { <cr> }:`. O código detecta isso e envia automaticamente o "Enter".                                                                         |
| `MORE_PROMPT_PATTERN`    | Identificar quando a OLT pausa uma saída longa e exibe uma mensagem como `---- More ----`, indicando que há mais conteúdo a ser exibido.                 | `---- More ( Press 'Q' to break ) ----`. O código detecta e pode enviar um espaço para continuar.                                |
| `oltPromptRegex`         | Reconhecer o prompt de comando da OLT. É o principal indicador de que um comando terminou de ser executado e a OLT está pronta para a próxima instrução. | `olt_teste_01#` (modo enable)<br>`olt_teste_01(config)#` (modo de configuração)<br>`olt_teste_01(config-if-gpon-0/1)#` (modo de configuração da interface)                             |

<br>

---

## 📡 Consulta de Sinal (`queryOpticalSignal`)

- **Comando:**  
  `display ont optical-info 0 all` *(executado na interface gpon 0/1/0)*

- **Padrão Regex:**
  ```regex
  ^\s*(\d+)\s+(-?\d+\.\d+)\s+(-?\d+\.\d+)\s+(-?\d+\.\d+)\s+(\d+)\s+(-?\d+\.\d+)\s+(-?\d+)\s+(\d+)\s*$
  ```

- **Exemplo capturado:**
  ```
  0  -18.45   2.98  -19.12   41   3.305    19      985
  ```

<br>

---

## 📋 Summary (`queryPonSummary`)

- **Comandos:**  
  `display port desc 0/1/0`  
  `display ont info summary 0/1/0`


| Padrão no Código           | Finalidade                                                                 | Exemplo de Texto Capturado                              |
|----------------------------|----------------------------------------------------------------------------|----------------------------------------------------------|
| `descDataLinePattern`      | Captura a linha de descrição da porta para a F/S/P.                       | `0/ 1/ 0 - PRIMARIA 00--CABO00`                      |
| `summaryCountPattern`      | Extrai o número total de ONTs e quantas estão online.                     | `In port 0 / 1 / 0, total of ONTs are: 32, online: 29.` |
| `statusPattern`            | Captura os detalhes de status das ONTs (online/offline).                  | `0 online 01/06/2025 10:00:15 01/01/1970 00:00:00 -`     |
| `infoPattern`              | Captura SN, modelo e nome do cliente.                                     | `0 HWTCaabbccdd HG8245H 985 -18.4/-19.1 Cliente-Exemplo-01` |
| `statusPatternForAnalysis`| Identifica ONTs offline para análise de rompimentos.                      | `1 offline 10/05/2025 09:20:00 08/06/2025 04:15:30 Dying-gasp` |

<br>

---

## 🔎 By-SN (`queryOntInfoBySn`)

- **Comando:**  
  `display ont info by-sn <SERIAL>`

| Padrão no Código | Finalidade                                         | Exemplo de Texto Capturado                                  |
|------------------|----------------------------------------------------|--------------------------------------------------------------|
| `keyValuePattern`| Captura chaves e valores no formato `Chave : Valor`| `F/S/P : 0/1/0` <br> `ONT-ID : 0`                            |
| `ontIpPattern`   | Captura o endereço IP da ONT                       | `ONT IP 1 address/mask : 192.168.100.5 / 255.255.255.0`       |

<br>

---

## 📉 Quedas (`queryOntRegisterInfo`)

- **Comando:**  
  `display ont register-info 0 0` *(executado na interface gpon 0/1/0)*

- **Exemplo de bloco processado:**
  ```
  Index          : 1
  Register time  : 2025/06/01 10:00:15
  Deregister time: 2025/06/08 14:30:00
  DownCause      : LOSi/LOBi alarm
  ```

<br>

---

## 📈 Tráfego (`queryOntTraffic`)

- **Comando:**  
  `display ont traffic 0 0` *(executado na interface gpon 0/1/0)*

| Padrão no Código     | Finalidade                          | Exemplo de Texto Capturado             |
|----------------------|-------------------------------------|-----------------------------------------|
| `upTrafficPattern`   | Captura o tráfego de upload (kbps)  | `Up traffic(kbps) : 8192`              |
| `downTrafficPattern` | Captura o tráfego de download (kbps)| `Down traffic(kbps) : 51200`           |

<br>

---

## ⚙️ Serviços (`queryServicePortInfo`)

- **Comando:**  
  `display service-port port 0/1/0 ont 0`

| Padrão no Código | Finalidade                                                | Exemplo de Texto Capturado                      |
|------------------|-----------------------------------------------------------|--------------------------------------------------|
| `dataPattern`    | Captura a linha de detalhes de cada `service-port`.       | `1 100 vlan-100 - 0/1/0 0 - - vlan 100 - - up`  |
| `totalMatcher`   | Captura o resumo final da saída.                          | `Total : 2 (Up/Down : 2/2)`                     |

<br>
</details> <br>


### 🔄 Detecção Automática de OLTs
- Status Online/Offline via protocolos TCP e ICMP
- Verificação de porta SSH acessível
- Fallback automático entre protocolos
<details>
<summary> Exemplos da Detecção </summary>
  <br>

- OLT Offline (Bloqueada/Desligada) <br>
  <img src="https://i.imgur.com/bsUR2OT.png" width="150"/> <br><br>
- OLT Online (Porta Aberta e Acessível) <br>
  <img src="https://i.imgur.com/IYxKO27.png" width="150"/> <br>

Os protocolos TCP e ICMP verificarão se a porta está aberta e acessível para autenticação.<br>
</details> <br>

### ➕ Adição Dinâmica e Filtragem de OLTs
- Interface modal para adicionar novas OLTs-
- Integração automática com a lista existente
- Validação de conectividade em tempo real
- Filtragem por busca, status, IP, etc.
<details>
<summary> Imagens do Modal de Adição </summary>
  <br>

- Modal de Adicionar OLT <br>
  <img src="https://i.imgur.com/UbzG6eE.jpeg" width="500"/> <br><br>
- Exemplo de Adição de OLT<br>
  <img src="https://i.imgur.com/beRVcjb.jpeg" width="500"/> <br><br>
- Exemplo de Filtragem de OLTs <br>
  <img src="https://i.imgur.com/tVuFZ6r.jpeg" width="500"/> <br><br> 

</details> <br>


### 🎫 **Sistema de Tickets Internos**
- Suporte direto do desenvolvedor
- Correção rápida de bugs
- Modal para sugestões de melhorias
<details>
<summary> Imagens do Modal de Tickets </summary>
    <br>

- Modal de Abrir Ticket <br>
  <img src="https://i.imgur.com/vZBZ9DH.jpeg" width="500"/><br><br>
- Modal dos seus Tickets abertos <br>
  <img src="https://i.imgur.com/fg9PzmM.jpeg" width="500"/><br><br>

</details> <br>


### 📊 **Exportação de Relatórios**
Geração de relatórios completos usando:
- **Apache POI** para arquivos Excel (XLSX)
- **OpenPDF** para documentos PDF
- **FileWriter** para CSV e TXT <br>
<details>
<summary> Imagens do Modal de Exports </summary>
  <br>

- Modal de relatórios (exports) em PDF, XLSX, CSV e TXT<br>
  <img src="https://i.imgur.com/vGWey3P.jpeg" width="500"/><br><br>

</details> <br>


### 🎨 **Temas Personalizáveis**

<details>
<summary> Ver todos os 14 temas disponíveis</summary>

- 💜 **Roxo** (Tema Padrão)<br>
  <img src="https://i.imgur.com/oR2ljOI.jpeg" width="400"/><br><br>
- 🖤 **All Black**<br>
  <img src="https://i.imgur.com/PPDFfPZ.jpeg" width="400"/><br><br>
- 🤍 **All White**<br>
  <img src="https://i.imgur.com/ant4yIJ.jpeg" width="400"/><br><br>
- 🧛 **Dracula**<br>
  <img src="https://i.imgur.com/4UH0bth.jpeg" width="400"/><br><br>
- 🔮 **GitHub Dark**<br>
  <img src="https://i.imgur.com/R1MBEVX.jpeg" width="400"/><br><br>
- 🟪 **Shades of Purple**<br>
  <img src="https://i.imgur.com/aEztdxG.jpeg" width="400"/><br><br>
- 🦉 **Night Owl**<br>
  <img src="https://i.imgur.com/lYatAQg.jpeg" width="400"/><br><br>
- 🦉 **Light Owl**<br>
  <img src="https://i.imgur.com/uhS34aD.jpeg" width="400"/><br><br>
- 🍮 **Creme**<br>
  <img src="https://i.imgur.com/JK0KeuX.jpeg" width="400"/><br><br>
- 🧑‍💻 **Terminal**<br>
  <img src="https://i.imgur.com/DLueSdT.jpeg" width="400"/><br><br>
- 💙 **Azul**<br>
  <img src="https://i.imgur.com/Tm6GgZo.jpeg" width="400"/><br><br>
- 💚 **Verde**<br>
  <img src="https://i.imgur.com/ZAJoVnw.jpeg" width="400"/><br><br>
- ❤️ **Vermelho**<br>
  <img src="https://i.imgur.com/NRGChpb.jpeg" width="400"/><br><br>
- 🩷 **Rosa**<br>
  <img src="https://i.imgur.com/RpVv76u.jpeg" width="400"/><br><br>

</details>

---

## 🛠️ Libs

| Bibliotecas | Versão | Função |
|------------|--------|---------|
| ![JavaFX](https://img.shields.io/badge/JavaFX-22+-orange) | 22+ | Interface gráfica moderna |
| ![JSCH](https://img.shields.io/badge/JSCH-0.1.55-blue) | 0.1.55 | Comunicação SSH segura |
| ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-42.7.6-336791) | 42.7.6 | Conexão com banco de dados |
| ![Apache POI](https://img.shields.io/badge/Apache_POI-5.4.1-green) | 5.4.1 | Exportação XLSX |
| ![OpenPDF](https://img.shields.io/badge/OpenPDF-1.3.32-red) | 1.3.32 | Exportação PDF |
| ![RichTextFX](https://img.shields.io/badge/RichTextFX-0.11.5-purple) | 0.11.5 | Terminal com syntax highlighting |
| ![JNA](https://img.shields.io/badge/JNA-5.17.0-brightgreen) | 5.17.0 | Acesso nativo ao sistema operacional |
| ![Ikonli](https://img.shields.io/badge/Ikonli-FontAwesome-ff6b35) | 12.3.1 | Ícones FontAwesome para JavaFX |
| ![XMLBeans](https://img.shields.io/badge/XMLBeans-5.3.0-yellow) | 5.3.0 | Processamento de documentos XML |
| ![SparseBitSet](https://img.shields.io/badge/SparseBitSet-1.3-lightgrey) | 1.3 | Estruturas de dados otimizadas |
| ![Log4j](https://img.shields.io/badge/Log4j-3.0.0beta3-brown) | 3.0.0-beta3 | Sistema de logging avançado |
| ![Launch4j](https://img.shields.io/badge/Launch4j-3.50-yellow) | 3.50 | Empacotamento Windows |

---

## 💾 Instalação

⚠️ Se você for um colaborador da ***N-Multifibra***, procure o Eduardo Tomaz — ele lhe fornecerá o projeto completo personalizado desenvolvido internamente para a empresa.<br> <br>
Se você for um desconhecido, siga os passos abaixo:

### 🔧 Configuração do Ambiente

```bash
# 1. Clone o repositório
git clone https://github.com/toomazs/NM-OLT-App.git
cd NM-OLT-App

# 2. Verifique a versão do Java (Recomendado Java 24)
java -version

# 3. Abra na sua IDE favorita
# Todas as dependências estão em lib/
```

### 🗄️ Configuração do Banco de Dados

O sistema é compatível com **PostgreSQL**, **MariaDB (MySQL)**, **SQLite** e **NoSQL**.

#### Exemplo com PostgreSQL:

```sql
-- Criar database
CREATE DATABASE olt_db;

-- Tabela de usuários
CREATE TABLE usuarios (
    id SERIAL PRIMARY KEY,
    nome TEXT NOT NULL,
    usuario TEXT UNIQUE NOT NULL,
    senha TEXT NOT NULL,
    cargo TEXT NOT NULL;
);

-- Inserir usuários
INSERT INTO usuarios (nome, usuario, senha, cargo) VALUES
    ('Usuário Padrão', 'intern', '12345678', 'estagiario'),
    ('Administrador', 'admin', '12345678', 'supervisor');
--  ... Quantos users quiser
```

### ⚙️ Arquivos de Configuração (Obrigatório)

#### 🔐 SecretsDB.java - Credenciais do Banco

```java
package database;

public class SecretsDB {
  // Configuração PostgreSQL (exemplo)
  public static final String DB_URL = "jdbc:postgresql://localhost:5432/olt_db";
  public static final String DB_USER = "usuario_db";
  public static final String DB_PASSWORD = "senha_db";
}
```

📁 **Localização:** `src/database/SecretsDB.java`

#### 🌐 Secrets.java - SSH e Lista de OLTs

```java
public class Secrets {
  // Credenciais SSH
  public static final String SSH_USER = "usuario_ssh";
  public static final String SSH_PASS = "senha_ssh";

  // Lista de OLTs {"Nome", "IP"}
  public static final String[][] OLT_LIST = {
          {"OLT_SP_CENTRO_1", "192.168.1.10"},
          {"OLT_SP_CENTRO_2", "192.168.1.11"},
          {"OLT_SP_NORTE_1", "192.168.1.20"},
          {"OLT_SP_SUL_1", "192.168.1.30"}
          // ... Adicione quantas OLTs quiser
  };
}
```

📁 **Localização:** `src/models/Secrets.java`

Após isso, recompile o código `.java` usando uma IDE. Pode compilar um `.exe` com Launch4j ou use `-cp` via bash ou bat.

---

### 📚 Estrutura do Projeto Final

```
NM-OLT-App/
├── lib/                                   # Bibliotecas JAR
├── resources/                             # Temas CSS, Ícones e Fontes
├── src/                                   # Pasta Principal
│   └── Main.java
│   └── SSHManager.java              
│   └── StageResizer.java
│   └── HuaweiOltCommands.java
│   ├── database/                          # Database
│   │   └── SecretsDB.java                 # (não versionado)      
│   │   └── DatabaseManager.java   
│   │   └── LoginResultStatus.java
│   ├── models/                            # Funcionalidades
│   │   └── Secrets.java                   # (não versionado)   
│   │   └── Ticket.java
│   │   └── Usuario.java
│   │   └── OLT.java
│   │   └── OLTList.java
│   ├── screens/                           # Telas de Login e Alterar Senha
│   │   └── ButtonEffects.java
│   │   └── ChangePasswordScreen.java
│   │   └── LoginScreen.java
│   └── utils/                             # Manager de cada Switch Case e JSON
│       └── ConfigManager.java
│       └── ThemeManager.java
│       └── WindowsUtils.java
└── OLTApp.exe
└── OLTApp.jar
└── unins000.exe
```

---

## 📞 Suporte

**Eduardo Tomaz** - Desenvolvedor Principal
- Instagram: [@tomazdudux](https://www.instagram.com/tomazdudux/)
- LinkedIn: [eduardotoomazs](https://www.linkedin.com/in/eduardotoomazs/)
- Email: [eduardotoomaz@Outlook.com](mailto:eduardotoomaz@outlook.com)

---


## 📜 Licença & Agradecimentos

Em especial, agradeço toda a equipe de Suporte, porém reconheço e agradeço àqueles que acompanharam o sistema desde o início, identificando bugs e sugerindo melhorias contínuas:
<br>
***César Bragança, Gabriel Marques, Gabriel Rosa, João Miyake e Kaiky Leandro.***
<br><br>
Este projeto é de propriedade exclusiva da **N-Multifibra** e foi desenvolvido por **Eduardo Tomaz** para uso interno da empresa.
<br>

**Todos os direitos reservados © 2025 N-Multifibra**

---
