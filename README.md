# NetScanner

**Network Reconnaissance & Analysis Tool** — ferramenta educacional para descoberta e diagnóstico de dispositivos em redes próprias ou com autorização explícita.

> **Versão 0.2.0:** adiciona descoberta de hosts IPv4 por sondagem TCP, mantendo a integração Java/Python e o protocolo JSON 1.0 da versão anterior.

## O que entrou nesta versão

- Ação **network.host_discovery** integrada ao motor Python e invocável pela aplicação Java.
- Validação de CIDR IPv4 e restrição a redes privadas RFC 1918 e loopback.
- Limite de 1.024 endereços utilizáveis por execução.
- Concorrência configurável, limitada a 128 workers.
- Sondagem TCP em portas comuns e tentativa de resolução reversa de hostname.
- Resultados JSON estruturados e testes da lógica Python e da integração Java/Python.

## Tecnologias

| Componente | Tecnologia |
|---|---|
| Aplicação/orquestração | Java 17 |
| Build Java | Maven |
| JSON | Jackson |
| Motor | Python 3.11+ (biblioteca padrão) |
| Testes Java | JUnit 5 |
| Testes Python | Pytest |

## Pré-requisitos

- JDK 17 ou superior.
- Maven 3.9 ou superior.
- Python 3.11 ou superior.
- Pytest para testes locais do motor.

## Instalar e testar

Motor Python — Linux/macOS:

    cd python-engine
    python3 -m pip install -e ".[dev]"
    PYTHONPATH=src python3 -m pytest

Motor Python — Windows PowerShell:

    cd python-engine
    py -m pip install -e ".[dev]"
    $env:PYTHONPATH = "src"
    py -m pytest

Aplicação Java integrada — execute na raiz do repositório:

Linux/macOS:

    cd java-app
    NETSCANNER_PYTHON=python3 mvn clean verify

Windows PowerShell:

    cd java-app
    $env:NETSCANNER_PYTHON = "py"
    $env:NETSCANNER_PYTHON_ENGINE = "../python-engine"
    mvn clean verify

Sem argumentos, a aplicação mantém o teste de integração da versão anterior e imprime a confirmação de que Java e Python se comunicaram.

## Executar Host Discovery

Na pasta java-app, execute o comando abaixo, substituindo o exemplo pelo CIDR da sua rede autorizada.

Linux/macOS:

    NETSCANNER_PYTHON=python3 mvn exec:java -Dexec.args="--discover 192.168.1.0/24"

Windows PowerShell:

    mvn exec:java "-Dexec.args=--discover 192.168.1.0/24"

A saída inclui o alvo, a quantidade de endereços consultados, a quantidade de hosts identificados, a duração e a lista de resultados. Cada host pode conter IP, hostname, portas abertas encontradas, latência e método de descoberta.

## Parâmetros

A ação network.host_discovery recebe os campos no payload JSON:

| Campo | Padrão | Limites | Descrição |
|---|---:|---:|---|
| cidr | — | obrigatório | Rede IPv4 em notação CIDR. |
| timeout_ms | 500 | 100–3.000 | Timeout de cada conexão TCP, em milissegundos. |
| max_workers | 64 | 1–128 | Máximo de hosts processados em paralelo. |

Configurações Java:

| Variável | Padrão | Descrição |
|---|---|---|
| NETSCANNER_PYTHON | python | Executável Python chamado pelo Java. |
| NETSCANNER_PYTHON_ENGINE | descoberta automática | Diretório do motor Python. |
| NETSCANNER_TIMEOUT_SECONDS | 30 | Tempo máximo do processo Python (1 a 300 segundos). Aumente para redes maiores ou lentas. |

Consulte docs/PROTOCOL.md para o contrato de comunicação.

## Segurança e limitações

- Utilize a ferramenta somente em redes próprias ou com autorização explícita.
- Nesta versão, somente redes IPv4 RFC 1918 (10/8, 172.16/12 e 192.168/16) e loopback (127/8) são aceitas. IPv6 e redes públicas são rejeitados.
- Cada execução pode incluir no máximo 1.024 endereços utilizáveis.
- A descoberta usa conexões TCP nas portas 22, 53, 80, 443, 445 e 3389. Não envia ICMP e não é uma varredura completa de portas.
- Hosts que descartam todas essas sondagens podem não ser detectados. Portanto, ausência de resultado não prova que o dispositivo esteja desligado.
- open_ports representa somente as portas do conjunto de descoberta.
- O hostname depende da resolução reversa DNS configurada no sistema e pode ser nulo.
- O Java inicia o motor com argumentos separados; stdout permanece reservado ao JSON e mensagens de diagnóstico usam stderr.

## Roadmap

- [x] Estrutura Java/Python e protocolo JSON 1.0.
- [x] Integração com a ação system.ping.
- [x] Tratamento inicial de erros e timeout.
- [x] Testes e pipeline de CI.
- [x] Descoberta limitada de hosts IPv4 em redes privadas autorizadas.
- [ ] Scanner de portas TCP dedicado.
- [ ] Detecção básica de serviços.
- [ ] Persistência local com SQLite (histórico, perfis e resultados).
- [ ] Interface JavaFX, relatórios e comparação de escaneamentos.

**Licença:** ainda não definida.
