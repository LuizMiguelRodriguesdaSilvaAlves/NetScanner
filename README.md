# NetScanner

**Network Reconnaissance & Analysis Tool** — ferramenta educacional para descoberta e diagnóstico de dispositivos e serviços em redes próprias ou com autorização explícita.

> **Versão 0.1.0:** fundação do projeto e integração Java/Python. Esta versão valida a comunicação local entre os módulos; descoberta de hosts e análise de portas serão implementadas nas próximas etapas.

## Objetivo desta versão

- Separar a aplicação Java do motor Python.
- Definir um protocolo JSON versionado.
- Fazer o Java iniciar o motor Python localmente.
- Correlacionar requisições e respostas por `request_id`.
- Tratar falhas de inicialização, respostas inválidas e timeout.
- Automatizar testes no GitHub Actions.

## Tecnologias

| Componente | Tecnologia |
|---|---|
| Aplicação/orquestração | Java 17 |
| Build Java | Maven |
| JSON | Jackson |
| Motor | Python 3.11+ |
| Testes Java | JUnit 5 |
| Testes Python | Pytest |

## Estrutura

```text
NetScanner/
├── .github/workflows/ci.yml
├── docs/PROTOCOL.md
├── java-app/
│   ├── pom.xml
│   └── src/main/java/com/netscanner/
│       ├── app/
│       ├── config/
│       ├── engine/
│       └── model/
├── python-engine/
│   ├── pyproject.toml
│   ├── src/netscanner/
│   └── tests/
├── scripts/
└── README.md
```

## Pré-requisitos

- JDK 17 ou superior.
- Maven 3.9 ou superior.
- Python 3.11 ou superior.
- Pytest para os testes Python.

## Executar e testar

### Motor Python

Linux/macOS:

```bash
cd python-engine
python3 -m pip install -e ".[dev]"
PYTHONPATH=src python3 -m pytest
printf '%s\n' '{"protocol_version":"1.0","request_id":"manual-001","action":"system.ping","payload":{}}' | PYTHONPATH=src python3 -m netscanner
```

Windows PowerShell:

```powershell
cd python-engine
py -m pip install -e ".[dev]"
$env:PYTHONPATH = "src"
py -m pytest
'{"protocol_version":"1.0","request_id":"manual-001","action":"system.ping","payload":{}}' | py -m netscanner
```

### Aplicação Java integrada

Execute a partir da raiz:

Linux/macOS:

```bash
cd java-app
NETSCANNER_PYTHON=python3 mvn clean verify
NETSCANNER_PYTHON=python3 mvn exec:java
```

Windows PowerShell:

```powershell
cd java-app
$env:NETSCANNER_PYTHON = "py"
$env:NETSCANNER_PYTHON_ENGINE = "../python-engine"
mvn clean verify
mvn exec:java
```

A execução bem-sucedida imprime uma resposta JSON com `success: true`, `status: ready` e a mensagem `NetScanner Java/Python integration: OK`.

### Configuração

| Variável | Padrão | Descrição |
|---|---|---|
| `NETSCANNER_PYTHON` | `python` | Executável Python chamado pelo Java. |
| `NETSCANNER_PYTHON_ENGINE` | descoberta automática | Diretório do motor Python. |
| `NETSCANNER_TIMEOUT_SECONDS` | `10` | Timeout (1 a 300 segundos). |

Consulte [docs/PROTOCOL.md](docs/PROTOCOL.md) para o contrato JSON completo.

## Segurança e limites

- Utilize a ferramenta somente em redes próprias ou com autorização explícita.
- O Java inicia o processo com argumentos separados, sem interpolação em shell.
- O motor reserva `stdout` exclusivamente para a resposta JSON; mensagens de diagnóstico usam `stderr`.
- O Java valida versão do protocolo e `request_id`, além de impor timeout.
- As futuras funcionalidades de rede precisarão validar alvos e limitar concorrência, escopo e duração.

## Roadmap

- [x] Estrutura inicial Java/Python e protocolo JSON 1.0.
- [x] Integração de processo com a ação `system.ping`.
- [x] Tratamento inicial de erros e timeout.
- [x] Testes e pipeline de CI.
- [ ] Descoberta de hosts em redes autorizadas.
- [ ] Scanner de portas TCP.
- [ ] Detecção básica de serviços.
- [ ] Interface JavaFX, histórico e relatórios.

**Licença:** ainda não definida.
