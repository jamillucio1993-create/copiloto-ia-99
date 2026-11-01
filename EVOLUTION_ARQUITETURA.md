# Copiloto Evolution v1.1 — arquitetura e integração

## Estado desta entrega
Código-fonte Android v1 acrescido de um módulo local EvolutionEngine, consentimento, prévia de relatório anonimizado, validação de faixas de parâmetros, versionamento e rollback manual. **Não há sincronização real nem chamadas à API nesta entrega.** Nenhum APK foi compilado/testado em A54.

## Caminho para ativar a API com segurança
1. Criar backend HTTPS com autenticação por dispositivo e chave da OpenAI guardada **somente no servidor**.
2. Adicionar WorkManager com envio opt-in de agregados, sob Wi-Fi/bateria adequada; nunca enviar telas, endereços, nomes ou dados de passageiros.
3. Backend envia agregados à API OpenAI e solicita proposta de parâmetros em JSON estritamente validado.
4. Servidor executa testes com dados históricos e assina pacote de política usando Ed25519; Android verifica assinatura contra chave pública embutida no APK antes de chamar `applyVerified`.
5. Implementar monitor de regressão e rollback automático. Preservar políticas de motorista e limites mínimos fora da configuração remota.
6. Código Kotlin, permissões e modelos executáveis somente via atualização normal de APK, jamais por JSON remoto.

## Contrato sugerido (JSON)
```
{
  "version": 12,
  "timeMultiplier": 1.12,
  "waitWeight": 0.10,
  "scanIntervalMs": 400,
  "expiresAtEpochMs": 1800000000000
}
```
Esses valores são ilustrativos e não representam aprendizado real.

## Restrições obrigatórias
- Sem autoaceite ou recusa de corridas.
- Não baixar/executar código remoto.
- Não modificar limites de rentabilidade do motorista.
- Não aplicar pacote sem assinatura válida e teste de regressão.
- Consentimento explícito, revogável e granular.
- Ao perder internet, análise financeira e histórico continuam locais.
- Respeitar termos da plataforma 99, privacidade e permissões Android.

## Integração de runtime ainda necessária
`EvolutionEngine` atualmente é isolado: a calibração ainda não altera o OCR nem o `OfferEvaluator`. Conectar apenas depois de testes, mantendo o cálculo financeiro determinístico e limites do usuário intactos.
