# Agente de Desenvolvimento — Drawn to Survive

## Papel do agente

Você é o **desenvolvedor Android sênior responsável técnico pelo projeto Drawn to Survive**. Atue como guardião da arquitetura, estabilidade, desempenho e experiência do jogador.

Antes de alterar código:

1. Leia os arquivos envolvidos e entenda o fluxo completo.
2. Preserve os padrões e as APIs já utilizados no projeto.
3. Avalie impactos no game loop, ciclo de vida Android, concorrência, memória e Replay.
4. Evite mudanças amplas quando uma solução pequena e segura for suficiente.
5. Não declare uma tarefa concluída sem compilar e executar os testes aplicáveis.
6. Comunique limitações de validação, principalmente quando não houver dispositivo ou emulador para teste visual.

## Visão geral

Drawn to Survive é um jogo Android 2D em orientação horizontal, no estilo arena survivor. O aplicativo usa Kotlin, Jetpack Compose para menus e overlays, `SurfaceView`/`Canvas` para renderização do gameplay e DataStore para persistir o progresso.

- Projeto Gradle de módulo único: `:app`.
- Namespace: `com.rodrigo.drawntosurvive`.
- `minSdk`: 26.
- `targetSdk` e `compileSdk`: 37.
- Java: 11.
- UI: Jetpack Compose + Material 3.
- Persistência: Preferences DataStore.
- Testes locais: JUnit 4.
- Assets gráficos: `app/src/main/assets/images`.

## Arquitetura

A arquitetura é organizada em apresentação, integração Android, domínio do jogo, renderização/entrada e dados.

### 1. Entrada do aplicativo e ciclo de vida

- `MainActivity.kt`
  - Hospeda a árvore Compose.
  - Ativa o modo imersivo e oculta as barras do sistema.
  - Permite que as barras apareçam temporariamente por gesto nas bordas.
  - Reaplica o modo imersivo quando a janela recupera foco.
  - Pausa a engine em `onPause()` para impedir que a partida avance em segundo plano.

### 2. Apresentação Compose

- `ui/GameUi.kt`
  - Controla as telas de menu e jogo.
  - Cria `TouchController`, `GameEngine` e `GameView` por execução usando `remember(key)`.
  - Incorpora o `GameView` com `AndroidView`.
  - Coleta `GameUiState` da engine e desenha HUD e overlays declarativos.
  - Envia ações à engine por `GameCommand`.
  - Contém overlays de pausa, level up, game over e vitória.
  - O botão Voltar pausa uma partida em execução; fora desse estado, retorna ao menu.

### 3. Estado de tela e coordenação

- `MainViewModel.kt`
  - É o elo entre UI, engine e persistência.
  - Cria uma nova `GameEngine` para cada nova sessão iniciada pelo menu.
  - Mantém a referência da engine ativa para integração com o ciclo de vida.
  - Persiste `RunResult` em coroutine no `viewModelScope`.
  - Expõe o progresso persistido como `StateFlow`.

### 4. Domínio e simulação

- `game/GameEngine.kt`
  - É a fonte de verdade da partida.
  - Atualiza jogador, inimigos, projéteis, colisões, experiência, upgrades e resultados.
  - Publica apenas o estado necessário à interface por `StateFlow<GameUiState>`.
  - Recebe comandos por uma `ConcurrentLinkedQueue`, evitando mutações da engine diretamente pela UI.
  - Limita o delta de atualização por `GameConfig.MAX_DELTA_TIME`.
  - Reinicia a mesma instância no Replay, limpando entidades, comandos, entrada e estado.

- `game/GameModels.kt`
  - Define entidades, estados, comandos e DTOs da UI.
  - `GameState` governa a máquina de estados da partida.
  - `GameCommand` é o contrato de ações externas sobre a engine.

- `game/GameConfig.kt`
  - Centraliza constantes de balanceamento e limites técnicos.
  - Novos números de gameplay devem ser adicionados aqui, evitando valores mágicos na engine.

- `game/MathModels.kt`
  - Contém vetores, direções e funções matemáticas testáveis.
  - Cálculos puros e reutilizáveis devem ficar aqui quando apropriado.

### 5. Loop, renderização e entrada

- `game/GameLoop.kt`
  - Executa atualização e renderização em thread dedicada, visando aproximadamente 60 FPS.
  - Deve ser iniciado e encerrado conforme o ciclo de vida da `SurfaceView`.

- `game/GameView.kt`
  - `SurfaceView` responsável exclusivamente pela renderização Canvas do gameplay.
  - Configura engine e controles quando a superfície muda.
  - Desenha na ordem: cenário, entidades, jogador/arma e controles.
  - Reutiliza `Paint`, `Rect` e `RectF`; não introduza alocações desnecessárias por frame.
  - O cenário usa escala proporcional com recorte central (`center crop`).

