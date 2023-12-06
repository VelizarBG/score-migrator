package velizarbg.score_migrator.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
	@Shadow @Final private MinecraftServer server;

	@SuppressWarnings("DataFlowIssue") // score can't be null
	@Inject(method = "onPlayerConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/UserCache;add(Lcom/mojang/authlib/GameProfile;)V"))
	private void migrateScores(CallbackInfo ci, @Local ServerPlayerEntity player, @Local String cachedName) {
		String newName = player.getGameProfile().getName();
		if (cachedName.equals(newName))
			return;

		ServerScoreboard scoreboard = server.getScoreboard();
		ScoreHolder scoreHolder = ScoreHolder.fromName(cachedName);
		scoreboard.getScoreHolderObjectives(scoreHolder).forEach((objective, scoreInt) -> {
			ScoreboardScore oldScore = (ScoreboardScore) scoreboard.getScore(scoreHolder, objective);
			ScoreAccess newScore = scoreboard.getOrCreateScore(player, objective, true);
			newScore.setDisplayText(oldScore.getDisplayText());
			newScore.setNumberFormat(oldScore.getNumberFormat());
			newScore.setScore(scoreInt);
		});
		scoreboard.removeScores(scoreHolder);
	}
}
