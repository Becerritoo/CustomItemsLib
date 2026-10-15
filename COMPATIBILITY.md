# Compatibility status

CustomItemsLib 1.3.0 is released for Paper 1.21.11 and Java 21. This file
records the validation completed for the release.

## Plugin combinations

The following combinations reached Paper's ready state and shut down cleanly
without CustomItemsLib, BagOfGold or MobHunting linkage errors:

| Combination | Versions | Status |
| --- | --- | --- |
| CustomItemsLib only | 1.3.0 | Startup and shutdown verified |
| CustomItemsLib and BagOfGold | 1.3.0 / 4.7.0 | Startup and shutdown verified |
| CustomItemsLib and MobHunting | 1.3.0 / 8.7.0 | Startup and shutdown verified |
| All three plugins | 1.3.0 / 4.7.0 / 8.7.0 | Startup and shutdown verified |

The build was also checked to contain no class references to the BagOfGold or
MobHunting implementation packages. BagOfGold and MobHunting consume the
library, while optional consumer behavior is supplied through Bukkit's
`ServicesManager`.

## Optional integrations

The reference environment includes CMILib, Citizens, EssentialsX,
PlaceholderAPI, ProtocolLib, PerWorldInventory, Shopkeepers, Vault, WorldEdit
and WorldGuard. Their load paths were exercised during the combination tests.
This technical matrix does not replace interaction tests for every inventory
move, physical-currency trade or reward path.

Other optional integrations declared by the plugin were not verified in this
development cycle. A retained integration is not considered incompatible only
because it is absent from the reference environment.
