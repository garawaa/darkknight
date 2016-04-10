package org.empyrn.darkknight.gamelogic;

import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.util.Log;

import org.empyrn.darkknight.GUIInterface;
import org.empyrn.darkknight.GameMode;
import org.empyrn.darkknight.PGNOptions;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Set;

/**
 * Created by nick on 10/3/15.
 */
public abstract class AbstractGameController implements GameController {

	// use a weak reference to avoid potential memory leaks to activities
	private WeakReference<GUIInterface> mGuiInterface;

	private PgnToken.PgnTokenReceiver mPgnTokenReceiver;


	public final @Nullable PgnToken.PgnTokenReceiver getGameTextListener() {
		return mPgnTokenReceiver;
	}

	public final void setGameTextListener(@Nullable PgnToken.PgnTokenReceiver pgnTokenReceiver) {
		if (hasGame()) {
			throw new IllegalStateException("Cannot set a game text listener when a game has already started");
		}

		mPgnTokenReceiver = pgnTokenReceiver;
	}

	public @Nullable final GUIInterface getGui() {
		if (mGuiInterface == null) {
			return null;
		} else {
			return mGuiInterface.get();
		}
	}

	public final void setGui(@Nullable GUIInterface guiInterface) {
		this.mGuiInterface = new WeakReference<>(guiInterface);
	}

	public final boolean hasGame() {
		return getGame() != null;
	}

	@Override
	public boolean isGameActive() {
		return getGameMode() != GameMode.ANALYSIS && getGame() != null
				&& getGame().getGameStatus() == Game.Status.ALIVE;
	}

	public @Nullable abstract Game getGame();

	/**
	 * Move a piece from one square to another.
	 *
	 * @return True if the move was legal, false otherwise.
	 */
	protected boolean doMove(Move move) {
		if (getGame() == null) {
			throw new IllegalStateException("Game is not initialized");
		} else if (getGui() == null) {
			throw new IllegalStateException("GUI is not initialized");
		}

		Log.d(getClass().getSimpleName(), "Controller: playing move " + move);

		Position pos = getGame().currPos();
		Set<Move> moves = MoveGenerator.INSTANCE.generateLegalMoves(pos);
		int promoteTo = move.promoteTo;
		for (Move m : moves) {
			if ((m.from == move.from) && (m.to == move.to)) {
				if ((m.promoteTo != Piece.EMPTY) && (promoteTo == Piece.EMPTY)) {
					promoteMove = m;
					getGui().requestPromotePiece();
					return false;
				}

				if (m.promoteTo == promoteTo) {
					getGame().performMove(m);
					return true;
				}
			}
		}

		return false;
	}

	protected PGNOptions getPGNOptions() {
		if (getGameTextListener() == null) {
			return null;
		} else {
			return getGameTextListener().getPGNOptions();
		}
	}

	@Override
	public final byte[] getPersistableGameState() {
		if (getGame() == null) {
			throw new IllegalStateException("Game is not initialized");
		}

		return getGame().getTree().toByteArray();
	}

	/** Convert current game to PGN format. */
	public final String getPGN() {
		if (getGame() == null) {
			throw new IllegalStateException("Game is not initialized");
		}

		return getGame().getTree().toPGN(getPGNOptions());
	}

	/** True if human's turn to make a move. (True in analysis mode.) */
	public final boolean isPlayerTurn() {
		return getGame() != null && getGameMode() != null
					&& getGameMode().isPlayerTurn(getGame().currPos().whiteMove);
	}

	/** Return true if computer player is using CPU power. */
	public final boolean isOpponentThinking() {
		if (getGame() == null) {
			throw new IllegalStateException();
		}

		return getGame().getGameStatus() == Game.Status.ALIVE
				&& (getGameMode() == GameMode.ANALYSIS || !isPlayerTurn());
	}

	protected void updateMoveList() {
		if (getGameTextListener() == null) {
			return;
		}

		if (getGame() == null) {
			throw new IllegalStateException("Game is not initialized");
		}

		if (!getGameTextListener().isUpToDate()) {
			PGNOptions tmpOptions = new PGNOptions();
			tmpOptions.exp.variations = getPGNOptions().view.variations;
			tmpOptions.exp.comments = getPGNOptions().view.comments;
			tmpOptions.exp.nag = getPGNOptions().view.nag;
			tmpOptions.exp.playerAction = false;
			tmpOptions.exp.clockInfo = false;
			tmpOptions.exp.moveNrAfterNag = false;
			getGameTextListener().clear();
			getGame().getTree().pgnTreeWalker(tmpOptions, getGameTextListener());
		}

		getGameTextListener().setCurrent(getGame().getTree().currentNode);
	}

