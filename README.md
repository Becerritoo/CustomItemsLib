CustomItemsLib
=====================
CustomItemsLib is a library used by both BagOfGold and MobHunting. It is mandatory.

## Maintained fork

This fork is maintained by [Becerritoo](https://github.com/Becerritoo) and continues the
work originally created by Rocologo. It provides the shared item, reward, storage and
compatibility layer used by MobHunting and BagOfGold on current Paper releases.

The current release is `1.4.1`, targeting Paper 1.21.11. Releases follow
[Semantic Versioning](https://semver.org/); the library version is independent from the
Minecraft or Paper version.

Version `1.4.1` makes multi-token physical-currency spends atomic so a failed or
duplicated token claim cannot leave a partially recorded transaction.

CMI and CMILib are separate optional integrations. CMI provides its hologram API, while
CMILib provides action-bar and boss-bar messages. Each integration has its own setting:

```yaml
plugins:
  cmi:
    enable_integration_cmi: false
  cmilib:
    enable_integration_cmilib: true
```

See [changelog.txt](changelog.txt) for the complete history and
[the 1.4.1 release notes](releases/v1.4.1.md) for compatibility and validation details.
The maintained responsibility boundaries and compatibility transition are described
in [ARCHITECTURE.md](ARCHITECTURE.md). The release test scope is recorded
in [COMPATIBILITY.md](COMPATIBILITY.md).
