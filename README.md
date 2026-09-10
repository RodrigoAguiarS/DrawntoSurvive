# Drawn to Survive

Jogo 2D de sobrevivência em arena para Android, desenvolvido em Kotlin. O jogador enfrenta hordas progressivamente mais numerosas, coleta experiência, escolhe melhorias e tenta sobreviver durante 10 minutos.

O projeto combina **Jetpack Compose** para menus, HUD e overlays com **SurfaceView/Canvas** para simulação e renderização do gameplay em tempo real.

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)

> Projeto em desenvolvimento. O balanceamento, os recursos visuais e a experiência em diferentes dispositivos ainda podem mudar.

## Sumário

- [Sobre o jogo](#sobre-o-jogo)
- [Funcionalidades](#funcionalidades)
- [Como jogar](#como-jogar)
- [Tecnologias](#tecnologias)
- [Arquitetura](#arquitetura)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Requisitos](#requisitos)
- [Como executar](#como-executar)
- [Build e testes](#build-e-testes)
- [Configuração do gameplay](#configuração-do-gameplay)
- [Persistência](#persistência)
- [Assets](#assets)
- [Como contribuir](#como-contribuir)
- [Licença](#licença)

## Sobre o jogo

**Drawn to Survive** é um arena survivor em orientação horizontal com controles twin-stick adaptados para telas sensíveis ao toque. Durante uma partida, inimigos surgem ao redor da arena e perseguem o jogador. Ao derrotá-los, orbes de experiência são liberados; cada novo nível apresenta três melhorias aleatórias.

A vitória ocorre ao sobreviver por **600 segundos (10 minutos)**. Se os pontos de vida chegarem a zero, a partida termina em game over.

## Funcionalidades

- Gameplay 2D em tempo real com loop dedicado próximo de 60 FPS.
- Movimento por joystick virtual e suporte a multitoque.
- Disparo normal contínuo enquanto o botão é mantido pressionado.
- Ataque especial radial com cooldown.
- Três tipos de inimigos: Slime, Fast e Skeleton.
- Aumento progressivo da frequência e composição das hordas.
- Separação entre inimigos para reduzir sobreposição visual.
- Colisão contínua de projéteis para evitar que tiros rápidos atravessem alvos.
- Orbes de XP com atração magnética.
- Level up com três opções de melhoria.
- 14 tipos de upgrades para jogador, arma e ataque especial.
- Chance de dano crítico, regeneração, multishot e melhorias acumuláveis.
- Estados de pausa, level up, game over e vitória.
- Opção de jogar novamente sem recriar desnecessariamente a engine.
- HUD compacto com vida, experiência, tempo, abates e pausa.
- Modo imersivo fullscreen e orientação landscape fixa.
- Cenário renderizado com center crop, sem distorção.
- Sprites direcionais e animações de personagem, inimigos e morte.
- Progresso local persistente: moedas, abates totais e melhor tempo.

## Como jogar

| Controle | Localização | Ação |
|---|---|---|
| Joystick virtual | Lado esquerdo | Move o personagem e define sua direção |
| **ATIRAR** | Canto inferior direito | Dispara; pode ser mantido pressionado |
| **SPECIAL** | Acima do botão de tiro | Lança uma rajada radial quando disponível |
| `Ⅱ` | Canto superior direito | Pausa a partida |
| Voltar do Android | Durante a partida | Abre a pausa |

### Objetivo

1. Mova-se para evitar o contato com os inimigos.
2. Use o disparo normal e o especial para eliminá-los.
3. Colete os orbes verdes para ganhar experiência.
4. Escolha uma melhoria sempre que subir de nível.
5. Sobreviva por 10 minutos para vencer.

### Melhorias disponíveis

- Velocidade de movimento;
- HP máximo e regeneração de vida;
- Dano e cadência da arma;
- Velocidade, tamanho e quantidade de projéteis;
- Chance de crítico;
- Alcance magnético de coleta;
- Dano, recarga, quantidade de projéteis e tamanho do ataque especial.


## Tecnologias

- [Kotlin 2.2.10](https://kotlinlang.org/)
- Android SDK 37 (`minSdk 26`, `targetSdk 37`)
- Android Gradle Plugin 9.3.2
- Gradle Wrapper 9.5.0
- Java 11 como compatibilidade de código-fonte e bytecode
- Jetpack Compose + Material 3
- `SurfaceView`, `Canvas` e `Paint` para o jogo
- Android Lifecycle e `ViewModel`
- Kotlin Coroutines e `StateFlow`
- Preferences DataStore
- JUnit 4, AndroidX Test e Espresso

## Arquitetura

O aplicativo usa um único módulo, `:app`, e separa responsabilidades entre UI declarativa, integração Android, domínio, renderização e persistência.

```text
MainActivity
    └── DrawnToSurviveApp (Compose)
          ├── MainViewModel ── ProgressRepository ── DataStore
          └── GameScreen
                ├── GameHud e overlays (Compose)
                └── GameView (SurfaceView/Canvas)
                      ├── GameLoop
                      ├── GameEngine
                      ├── TouchController
                      └── SpriteStore
```

### Componentes principais

- **`MainActivity`**: hospeda o Compose, configura modo imersivo e pausa o jogo quando o app vai para segundo plano.
- **`GameUi.kt`**: contém menu, tela de jogo, HUD e overlays. Converte ações da interface em `GameCommand`.
- **`MainViewModel`**: coordena engine e persistência, mantendo o progresso em `StateFlow`.
- **`GameEngine`**: fonte de verdade da partida; processa movimento, disparos, spawns, colisões, XP, upgrades e resultados.
- **`GameLoop`**: thread dedicada que atualiza e renderiza a partida, com alvo de aproximadamente 60 FPS.
- **`GameView`**: desenha cenário, entidades, efeitos e controles no `Canvas`.
- **`TouchController`**: interpreta entrada multitoque e entrega snapshots seguros à engine.
- **`SpriteStore`**: carrega e mantém os bitmaps em cache durante o processo.
- **`ProgressRepository`**: salva o progresso local com Preferences DataStore.

A UI observa apenas snapshots de `GameUiState`. Comandos externos passam por uma `ConcurrentLinkedQueue`, reduzindo mutações concorrentes entre a thread principal e o game loop.

## Estrutura do projeto

```text
DrawntoSurvive/
├── app/
│   ├── src/main/
│   │   ├── assets/images/       # Cenário, sprites e spritesheets
│   │   ├── java/com/rodrigo/drawntosurvive/
│   │   │   ├── data/            # Persistência do progresso
│   │   │   ├── game/            # Engine, loop, modelos, input e renderização
│   │   │   ├── ui/              # Compose, HUD, telas e tema
│   │   │   ├── MainActivity.kt
│   │   │   └── MainViewModel.kt
│   │   └── AndroidManifest.xml
│   ├── src/test/                 # Testes unitários locais
│   └── src/androidTest/          # Testes instrumentados
├── gradle/                       # Wrapper e catálogo de versões
├── agente.md                     # Diretrizes técnicas do projeto
├── build.gradle.kts
└── settings.gradle.kts
```

## Requisitos

- Android Studio com suporte às versões de AGP e Kotlin usadas pelo projeto;
- JDK compatível com Gradle 9.5 e AGP 9.3.2 (prefira o JDK integrado ao Android Studio);
- Android SDK Platform 37 instalado;
- dispositivo ou emulador com Android 8.0 / API 26 ou superior;
- Git para clonar o repositório.

> Não é necessário instalar o Gradle globalmente: o projeto inclui o Gradle Wrapper.

## Como executar

### Android Studio

1. Clone o repositório:

```bash
git clone https://github.com/RodrigoAguiarS/DrawntoSurvive.git
cd DrawntoSurvive
```

2. Abra a pasta no Android Studio.
3. Aguarde a sincronização do Gradle e o download das dependências.
4. Selecione um dispositivo físico ou emulador com API 26+.
5. Execute a configuração **app**.

O jogo permanece em orientação horizontal. As barras do sistema são ocultadas, mas podem aparecer temporariamente por gesto nas bordas.


### Linha de comando

Windows:

```powershell
.\gradlew.bat installDebug
```

Linux ou macOS:

```bash
./gradlew installDebug
```

É necessário ter um dispositivo ou emulador conectado e reconhecido pelo ADB.

## Build e testes

Execute a partir da raiz do projeto.

### Validação completa de desenvolvimento

Windows:

```powershell
.\gradlew.bat test assembleDebug --console=plain
```

Linux ou macOS:

```bash
./gradlew test assembleDebug --console=plain
```

### Apenas testes unitários

```powershell
.\gradlew.bat test
```

Os testes cobrem regras matemáticas, direções, colisões, espalhamento de tiros, ataque radial, progressão, separação de inimigos, animação de salto e reinício de partida.

### APK de debug

Após `assembleDebug`, o APK é gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Testes instrumentados

Com um dispositivo ou emulador ativo:

```powershell
.\gradlew.bat connectedAndroidTest
```

## Configuração do gameplay

As constantes de balanceamento ficam em:

```text
app/src/main/java/com/rodrigo/drawntosurvive/game/GameConfig.kt
```

Esse arquivo centraliza duração da partida, atributos iniciais, cooldowns, limites de entidades, intervalos de spawn, colisões e bônus de vitória. Evite espalhar números de balanceamento pela engine; prefira incluir novas constantes em `GameConfig`.

A flag `DEBUG_FAST_RUN` deve permanecer desativada em builds normais.

## Persistência

O progresso é armazenado localmente com Preferences DataStore no arquivo lógico `player_progress`. Atualmente são persistidos:

- total de moedas;
- total de inimigos abatidos;
- melhor tempo de sobrevivência.

Ao concluir uma partida, a engine produz um `RunResult`; o `MainViewModel` solicita a gravação ao `ProgressRepository` em uma coroutine. Os dados permanecem entre execuções, mas podem ser removidos ao limpar os dados ou desinstalar o app.

## Assets

Os recursos gráficos estão em:

```text
app/src/main/assets/images/
├── cenario.png
├── Sprites/
│   ├── Base Character/
│   ├── Death FX/
│   ├── Hero/
│   ├── Jump FX/
│   ├── Monster/
│   └── Skeleton/
└── Spritesheets/
```

O `SpriteStore` carrega os arquivos utilizados pelo jogo e mantém os bitmaps em cache. O cenário é desenhado com recorte central para preencher a tela sem deformar a imagem. Se um sprite esperado não puder ser carregado, a renderização utiliza um fallback simples em vez de interromper a partida.

> Nomes e capitalização dos arquivos devem ser preservados, pois caminhos em `assets` podem ser sensíveis a maiúsculas e minúsculas dependendo do ambiente.

### Créditos dos recursos visuais

Este repositório ainda não contém um arquivo consolidado com autoria, origem e licença dos assets gráficos. Antes de redistribuir ou publicar comercialmente o jogo, documente os créditos e confirme se cada recurso permite o uso pretendido.

## Como contribuir

1. Crie um fork ou uma branch a partir da versão mais recente.
2. Faça alterações pequenas e focadas.
3. Preserve a separação entre Compose, engine, renderização e dados.
4. Adicione testes para regras determinísticas ou correções de gameplay.
5. Execute `test assembleDebug` antes de abrir um pull request.
6. Valide em dispositivo ou emulador quando a alteração afetar UI, toque, lifecycle ou renderização.

Convenção sugerida para commits:

```text
feat(game): adiciona nova mecânica
fix(engine): corrige estado ao reiniciar partida
test(game): cobre nova regra de colisão
docs(readme): atualiza instruções do projeto
```

Para orientações técnicas detalhadas, consulte [`agente.md`](agente.md).

## Limitações e próximos passos

- A interface e os controles precisam ser validados visualmente em diferentes proporções e densidades de tela.
- O balanceamento das hordas e melhorias ainda pode ser refinado.
- Os créditos e licenças dos assets devem ser consolidados.
- O projeto ainda pode receber testes instrumentados mais abrangentes para navegação e controles.
- Não há pipeline de integração contínua documentado neste momento.

## Licença

Nenhum arquivo de licença foi identificado no repositório. Portanto, o código e os assets **não devem ser considerados automaticamente livres para reutilização, modificação ou redistribuição**.

Para tornar o projeto open source, adicione um arquivo `LICENSE` compatível com o objetivo do projeto e documente separadamente as licenças dos recursos gráficos de terceiros.

---

Desenvolvido por [Rodrigo Aguiar](https://github.com/RodrigoAguiarS).

