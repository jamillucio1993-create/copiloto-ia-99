# Evolution Recorder v1.2 — implementação inicial

## Entregue no código
- Banco SQLite local separado (`evolution_recorder.db`) com eventos compactos e agregados diários.
- Limite de 3.000 eventos detalhados e retenção de 30 dias; agregados por 365 dias.
- Captura de início/fim de sessão, novas ofertas, encerramento e falhas do OCR.
- Registro de análises manuais; diagnóstico JSON local sem imagens, rotas ou identificadores de passageiros.
- Botões na interface para visualizar e compartilhar o relatório por ação explícita do usuário.
- Escrita de eventos em executor de thread única, evitando bloquear a interface.

## Não entregue
- Não há servidor nem API OpenAI configurados; nenhum relatório é enviado automaticamente.
- Não há compilação APK validada, nem teste no Galaxy A54.
- O motivo de encerramento não é inferido: `offer_ended` não significa necessariamente expiração.
- Não há limite físico rígido de 50 MB: o limite implementado é por registros e retenção. O banco de ofertas original continua independente.
- O sistema de melhoria autônoma ainda não foi ativado.

## Próximas validações
1. Abrir projeto no Android Studio com Android SDK 35 e JDK 17.
2. Compilar e testar em A54, confirmar permissões e OCR na interface real.
3. Testar migração, retenção, falhas de captura, carga térmica e uso de disco.
4. Implementar serviço autenticado com consentimento separado, envio criptografado, assinatura de políticas e rollback automático.

**Privacidade:** o compartilhamento manual do JSON deve ser revisado pelo usuário antes de enviar; a base detalhada permanece no telefone.
