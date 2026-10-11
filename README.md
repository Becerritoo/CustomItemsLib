CustomItemsLib
=====================
CustomItemsLib is a library used by both BagOfGold and MobHunting. It is mandatory.

## Maintained fork

This fork is maintained by [Becerritoo](https://github.com/Becerritoo) and continues the
work originally created by Rocologo. It provides the shared item, reward, storage and
compatibility layer used by MobHunting and BagOfGold on current Paper releases.

The current release is `1.2.0`, validated on Paper 1.21.11. Releases follow
[Semantic Versioning](https://semver.org/); the library version is independent from the
Minecraft or Paper version.

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
[the 1.2.0 release notes](releases/v1.2.0.md) for compatibility and validation details.
The maintained responsibility boundaries and compatibility transition are described
in [ARCHITECTURE.md](ARCHITECTURE.md).
