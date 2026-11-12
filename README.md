# ModLock

Mod para NeoForge que restringe o **craft manual** de itens por mod, por jogador. É pensado para
modpacks de progressão e séries de interação: cada jogador só crafta à mão os itens dos mods que liberou.

| Branch   | Minecraft | NeoForge | Packs |
|----------|-----------|----------|-------|
| `1.21.1` | 1.21.1    | 21.1.x   | ATM10, ATM10 Sky, ATM10 Aeronautics, All the Mons |
| `26.1`   | 26.1.2    | 26.1.x   | ATM11 (port pendente) |

## Etapa 1 (atual)
- Mesa de craft e grade 2x2: o resultado não aparece se o jogador não tem acesso; aparece uma prévia vermelha com o motivo.
- Smithing e stonecutter: mesma regra.
- Fornalha, alto-forno e defumador: aceitam o item, mas não cozinham se **quem colocou o item à mão** não tem acesso. Funil e automação sempre funcionam.
- Máquinas de outros mods: o jogador não consegue colocar à mão um item cujas receitas (de ingrediente único) só geram itens bloqueados. Se alguma receita gera algo permitido, o item é aceito. Funis e canos nunca são checados.
- Modo estrito opcional (desligado por padrão): bloqueia usar, colocar e abrir blocos, itens e entidades de mods sem acesso.
- `always_allowed` / `manual_only` por receita ou item.
- Comandos (OP nível 2):
  - `/modlock grant|revoke <jogadores> mod <modid>`
  - `/modlock grant|revoke <jogadores> item <item>`
  - `/modlock grant|revoke <jogadores> machine <bloco>`
  - `/modlock list [jogador]`, `/modlock reload`

Config: `serverconfig/modlock-server.toml` do mundo (ou `defaultconfigs/` no modpack). `locked_mods = ["*"]` bloqueia todos os mods, exceto `minecraft` e os de `global_allowed_mods`.

## Build e testes
```bash
./gradlew build              # jar em build/libs
./gradlew runGameTestServer  # GameTests automatizados
```