	protected @Deprecated abstract String getStatusText();

	protected void onMoveMade() {
		if (getGame() == null) {
			throw new IllegalStateException("Game is not initialized");
		} else if (getGui() == null) {
			throw new IllegalStateException("GUI is not initialized");
		}

		getGui().setStatusString(getStatusText());

		if (getGameMode() == GameMode.ANALYSIS) {
			onPositionChanged();
		} else {
			updateMoveList();

			if (getGame().getGameStatus() != Game.Status.ALIVE) {
				getGui().onGameOver(getGame().getGameStatus());
			} else {
				Move lastMove = getGame().getLastMove();
				if (lastMove != null) {
					getGui().onMoveMade(getGame().getLastMove());
				}
			}
		}
	}

	protected @Deprecated void onPositionChanged() {
		if (getGameMode() != GameMode.ANALYSIS) {
			return;
		}

		updateMoveList();

		if (getGame() == null) {
			throw new IllegalStateException("Game is not initialized");
		} else if (getGui() == null) {
			throw new IllegalStateException("GUI is not initialized");
		}

		getGui().setStatusString(getStatusText());

		StringBuilder sb = new StringBuilder();
		if (getGame().getTree().currentNode != getGame().getTree().rootNode) {
			getGame().getTree().goBack();
			Position pos = getGame().currPos();
			List<Move> prevVarList = getGame().getTree().variations();
			for (int i = 0; i < prevVarList.size(); i++) {
				if (i > 0) {
					sb.append(' ');
				}

				if (i == getGame().getTree().currentNode.defaultChild) {
					sb.append("<b>");
				}

				sb.append(TextIO.moveToString(pos, prevVarList.get(i), false));
				if (i == getGame().getTree().currentNode.defaultChild) {
					sb.append("</b>");
				}
			}
			getGame().getTree().goForward(-1);
		}

		getGui().onPositionChanged(getGame().currPos(), sb.toString(), getGame().getTree().variations());
	}


	private Move promoteMove;

	public final void setPromotionChoice(PromotionPiece promotionPiece) {
		if (getGame() == null) {
			throw new IllegalStateException("Game is not initialized");
		}

		final int choice = promotionPiece.ordinal();

		final boolean white = getGame().currPos().whiteMove;
		int promoteTo;
		switch (choice) {
			case 1:
				promoteTo = white ? Piece.WROOK : Piece.BROOK;
				break;
			case 2:
				promoteTo = white ? Piece.WBISHOP : Piece.BBISHOP;
				break;
			case 3:
				promoteTo = white ? Piece.WKNIGHT : Piece.BKNIGHT;
				break;
			default:
				promoteTo = white ? Piece.WQUEEN : Piece.BQUEEN;
				break;
		}

		promoteMove = new Move(promoteMove.from, promoteMove.to, promoteTo);
		Move m = promoteMove;
		promoteMove = null;
		tryPlayMove(m);
	}

	/**
	 * Help human to claim a draw by trying to find and execute a valid draw
	 * claim.
	 */
	public final boolean claimDrawIfPossible() {
		if (!findValidDrawClaim()) {
			return false;
		} else {
			onMoveMade();   // consider a draw to be a "move"
			return true;
		}
	}

	private boolean findValidDrawClaim() {
		if (getGame() == null) {
			throw new IllegalStateException("Game is not initialized");
		}

		if (getGame().getGameStatus() != Game.Status.ALIVE) {
			return true;
		}

		getGame().processString("draw accept");
		if (getGame().getGameStatus() != Game.Status.ALIVE) {
			return true;
		}

		getGame().processString("draw rep");
		if (getGame().getGameStatus() != Game.Status.ALIVE) {
			return true;
		}

		getGame().processString("draw 50");
		return getGame().getGameStatus() != Game.Status.ALIVE;
	}


	@Override
	public boolean canUndoMove() {
		return getGame() != null && getGame().getLastMove() != null;
	}

	@Override
	public boolean canRedoMove() {
		return getGame() != null && getGame().canRedoMove();
	}
}