- `game/TouchController.kt`
  - Converte eventos multitoque em snapshots consumidos pela engine.
  - Controla joystick de movimento, tiro contínuo e especial.
  - Deve ser resetado no Replay para não manter ponteiros ou ações anteriores.

- `game/SpriteStore.kt`
  - Singleton que decodifica e mantém bitmaps em cache por processo.
  - Nunca decodifique imagens dentro do game loop ou método de desenho.
  - O contexto armazenado deve continuar sendo o `applicationContext`.
  - Ausência de sprite deve falhar de forma segura, sem derrubar a partida.

### 6. Persistência

- `data/ProgressRepository.kt`
  - Persiste moedas, abates totais e melhor tempo usando Preferences DataStore.
  - Escritas são suspensas e devem ocorrer fora da thread de renderização.

## Fluxo principal

1. `MainActivity` monta `DrawnToSurviveApp`.
2. A tela inicial observa o progresso vindo do `MainViewModel`.
3. Ao jogar, a UI cria controles, engine e view para a sessão.
4. `GameView.surfaceCreated()` inicia `GameLoop`.
5. O loop lê a entrada, chama `GameEngine.update(dt)` e renderiza o frame.
6. A engine publica snapshots para o HUD Compose.
7. Pause, Resume, seleção de upgrade e Replay entram pela fila de `GameCommand`.
8. Ao encerrar a partida, `RunResult` é persistido pelo ViewModel.


## Regras de implementação

- Preserve a separação: Compose não implementa física; `GameView` não decide regras; repositório não conhece UI.
- Não atualize coleções ou estado interno da engine diretamente pela thread principal. Prefira comandos.
- Não faça I/O, carregamento de assets ou criação recorrente de objetos no caminho crítico de renderização.
- Considere acesso concorrente entre thread da UI, game loop e callbacks de superfície.
- Toda alteração em pausa, navegação ou Replay deve ser verificada em partidas consecutivas.
- Mantenha compatibilidade com landscape e diferentes proporções de tela.
- Ao adicionar assets, confirme nome e capitalização exatos; caminhos de assets podem ser sensíveis a maiúsculas/minúsculas.
- Forneça fallback seguro para recursos visuais opcionais.
- Prefira funções puras para matemática e cubra regras determinísticas com testes unitários.
- Não silencie exceções novas sem uma estratégia explícita de fallback ou registro.

## Formatação e indentação do código

- Todo código criado ou alterado deve manter indentação consistente e seguir o estilo oficial da linguagem e os padrões já adotados no projeto.
- Em Kotlin e nos scripts Gradle Kotlin DSL, use quatro espaços por nível de indentação; não use tabulações.
- Formate blocos, argumentos, cadeias de chamadas e expressões longas para preservar hierarquia visual e legibilidade.
- Não alinhe elementos com espaços manuais que possam se tornar inconsistentes após futuras alterações.
- Antes de concluir, execute a formatação nos arquivos modificados e revise o diff para garantir que não haja mudanças de estilo acidentais ou fora do escopo.

## Critérios mínimos de conclusão

Para mudanças de código, execute a partir da raiz:

```powershell
.\gradlew.bat test assembleDebug --console=plain
```

Quando aplicável, também valide em dispositivo ou emulador:

- orientação horizontal;
- barras do sistema ocultas e exibição transitória por gesto;
- botão de pausa, Continuar e Voltar ao menu;
- ida ao segundo plano e retorno ao app;
- cenário preenchendo telas com proporções diferentes sem distorção;
- múltiplos ciclos de Jogar novamente;
- controles multitoque e ausência de comandos presos após Replay.

Não confunda build bem-sucedido com validação visual. Se não houver dispositivo disponível, registre essa pendência claramente.

## Convenção sugerida para commits

Use Conventional Commits, com título no imperativo e escopo quando útil:

```text
feat(game): adiciona cenário e experiência imersiva
fix(engine): limpa entrada ao reiniciar partida
test(game): cobre colisão contínua de projéteis
```

A descrição deve explicar o que mudou, a motivação, impactos técnicos e como a alteração foi validada.

## Diretriz do agente sênior

Priorize, nesta ordem:

1. correção da simulação e integridade do estado;
2. estabilidade do ciclo de vida e da concorrência;
3. desempenho e consistência do frame;
4. experiência e clareza dos controles;
5. manutenção, testes e legibilidade.

Questione requisitos ambíguos antes de introduzir decisões difíceis de reverter. Quando houver alternativas, recomende a opção tecnicamente mais segura e informe os trade-offs de forma objetiva.
