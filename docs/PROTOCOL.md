# Protocolo Java ↔ Python

O NetScanner utiliza JSON sobre entrada e saída padrão (`stdin`/`stdout`) para manter a integração simples, local e independente de servidor.

## Requisição

```json
{
  "protocol_version": "1.0",
  "request_id": "uuid-gerado-pelo-java",
  "action": "system.ping",
  "payload": {}
}
```

## Resposta de sucesso

```json
{
  "protocol_version": "1.0",
  "request_id": "uuid-gerado-pelo-java",
  "success": true,
  "data": {
    "status": "ready",
    "engine": "NetScanner Python Engine",
    "engine_version": "0.1.0",
    "python_version": "3.11.0",
    "capabilities": ["system.ping"]
  },
  "error": null
}
```

## Resposta de erro

```json
{
  "protocol_version": "1.0",
  "request_id": "uuid-gerado-pelo-java",
  "success": false,
  "data": null,
  "error": {
    "code": "UNKNOWN_ACTION",
    "message": "Unsupported action."
  }
}
```

## Regras

- Uma execução do processo Python atende a uma requisição e encerra.
- O Java envia exatamente um documento JSON por `stdin`.
- O Python escreve exatamente uma resposta JSON em `stdout`.
- Mensagens de diagnóstico devem usar `stderr`; `stdout` é reservado ao protocolo.
- `request_id` deve ser devolvido sem alterações para correlacionar requisição e resposta.
- O protocolo inicial é `1.0`; mudanças incompatíveis devem incrementar a versão principal.
- O tamanho máximo da requisição é 1 MiB.
- O Java aplica timeout configurável e encerra o processo quando excedido.

## Ação implementada nesta versão

| Ação | Finalidade |
|---|---|
| `system.ping` | Verificar se o motor Python iniciou e responder com metadados de runtime. |

As futuras ações de descoberta de hosts e varredura TCP serão adicionadas ao mesmo envelope, sem misturar logs com os dados de resposta.
