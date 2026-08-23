package io.github.neo236.packwarden.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Una votacion abierta sobre el reinicio.
 *
 * <p>Gana la mayoria simple de los votos emitidos. No se exige que vote todo el
 * mundo: quien no vota, no bloquea. Si nadie vota, el horario original se
 * mantiene, que es el comportamiento menos sorpresivo.
 */
public class VoteSession {

    public enum Choice {
        POSTPONE,
        NOW
    }

    public enum Outcome {
        POSTPONE,
        NOW,
        NO_QUORUM
    }

    private final Map<UUID, Choice> votes = new HashMap<>();
    private final long closesAtTick;
    private final boolean postponeAllowed;

    public VoteSession(long closesAtTick, boolean postponeAllowed) {
        this.closesAtTick = closesAtTick;
        this.postponeAllowed = postponeAllowed;
    }

    public boolean isPostponeAllowed() {
        return postponeAllowed;
    }

    public boolean isClosed(long currentTick) {
        return currentTick >= closesAtTick;
    }

    public long remainingSeconds(long currentTick) {
        return Math.max(0, (closesAtTick - currentTick) / 20);
    }

    /** Un jugador puede cambiar su voto mientras la votacion siga abierta. */
    public void cast(ServerPlayer player, Choice choice) {
        votes.put(player.getUUID(), choice);
    }

    public int count(Choice choice) {
        int total = 0;
        for (Choice value : votes.values()) {
            if (value == choice) {
                total++;
            }
        }
        return total;
    }

    public Outcome resolve() {
        int postpone = count(Choice.POSTPONE);
        int now = count(Choice.NOW);

        if (postpone == 0 && now == 0) {
            return Outcome.NO_QUORUM;
        }
        if (now > postpone) {
            return Outcome.NOW;
        }
        if (postpone > now && postponeAllowed) {
            return Outcome.POSTPONE;
        }
        // Empate, o mayoria por postergar cuando ya no quedan postergaciones.
        return Outcome.NO_QUORUM;
    }
}
