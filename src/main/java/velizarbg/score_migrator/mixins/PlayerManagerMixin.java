package velizarbg.score_migrator.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
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

	@Inject(method = "onPlayerConnect", at = @At("HEAD"))
	private void shareCachedName(CallbackInfo ci, @Local(argsOnly = true) ServerPlayerEntity player,
								 @Share("currentName") LocalRef<String> currentNameRef,
								 @Share("cachedName") LocalRef<String> cachedNameRef
	) {
		PlayerConfigEntry configEntry = player.getPlayerConfigEntry();
		String currentName = configEntry.name();
		String cachedName = server.getApiServices().nameToIdCache()
			.getByUuid(configEntry.id()).map(PlayerConfigEntry::name).orElse(currentName);
		currentNameRef.set(currentName);
		cachedNameRef.set(cachedName);
	}

	@SuppressWarnings("DataFlowIssue") // score can't be null
	@Inject(method = "onPlayerConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendScoreboard(Lnet/minecraft/scoreboard/ServerScoreboard;Lnet/minecraft/server/network/ServerPlayerEntity;)V"))
	private void migrateScores(CallbackInfo ci, @Local(argsOnly = true) ServerPlayerEntity player,
							   @Share("currentName") LocalRef<String> currentNameRef,
							   @Share("cachedName") LocalRef<String> cachedNameRef
	) {
		String cachedName = cachedNameRef.get();
		if (cachedName.equals(currentNameRef.get()))
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
