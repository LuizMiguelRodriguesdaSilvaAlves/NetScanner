# NetScanner

**Network Reconnaissance & Analysis Tool** — ferramenta educacional para descoberta e diagnóstico de dispositivos em redes próprias ou com autorização explícita.

> **Versão 0.3.0:** adiciona varredura TCP de portas em um endereço IPv4 privado/autorizado, mantendo a descoberta de hosts da v0.2.0 e a integração Java/Python pelo protocolo JSON 1.0.

## O que entrou nesta versão

- Nova ação **network.port_scan** no motor Python e integração com a aplicação Java.
- Varredura TCP connect em um único endereço IPv4 por execução.
- Seleção de portas por lista e intervalos, por exemplo `22,80,443,8000-8100`.
- Limite de 1.024 portas únicas por execução.
- Concorrência configurável, limitada a 128 workers, e timeout por conexão entre 100 e 3.000 ms.
- Classificação dos resultados em `open`, `closed` e `filtered` (inconclusivo).
- Validação de escopo restrita a redes privadas RFC 1918 e loopback.
- Testes unitários e teste de integração Java/Python com uma porta TCP local.

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

Sem argumentos, a aplicação executa `system.ping` e imprime a confirmação de que Java e Python se comunicaram.

## Executar Host Discovery

Na pasta `java-app`, substitua o exemplo pelo CIDR da sua rede autorizada.

Linux/macOS:

    NETSCANNER_PYTHON=python3 mvn exec:java -Dexec.args="--discover 192.168.1.0/24"

Windows PowerShell:

    mvn exec:java "-Dexec.args=--discover 192.168.1.0/24"

A descoberta consulta as portas TCP 22, 53, 80, 443, 445 e 3389. Ela não é uma varredura completa de portas.

## Executar TCP Port Scan

A varredura de portas recebe **um único endereço IPv4 privado ou loopback**, não um CIDR. Informe explicitamente as portas ou intervalos desejados.

Linux/macOS:

    NETSCANNER_PYTHON=python3 mvn exec:java -Dexec.args="--scan 192.168.1.10 --ports 22,80,443,8000-8100"

Windows PowerShell:

    mvn exec:java "-Dexec.args=--scan 192.168.1.10 --ports 22,80,443,8000-8100"

Opções adicionais:

    --timeout-ms 500
    --max-workers 64

Exemplo completo no PowerShell:

    mvn exec:java "-Dexec.args=--scan 192.168.1.10 --ports 1-1024 --timeout-ms 500 --max-workers 64"

A lista aceita portas separadas por vírgula e intervalos inclusivos. Portas repetidas são removidas automaticamente. Cada execução aceita no máximo 1.024 portas únicas, no intervalo de 1 a 65.535.

O resultado JSON informa o alvo, a quantidade de portas consultadas, contagens por estado, lista de portas abertas, duração e horário da execução. O campo `results` contém o estado e a latência observada para cada porta.

## Parâmetros do motor

### network.host_discovery

| Campo | Padrão | Limites | Descrição |
|---|---:|---:|---|
| cidr | — | obrigatório | Rede IPv4 em notação CIDR. |
| timeout_ms | 500 | 100–3.000 | Timeout de cada conexão TCP, em milissegundos. |
| max_workers | 64 | 1–128 | Máximo de hosts processados em paralelo. |

### network.port_scan

| Campo | Padrão | Limites | Descrição |
|---|---:|---:|---|
| target | — | obrigatório | Um endereço IPv4 privado RFC 1918 ou loopback. |
| ports | — | obrigatório | Lista/ranges separados por vírgula, até 1.024 portas únicas. |
| timeout_ms | 500 | 100–3.000 | Timeout de cada conexão TCP, em milissegundos. |
| max_workers | 64 | 1–128 | Máximo de conexões processadas em paralelo. |

Configurações Java:

| Variável | Padrão | Descrição |
|---|---|---|
| NETSCANNER_PYTHON | python | Executável Python chamado pelo Java. |
| NETSCANNER_PYTHON_ENGINE | descoberta automática | Diretório do motor Python. |
| NETSCANNER_TIMEOUT_SECONDS | 30 | Tempo máximo do processo Python (1 a 300 segundos). Ajuste para varreduras maiores ou conexões lentas. |

Consulte `docs/PROTOCOL.md` para o contrato de comunicação.

## Segurança e limitações

- Utilize a ferramenta somente em redes próprias ou com autorização explícita.
- Tanto Host Discovery quanto Port Scan aceitam somente endereços IPv4 privados RFC 1918 (10/8, 172.16/12 e 192.168/16) e loopback (127/8). IPv6 e endereços públicos são rejeitados.
- Host Discovery aceita até 1.024 endereços utilizáveis por execução.
- Port Scan aceita somente um endereço de destino e até 1.024 portas TCP únicas por execução.
- A varredura usa TCP connect: não envia payloads de aplicação, não faz fingerprint de serviços e não é uma varredura UDP.
- `open` indica conexão TCP estabelecida; `closed` indica recusa explícita; `filtered` representa timeout ou erro sem recusa explícita e é inconclusivo. Um resultado `filtered` não prova que exista um firewall.
- A varredura não garante detectar serviços que mudem de estado, limitem conexões ou bloqueiem sondagens.
- Na descoberta de hosts, `open_ports` representa somente as portas do conjunto de sondagem (22, 53, 80, 443, 445 e 3389).
- O hostname da descoberta depende da resolução reversa DNS configurada no sistema e pode ser nulo.
- O Java inicia o motor com argumentos separados; stdout permanece reservado ao JSON e mensagens de diagnóstico usam stderr.

## Roadmap

- [x] Estrutura Java/Python e protocolo JSON 1.0.
- [x] Integração com a ação system.ping.
- [x] Tratamento inicial de erros e timeout.
- [x] Testes e pipeline de CI.
- [x] Descoberta limitada de hosts IPv4 em redes privadas autorizadas.
- [x] Scanner de portas TCP dedicado para um host IPv4 privado/autorizado.
- [ ] Detecção básica de serviços.
- [ ] Persistência local com SQLite (histórico, perfis e resultados).
- [ ] Interface JavaFX, relatórios e comparação de escaneamentos.

**Licença:** ainda não definida.
