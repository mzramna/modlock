<div align="center">

<img src="docs/icon.png" width="160" alt="Ícone do ModLock">

# ModLock

**Libere mods jogando.**
Bloqueie o craft manual por mod e por jogador, e deixe os jogadores conquistarem mods, ou pacotes inteiros de mods com todos os addons, ao longo do tempo.

[English](README.md) | **Português (Brasil)**

[![Release](https://img.shields.io/github/v/release/mzramna/modlock?label=release&color=f0b429)](https://github.com/mzramna/modlock/releases/latest)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%2026.1.2-62b47a)
![NeoForge](https://img.shields.io/badge/loader-NeoForge-e8742c)
[![Build](https://img.shields.io/github/actions/workflow/status/mzramna/modlock/build.yml?branch=1.21.1&label=build)](https://github.com/mzramna/modlock/actions)
[![Licença: MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-blue)](LICENSE)

</div>

<p align="center">
  <img src="docs/screenshots/pt-choice-screen.png" width="820" alt="Tela de escolha inicial com o pacote Create selecionado">
</p>

## Por que usar o ModLock?

- 🧭 **Modpacks de progressão:** comece com poucos mods e libere o resto com quests, loot ou recompensas dos admins.
- 🎬 **Séries de interação e YouTube:** "Fulano é dono do Create, Ciclano do Mekanism" vira regra do jogo, não acordo de cavalheiros.
- 🛡️ **Servidores RP com classes:** cada classe tem seu próprio pacote fechado de mods (engenheiros ficam com a eletricidade, magos com a magia…).
- ⚙️ **Leve de propósito:** só o *craft manual* é restringido. Máquinas, automação e funis continuam funcionando, então os outros mods rodam intactos e rápidos.

## Galeria

| Craft bloqueado | Liberado depois da escolha |
|:---:|:---:|
| <img src="docs/screenshots/pt-craft-locked.png" width="400" alt="Resultado de craft bloqueado com tooltip"> | <img src="docs/screenshots/craft-unlocked.png" width="400" alt="A mesma receita liberada"> |
| O resultado não aparece: um fantasma vermelho explica o motivo. | Escolhendo o pacote, a receita funciona. |

| Pacote restrito a uma classe | Pacote fechado tem prioridade |
|:---:|:---:|
| <img src="docs/screenshots/choice-class-bundle.png" width="400" alt="Pacote de classe na tela de escolha"> | <img src="docs/screenshots/closed-bundle.png" width="400" alt="Item ainda bloqueado por um pacote fechado de classe"> |
| Só *Engenheiros* veem o pacote **Electric Engineers**. | Um addon de um pacote fechado de classe continua bloqueado pras outras classes, mesmo dentro do pacote Create. |

| Tooltip de cadeado | Comandos |
|:---:|:---:|
| <img src="docs/screenshots/pt-tooltip-locked.png" width="300" alt="Tooltip de cadeado em um item"> | <img src="docs/screenshots/chat-commands.png" width="500" alt="Comandos do ModLock no chat"> |

## Funcionalidades

- 🔒 **Bloqueio de craft por mod**: mesa de craft, grade 2x2, mesa de ferraria e cortador de pedras.
- 🔥 **Fornalhas**: aceitam qualquer item, mas não cozinham se quem colocou à mão não tem acesso. Funis sempre funcionam.
- 🏭 **Máquinas de outros mods**: o jogador não consegue colocar à mão um item cujas receitas só geram coisas que ele não pode craftar.
- 📦 **Pacotes de mods**: um arquivo JSON libera um mod **e todos os addons dele instalados**, detectados sozinhos pelas dependências.
- 🎭 **Classes de RP**: pacotes restritos e **fechados**. Dê classes por comando ou pela tag `modlock.class.<classe>`.
- 🖼️ **Tela de escolha inicial**: o jogador escolhe pacotes ou mods avulsos no primeiro login, com imagem, descrição e lista de mods. Exclusividade opcional ("já escolhido por…").
- ✅ **Exceções**: `always_allowed` e `manual_only` por receita ou item, e um modo estrito opcional que também bloqueia usar e colocar blocos de mods bloqueados.
- 🌍 **Traduzido**: inglês e português do Brasil. Toda mensagem é traduzida no cliente.

## Começo rápido

1. Baixe o jar da sua versão do Minecraft em **[Releases](https://github.com/mzramna/modlock/releases/latest)** e instale **no servidor e em todos os clientes**.
2. Em `config/modlock-server.toml`, bloqueie alguns mods: `locked_mods = ["create", "mekanism"]` (ou `["*"]` pra todos).
3. Opcional: crie um pacote em `config/modlock/bundles/create.json` e dê com `/modlock grant <jogador> bundle create`, ou ligue `show_selection_screen` pra que os jogadores escolham.

| Minecraft | NeoForge | Java | Modpacks |
|---|---|---|---|
| 1.21.1 | 21.1+ | 21 | ATM10, ATM10 To The Sky, ATM10 Aeronautics, All the Mons |
| 26.1.2 | 26.1+ | 25 | ATM11 |

Páginas no Modrinth e no CurseForge em breve.

## Pacotes de mods

Um arquivo por pacote em `config/modlock/bundles/<id>.json` (um exemplo comentado, `_example.json.txt`, é gerado lá):

```jsonc
{
  "name": "Create",                 // obrigatório
  "icon": "create:cogwheel",        // opcional: item usado como ícone
  "image": "create.png",            // opcional: imagem da tela de escolha (PNG ao lado deste arquivo, até 256 KiB,
                                    //           ou textura de resource pack, ex. "meupack:textures/gui/create.png")
  "description": "Create e seus addons",
  "mods": ["create"],               // mods base
  "include_addons": true,           // + todo mod instalado com dependência OBRIGATÓRIA em um membro (recursivo)
  "include": [], "exclude": [],
  "classes": [],                    // RP: só jogadores com uma dessas classes podem usar
  "closed": false                   // membros são negados a quem está fora de "classes", mesmo que liberados de outro jeito
}
```

Os addons são resolvidos quando o servidor inicia ou com `/modlock reload`, então addons novos entram sozinhos. Com `lock_bundle_members = true` (padrão), os addons ficam bloqueados sempre que o mod base do pacote está bloqueado.

## Configuração

`serverconfig/modlock-server.toml` do mundo (ou `defaultconfigs/` no modpack).

| Opção | O que faz |
|---|---|
| `locked_mods` / `global_allowed_mods` | Mods controlados pelo ModLock / sempre liberados. `["*"]` bloqueia tudo exceto `minecraft`. |
| `always_allowed` / `manual_only` | Exceções por receita (ID da receita ou `item:<id>`). |
| `smelting_enabled`, `smithing_and_stonecutter` | Estações vanilla. |
| `block_manual_insert`, `insert_exempt_blocks` | Regra de máquinas e blocos de armazenamento ignorados (também a tag `#modlock:insert_exempt`). |
| `block_usage_of_locked_mods` | Modo estrito: sem usar, colocar ou abrir blocos e itens de mods bloqueados. |
| `starting_count`, `show_selection_screen`, `allow_single_mods`, `choosable_mods`, `exclusive`, `announce_choice` | Escolha inicial. |
| `lock_bundle_members` | Bloqueia os addons de pacotes com mod base bloqueado. |
| `block_modlock_crafting` | Ninguém pode craftar itens do ModLock, nem a automação. |
| `loot.enabled`, `loot.tome_bundles` | Itens de liberação nos baús (passam a valer quando os tomos existirem). |

## Comandos

```
/modlock grant|revoke <jogadores> mod|item|machine|bundle <id>
/modlock class add|remove <jogadores> <classe>
/modlock bundles [id]
/modlock resetchoice <jogadores>
/modlock list [jogador]
/modlock reload
```
Qualquer jogador pode usar `/modlock choose` pra reabrir a tela de escolha enquanto não confirmar.

## Perguntas frequentes

**Bloqueia máquinas e automação?** Não. Só o craft feito à mão é checado. Um funil abastecendo a fornalha, um autocrafter do AE2 ou um mechanical crafter do Create continuam funcionando. É de propósito: deixa o mod leve e compatível.

**Precisa estar no cliente?** Sim. A tela de escolha, os tooltips de cadeado e a prévia vermelha rodam no cliente.

**Funciona com KubeJS ou mods de quest?** Sim. Classes podem ser dadas pela tag `modlock.class.<classe>`, e o resto pelos comandos `/modlock`.

## Próximas etapas

Chave de Mod (recompensa de quest) → efeitos de conhecimento temporário → Pergaminho de Receita, Tomos e Licença de Máquina → acabamento com JEI/EMI.

## Compilando

| Branch | Minecraft | Java |
|---|---|---|
| `1.21.1` | 1.21.1 | 21 |
| `26.1` | 26.1.2 | 25 |

```bash
./gradlew build              # jar em build/libs
./gradlew runGameTestServer  # GameTests automatizados
```

O CI roda build e GameTests em todo push e pull request. Enviar uma tag `v*`, ou fazer push no branch `release` (a tag `v<mod_version>` é criada automaticamente), compila e testa os dois branches e publica um release com um jar por versão do Minecraft.

## Licença

[MIT](LICENSE)
