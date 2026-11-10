# Protocolo Java ↔ Python

O NetScanner utiliza JSON sobre entrada e saída padrão (stdin/stdout). Uma execução do processo Python atende a uma requisição, devolve uma resposta e encerra.

## Envelope

    {
      "protocol_version": "1.0",
      "request_id": "uuid-gerado-pelo-java",
      "action": "system.ping",
      "payload": {}
    }

## Ações disponíveis

### system.ping

Verifica se o processo Python iniciou corretamente. Não exige parâmetros no payload. A resposta inclui versão do motor, versão do Python e a lista de capacidades disponíveis.

### network.host_discovery

Executa descoberta TCP limitada em uma rede IPv4 CIDR privada ou loopback.

Requisição:

    {
      "protocol_version": "1.0",
      "request_id": "discovery-001",
      "action": "network.host_discovery",
      "payload": {
        "cidr": "192.168.1.0/24",
        "timeout_ms": 500,
        "max_workers": 64
      }
    }

`cidr` é obrigatório. `timeout_ms` é opcional, com padrão 500 ms e faixa de 100 a 3.000 ms. `max_workers` é opcional, com padrão 64 e faixa de 1 a 128.

Resposta de exemplo:

    {
      "protocol_version": "1.0",
      "request_id": "discovery-001",
      "success": true,
      "data": {
        "target": "192.168.1.0/24",
        "scanned_hosts": 254,
        "active_hosts": 1,
        "duration_ms": 812.4,
        "scanned_at": "2026-11-09T12:00:00+00:00",
        "method": "tcp_connect",
        "hosts": [
          {
            "ip": "192.168.1.10",
            "hostname": "workstation.local",
            "open_ports": [445],
            "latency_ms": 4.21,
            "discovery_method": "tcp_connect"
          }
        ]
      },
      "error": null
    }

`hostname` pode ser nulo quando a resolução reversa não encontra um nome. `open_ports` lista somente portas do conjunto usado para descoberta: 22, 53, 80, 443, 445 e 3389. Não representa uma varredura completa.

### network.port_scan

Executa uma varredura TCP connect em um único endereço IPv4 privado RFC 1918 ou loopback. Não aceita CIDR, endereços públicos nem IPv6.

Requisição:

    {
      "protocol_version": "1.0",
      "request_id": "port-scan-001",
      "action": "network.port_scan",
      "payload": {
        "target": "192.168.1.10",
        "ports": "22,80,443,8000-8100",
        "timeout_ms": 500,
        "max_workers": 64
      }
    }

`target` e `ports` são obrigatórios. `ports` é uma string com portas separadas por vírgula e/ou intervalos inclusivos; cada porta deve estar entre 1 e 65.535. Itens duplicados são removidos e o conjunto final pode conter no máximo 1.024 portas únicas. `timeout_ms` é opcional (padrão 500; faixa 100–3.000) e `max_workers` é opcional (padrão 64; faixa 1–128).

Resposta de exemplo:

    {
      "protocol_version": "1.0",
      "request_id": "port-scan-001",
      "success": true,
      "data": {
        "target": "192.168.1.10",
        "total_ports": 4,
        "scanned_ports": 4,
        "open_count": 2,
        "closed_count": 1,
        "filtered_count": 1,
        "open_ports": [22, 443],
        "duration_ms": 15.4,
        "scanned_at": "2026-11-10T11:00:00+00:00",
        "method": "tcp_connect",
        "timeout_ms": 500,
        "max_workers": 4,
        "results": [
          {"port": 22, "state": "open", "latency_ms": 2.1},
          {"port": 80, "state": "closed", "latency_ms": 1.4},
          {"port": 443, "state": "open", "latency_ms": 3.2},
          {"port": 8080, "state": "filtered", "latency_ms": 500.5}
        ]
      },
      "error": null
    }

Estados:

- `open`: a conexão TCP foi estabelecida.
- `closed`: o destino recusou explicitamente a conexão.
- `filtered`: houve timeout ou outro erro sem recusa explícita; o resultado é inconclusivo e não prova a existência de firewall.

A implementação não envia dados de aplicação, não identifica serviços e não executa sondagens UDP.

## Erros de descoberta

A resposta de erro mantém o mesmo envelope, com `success: false`, `data: null` e um objeto `error`.

| Código | Significado |
|---|---|
| INVALID_TARGET | CIDR ausente, inválido ou sem endereços utilizáveis. |
| TARGET_OUT_OF_SCOPE | Rede fora dos intervalos privados/loopback permitidos. |
| NETWORK_TOO_LARGE | Mais de 1.024 endereços utilizáveis. |
| INVALID_DISCOVERY_OPTIONS | Opção desconhecida ou fora dos limites. |

## Erros de varredura TCP

| Código | Significado |
|---|---|
| INVALID_TARGET | Endereço ausente, inválido, IPv6 ou formato diferente de um IPv4 único. |
| TARGET_OUT_OF_SCOPE | Endereço fora dos intervalos privados/loopback ou endereço IPv4 especial não permitido. |
| INVALID_PORT_SPEC | Lista/range de portas inválido. |
| TOO_MANY_PORTS | Mais de 1.024 portas únicas na solicitação. |
| INVALID_PORT_SCAN_OPTIONS | Opção desconhecida ou timeout/concorrência fora dos limites. |

## Regras gerais

- O Java envia exatamente um documento JSON por stdin.
- O Python escreve exatamente uma resposta JSON em stdout.
- Mensagens de diagnóstico usam stderr; stdout é reservado ao protocolo.
- `request_id` é devolvido sem alterações.
- O protocolo permanece na versão 1.0; as novas ações são compatíveis com o envelope anterior.
- O tamanho máximo da requisição é 1 MiB.
- O Java aplica timeout global ao processo por meio de `NETSCANNER_TIMEOUT_SECONDS` (padrão 30, faixa de 1 a 300 segundos). Esse valor é independente do timeout individual das conexões TCP.
- Host Discovery aceita somente redes IPv4 RFC 1918 e loopback, com até 1.024 endereços utilizáveis.
- Port Scan aceita somente um IPv4 privado RFC 1918 ou loopback e até 1.024 portas TCP por execução.
- Resultados de descoberta e varredura dependem das respostas da rede; ausência de resposta não deve ser interpretada como prova de que um host ou serviço está desligado.
