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

cidr é obrigatório. timeout_ms é opcional, com padrão 500 ms e faixa de 100 a 3.000 ms. max_workers é opcional, com padrão 64 e faixa de 1 a 128.

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

hostname pode ser nulo quando a resolução reversa não encontra um nome. open_ports lista somente portas do conjunto usado para descoberta: 22, 53, 80, 443, 445 e 3389. Não representa uma varredura completa.

## Erros de descoberta

A resposta de erro mantém o mesmo envelope, com success false, data null e um objeto error.

| Código | Significado |
|---|---|
| INVALID_TARGET | CIDR ausente, inválido ou sem endereços utilizáveis. |
| TARGET_OUT_OF_SCOPE | Rede fora dos intervalos privados/loopback permitidos. |
| NETWORK_TOO_LARGE | Mais de 1.024 endereços utilizáveis. |
| INVALID_DISCOVERY_OPTIONS | Opção desconhecida ou fora dos limites. |

## Regras gerais

- O Java envia exatamente um documento JSON por stdin.
- O Python escreve exatamente uma resposta JSON em stdout.
- Mensagens de diagnóstico usam stderr; stdout é reservado ao protocolo.
- request_id é devolvido sem alterações.
- O protocolo permanece na versão 1.0; a nova ação é compatível com o envelope anterior.
- O tamanho máximo da requisição é 1 MiB.
- O Java aplica timeout global ao processo por meio de NETSCANNER_TIMEOUT_SECONDS (padrão 30, faixa de 1 a 300 segundos). Esse valor é independente do timeout individual das conexões TCP.
- A descoberta aceita somente redes IPv4 RFC 1918 e loopback, com até 1.024 endereços utilizáveis.
- A descoberta depende de respostas TCP nas portas de sondagem. Hosts que descartam essas conexões podem não aparecer nos resultados.
