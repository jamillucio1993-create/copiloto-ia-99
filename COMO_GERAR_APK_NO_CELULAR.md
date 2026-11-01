# Como gerar o APK usando somente o Galaxy A54

1. Crie uma conta em https://github.com e entre no navegador do celular.
2. Crie um repositório **privado** e vazio, por exemplo `copiloto-a54`.
3. Extraia o ZIP deste pacote. **Envie o conteúdo da pasta `CopilotoIA99_A54_v1_2_Projeto` para a raiz do repositório**, inclusive a pasta oculta `.github` (não envie apenas o ZIP). Pelo navegador móvel, pode ser mais fácil usar o modo "site para computador". A interface web do GitHub pode dificultar o envio de pastas; alternativamente, use um editor Git no Android que preserve diretórios ocultos.
4. Abra a aba **Actions**, selecione **Compilar APK do Copiloto**, toque em **Run workflow**. O push inicial para `main` também pode iniciar o build.
5. Aguarde o resultado. Se o build ficar verde, abra a execução e baixe o artefato `CopilotoIA99-A54-v1.2-debug`.
6. Extraia o arquivo do artefato e instale `app-debug.apk` no A54, autorizando instalação por essa fonte se solicitado.
7. Se o build falhar, abra o passo vermelho e copie o log. **Este projeto ainda não foi compilado com sucesso aqui e pode exigir correções.**

## Cuidados
- O APK é de **teste**, não de produção. Não use recomendações em trânsito antes de validação.
- A captura da tela exige autorização por sessão e pode ter limitações impostas pelo Android e pelo aplicativo capturado.
- Não publique tokens ou chaves da API da OpenAI no repositório nem no aplicativo.
- Este workflow não cria atualizações automáticas e não envia dados à OpenAI.
