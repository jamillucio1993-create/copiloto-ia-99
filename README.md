# Copiloto IA 99 — Galaxy A54 — Protótipo Android v1.0

**ENTREGA: PROJETO-FONTE, NÃO APK. Não compilado nem testado em aparelho físico neste ambiente.**

## Funcionalidades no código
- Captura de tela **com consentimento explícito** via Android MediaProjection, em serviço foreground.
- OCR **no aparelho** com ML Kit (dependência empacotada no APK após download pelo Gradle).
- Parser experimental de ofertas com preço, embarque e viagem; **não garante leitura correta da interface da 99**.
- Janela flutuante opcional (permissão separada) sem interceptar toques.
- Motor financeiro local, classificação, limites de valor, R$/km, R$/hora e embarque.
- Nota do passageiro **não extraída automaticamente**: a análise capturada NÃO aplica o limite de nota; não considere aprovação completa.
- Histórico SQLite privado e modelo estatístico adaptativo de espera por região, com formulário manual para dados reais.
- Funciona sem rede para cálculos, OCR após instalação e histórico; não faz requisições a servidores.
- Botão de parada da captura, aviso persistente de captura em primeiro plano.
- Nenhuma função de aceitar ou recusar corridas automaticamente.

## Compilar e instalar
1. Instalar Android Studio, Android SDK 35, JDK 17 e Gradle 8.9 (não há wrapper incluído).
2. Abrir esta pasta como projeto Android e sincronizar o Gradle com acesso à internet para baixar dependências **na compilação**.
3. Executar `gradle testDebugUnitTest` e `gradle assembleDebug`.
4. Instalar `app/build/outputs/apk/debug/app-debug.apk` no Galaxy A54 após compilação bem-sucedida.
5. Abrir o Copiloto; permitir sobreposição se desejar; tocar em Iniciar captura e aprovar o diálogo do Android.
6. Usar com o veículo parado; testar com ofertas simuladas e conferir os dados extraídos antes de confiar em qualquer recomendação.

## Limitações importantes
- **Sem compilação validada**: erros de build ou de runtime ainda podem existir.
- OCR heurístico: formatos de cartões diferentes, texto fora de ordem e múltiplas ofertas podem gerar leituras erradas. Não recomendado para decisões reais antes de validação.
- MediaProjection exige consentimento por sessão, pode ser interrompido e pode não capturar conteúdo protegido.
- Captura de tela de terceiros deve respeitar permissões, privacidade, regras da 99 e políticas de distribuição.
- Sem GPS, dados externos de trânsito, demanda em tempo real, modelo neural avançado, backup exportável ou criptografia específica do banco.
- A captura automática pode detectar texto que não pertence à oferta; não há garantia de expiração correta, pois o detector atual depende de ausência de dados reconhecíveis.
- SQLite fica no armazenamento privado do aplicativo e o Android oferece criptografia do aparelho, mas **o banco não tem criptografia própria**.
- **Não afiliado à 99.**

## Cenário de referência
Oferta fictícia reproduzindo os números vistos na gravação: R$ 11,98, embarque 841 m / 4 min, viagem 2,3 km / 7 min. O teste de parser deve extrair os valores e o motor financeiro calcular ~R$3,81/km e ~R$65,35/h. Não equivale a prova de captura no aparelho.


## Evolution Recorder v1.2
Consulte `EVOLUTION_RECORDER_V12.md`. Interface inclui diagnóstico real local e compartilhamento manual. Não há integração de API ou APK validado.
