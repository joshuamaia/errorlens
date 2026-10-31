# Certificados de CA adicionais (opcional)

Em redes corporativas ou com antivírus que inspecionam TLS (proxy MITM), os downloads feitos
**dentro dos containers durante o build** (Maven Central, npm) podem falhar com
`certificate verify failed` / `PKIX path building failed`.

Nesses casos, copie o certificado raiz do proxy/antivírus (formato PEM) para esta pasta com
extensão `.crt`, por exemplo:

```bash
cp /caminho/para/proxy-root-ca.pem infrastructure/certs/proxy-ca.crt
docker compose build --no-cache
```

Os Dockerfiles importam automaticamente todo `*.crt` desta pasta no repositório de certificados
do sistema operacional, no truststore da JVM e em `NODE_EXTRA_CA_CERTS`, **apenas nos estágios
de build**. As imagens finais não recebem esses certificados.

Os arquivos `*.crt` desta pasta são ignorados pelo Git.
