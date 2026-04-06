package velizarbg.score_migrator.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {
	@Shadow @Final private MinecraftServer server;

	@Inject(method = "placeNewPlayer", at = @At("HEAD"))
	private void shareCachedName(CallbackInfo ci, @Local(argsOnly = true) ServerPlayer player,
								 @Share("currentName") LocalRef<String> currentNameRef,
								 @Share("cachedName") LocalRef<String> cachedNameRef
	) {
		NameAndId nameAndId = player.nameAndId();
		String currentName = nameAndId.name();
		String cachedName = server.services().nameToIdCache()
			.get(nameAndId.id()).map(NameAndId::name).orElse(currentName);
		currentNameRef.set(currentName);
		cachedNameRef.set(cachedName);
	}

	@SuppressWarnings("DataFlowIssue") // score can't be null
	@Inject(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;updateEntireScoreboard(Lnet/minecraft/server/ServerScoreboard;Lnet/minecraft/server/level/ServerPlayer;)V"))
	private void migrateScores(CallbackInfo ci, @Local(argsOnly = true) ServerPlayer player,
							   @Share("currentName") LocalRef<String> currentNameRef,
							   @Share("cachedName") LocalRef<String> cachedNameRef
	) {
		String cachedName = cachedNameRef.get();
		if (cachedName.equals(currentNameRef.get()))
			return;

		ServerScoreboard scoreboard = server.getScoreboard();
		ScoreHolder scoreHolder = ScoreHolder.forNameOnly(cachedName);
		scoreboard.listPlayerScores(scoreHolder).forEach((objective, scoreInt) -> {
			Score oldScore = (Score) scoreboard.getPlayerScoreInfo(scoreHolder, objective);
			ScoreAccess newScore = scoreboard.getOrCreatePlayerScore(player, objective, true);
			newScore.display(oldScore.display());
			newScore.numberFormatOverride(oldScore.numberFormat());
			newScore.set(scoreInt);
		});
		scoreboard.resetAllPlayerScores(scoreHolder);
	}
}
