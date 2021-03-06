package org.empyrn.darkknight.gamelogic;

import java.io.Serializable;

/**
 * @author petero
 */
public final class Move implements Serializable {
	/**
	 * From square, 0-63.
	 */
	public final int from;

	/**
	 * To square, 0-63.
	 */
	public final int to;

	/**
	 * Promotion piece.
	 */
	public final int promoteTo;

	/**
	 * Create a move object.
	 */
	public Move(int from, int to, int promoteTo) {
		this.from = from;
		this.to = to;
		this.promoteTo = promoteTo;
	}

	public Move(Move m) {
		this.from = m.from;
		this.to = m.to;
		this.promoteTo = m.promoteTo;
	}

	@SuppressWarnings("RedundantIfStatement")
	@Override
	public boolean equals(Object o) {
		if ((o == null) || (o.getClass() != this.getClass())) {
			return false;
		}

		Move other = (Move) o;
		if (from != other.from) {
			return false;
		}

		if (to != other.to) {
			return false;
		}

		if (promoteTo != other.promoteTo) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		return (from * 64 + to) * 16 + promoteTo;
	}

	/**
	 * Useful for debugging.
	 */
	public final String toString() {
		return TextIO.moveToUCIString(this);
	}
}
