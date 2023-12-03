package velizarbg.score_migrator.mixins;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.ClientConnection;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
	private static String cachedName;

	@Shadow @Final private MinecraftServer server;

	@ModifyVariable(method = "onPlayerConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/UserCache;add(Lcom/mojang/authlib/GameProfile;)V"))
	private String getCachedName(String string) {
		cachedName = string;
		return string;
	}

	@Inject(method = "onPlayerConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendScoreboard(Lnet/minecraft/scoreboard/ServerScoreboard;Lnet/minecraft/server/network/ServerPlayerEntity;)V"))
	private void migrateScores(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
		String newName = player.getGameProfile().getName();
		if (server.getUserCache() == null || cachedName.equals(newName))
			return;

		ServerScoreboard scoreboard = server.getScoreboard();
		ScoreHolder oldScoreHolder = ScoreHolder.fromName(cachedName);
		Object2IntMap<ScoreboardObjective> objectives = scoreboard.getScoreHolderObjectives(oldScoreHolder);
		objectives.forEach((scoreboardObjective, score) -> {
			scoreboard.removeScore(oldScoreHolder, scoreboardObjective);
			if (!scoreboardObjective.getCriterion().isReadOnly()) {
				scoreboard.getOrCreateScore(player, scoreboardObjective, false).setScore(score);
			}
		});
	}
}
