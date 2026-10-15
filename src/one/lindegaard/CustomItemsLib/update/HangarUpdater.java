package one.lindegaard.CustomItemsLib.update;

import one.lindegaard.CustomItemsLib.Core;

public class HangarUpdater extends HangarUpdateService {

	public HangarUpdater(Core plugin) {
		super(plugin, "Becerritoo", "CustomItemsLib");
	}

	@Override
	protected long getCheckIntervalSeconds() {
		return Core.getConfigManager().checkEvery;
	}

	@Override
	protected boolean isAutoUpdateEnabled() {
		return Core.getConfigManager().autoupdate;
	}

	@Override
	protected String getPrefix() {
		return Core.PREFIX;
	}

	@Override
	protected String getCheckingMessage() {
		return Core.getMessages().getString("core.commands.update.check");
	}

	@Override
	protected String getVersionFoundMessage(String version) {
		return Core.getMessages().getString("core.commands.update.version-found", "newversion", version);
	}

	@Override
	protected String getNoUpdateMessage() {
		return Core.getMessages().getString("core.commands.update.no-update");
	}

	@Override
	protected String getUpdateCompleteMessage() {
		return Core.getMessages().getString("core.commands.update.complete");
	}

	@Override
	protected String getCouldNotUpdateMessage() {
		return Core.getMessages().getString("core.commands.update.could-not-update");
	}

	@Override
	protected String getUpdateHelpMessage(String projectUrl) {
		return Core.getMessages().getString("core.commands.update.help") + " " + projectUrl;
	}
}
