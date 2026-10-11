# CustomItemsLib architecture

CustomItemsLib provides the shared reward-item infrastructure used by
BagOfGold and MobHunting. It must remain able to start without either consumer
plugin installed.

## Public contract

CustomItemsLib owns the general operations that are independent of a gameplay
plugin:

- create, identify, sign and validate reward items;
- preserve the existing reward metadata and signatures;
- combine, split, place, break, drop and collect reward items safely;
- prevent duplicated reward tokens from being spent more than once;
- expose stable APIs, events and service interfaces for consumer plugins;
- provide shared player settings and presentation helpers.

Consumer-specific behavior is supplied through interfaces declared by
CustomItemsLib and implementations registered with Bukkit's `ServicesManager`.
CustomItemsLib must not import implementation classes from a consumer plugin.

`PhysicalCurrencyService` is the first of these contracts. A currency plugin
uses it to account for physical reward items as they move into or out of a
player inventory. The service is optional and is looked up at the time of each
operation so consumers can be enabled after CustomItemsLib.

## Consumer responsibilities

BagOfGold owns physical-currency policy and accounting, including player
balances, debt, banks, Vault and Reserve providers, Shopkeepers behavior,
Citizens banker traits and BagOfGold placeholders.

MobHunting owns kill detection, rewards and penalties, achievements, bounties,
leaderboards, mob integrations and its update lifecycle.

Both plugins may use CustomItemsLib reward items and events. Their absence must
not prevent CustomItemsLib from loading.

## Persistence ownership

CustomItemsLib owns its player settings and reward-token security records.
BagOfGold owns balances, debt and bank records. MobHunting owns hunt statistics,
achievements, bounties and mob records.

Historical table names, metadata keys, signatures and serialized values remain
unchanged during the 1.3.x transition. Schema ownership will be clarified before
any migration is introduced.

## Compatibility transition

The 1.3.x line adds the new service contracts while retaining the public
compatibility classes currently used by released versions of BagOfGold and
MobHunting. Those classes may be marked deprecated after both consumers use the
new contracts. Removing them requires a future major release.

Development and validation proceed in this order:

1. add a backward-compatible CustomItemsLib API;
2. migrate BagOfGold to register its services;
3. migrate MobHunting to consume the shared API;
4. verify CustomItemsLib alone and each supported plugin combination;
5. remove deprecated entry points only in a major release.
