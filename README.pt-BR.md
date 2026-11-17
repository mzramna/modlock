<img src="docs/icon.png" width="128" alt="ModLock icon" align="right">

# ModLock

[English](README.md) | **Português (Brasil)**

Mod para NeoForge que restringe o **craft manual** de itens por mod, por jogador. É pensado para
modpacks de progressão e séries de interação: cada jogador só crafta à mão os itens dos mods que liberou.
Máquinas e automação nunca são restringidas.

## Downloads
Baixe o jar da sua versão do Minecraft em [Releases](https://github.com/mzramna/modlock/releases/latest):

| Jar                        | Minecraft | NeoForge | Packs |
|----------------------------|-----------|----------|-------|
| `modlock-1.21.1-<ver>.jar` | 1.21.1    | 21.1.x   | ATM10, ATM10 To The Sky, ATM10 Aeronautics, All the Mons |
| `modlock-26.1.2-<ver>.jar` | 26.1.2    | 26.1.x   | ATM11 |

Instale **no servidor e nos clientes**.

## Funcionalidades (etapa 1)
- **Mesa de craft e grade 2x2:** o resultado não aparece se o jogador não tem acesso ao mod do item; uma prévia vermelha mostra o motivo.
- **Mesa de ferraria e cortador de pedras:** mesma regra.
- **Fornalha, alto-forno e defumador:** aceitam o item, mas não cozinham se **quem colocou à mão** não tem acesso. Funil e automação sempre funcionam.
- **Máquinas de outros mods:** o jogador não consegue colocar à mão um item quando **todas** as receitas de ingrediente único dele geram algo bloqueado. Se alguma receita gera algo permitido, o item é aceito. Canos e funis nunca são checados.
- **Modo estrito opcional** (desligado por padrão): também bloqueia usar, colocar e abrir blocos, itens e entidades de mods bloqueados.
- Listas `always_allowed` / `manual_only` por ID de receita ou `item:<id>`.
- Tooltips de cadeado (inclusive no JEI/EMI), traduções em inglês e português.

## Pacotes de mods (0.2.0)
Um pacote libera um grupo de mods de uma vez — por exemplo **Create + todos os addons do Create instalados no servidor**.
Crie um arquivo JSON por pacote em `config/modlock/bundles/<id>.json` (um exemplo, `_example.json.txt`, é gerado lá):

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

- Os addons são resolvidos quando o servidor inicia ou com `/modlock reload`; addons novos entram no pacote sozinhos.
- Com `lock_bundle_members = true` (padrão), os addons ficam bloqueados sempre que o mod base do pacote está bloqueado.
- **Classes (servidores RP):** `/modlock class add|remove <jogadores> <classe>` ou a tag de entidade `modlock.class.<classe>` (funciona com command blocks e mods de quest).

## Tela de escolha inicial
Com `show_selection_screen = true`, o jogador escolhe `starting_count` itens no primeiro login: pacotes que a classe dele
pode usar e, se `allow_single_mods = true`, mods avulsos bloqueados. A tela mostra a imagem, a descrição e os mods de cada
pacote. `exclusive = true` faz cada item poder ser escolhido por um jogador só. O jogador reabre a tela com
`/modlock choose` até confirmar; OPs reiniciam com `/modlock resetchoice <jogadores>`.

## Configuração
Arquivo `serverconfig/modlock-server.toml` do mundo (ou `defaultconfigs/` no modpack).

| Opção | Descrição |
|-------|-----------|
| `locked_mods` | Mods controlados pelo ModLock. `["*"]` bloqueia todos os mods, exceto `minecraft` e os de `global_allowed_mods`. |
| `global_allowed_mods` | Sempre liberados para todos. |
| `always_allowed` / `manual_only` | Exceções por receita. |
| `smelting_enabled`, `smithing_and_stonecutter` | Estações vanilla. |
| `block_manual_insert`, `insert_exempt_blocks` | Regra de máquinas e blocos de armazenamento ignorados (também a tag `#modlock:insert_exempt`). |
| `block_usage_of_locked_mods` | Modo estrito. |
| `starting_count`, `show_selection_screen`, `allow_single_mods`, `choosable_mods`, `exclusive`, `announce_choice` | Escolha inicial. |
| `lock_bundle_members` | Bloqueia addons de pacotes com mod base bloqueado. |
| `block_modlock_crafting` | Ninguém pode craftar itens do ModLock (inclusive automação). |
| `loot.enabled`, `loot.tome_bundles` | Itens de liberação nos baús / só tomos desses pacotes (valem quando os tomos existirem). |

## Comandos (OP nível 2)
```
/modlock grant|revoke <jogadores> mod <modid>
/modlock grant|revoke <jogadores> item <item>
/modlock grant|revoke <jogadores> machine <bloco>
/modlock grant|revoke <jogadores> bundle <id>
/modlock class add|remove <jogadores> <classe>
/modlock bundles [id]
/modlock resetchoice <jogadores>
/modlock list [jogador]
/modlock reload
```
Qualquer jogador: `/modlock choose`.

## Próximas etapas
Chave de Mod → efeitos de conhecimento temporário → Pergaminho de Receita, Tomos e Licença de Máquina → acabamento com JEI/EMI.

## Compilando
| Branch   | Java |
|----------|------|
| `1.21.1` | 21   |
| `26.1`   | 25   |

```bash
./gradlew build              # jar em build/libs
./gradlew runGameTestServer  # GameTests automatizados
```

O CI roda build + GameTests em todo push e pull request. Enviar uma tag `v*` (ex.: `v0.1.0`), ou fazer push no branch `release` (a tag `v<mod_version>` é criada automaticamente), compila e testa os dois branches e publica um release com os dois jars.

## Licença
[MIT](LICENSE)
