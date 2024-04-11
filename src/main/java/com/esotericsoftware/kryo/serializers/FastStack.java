package com.esotericsoftware.kryo.serializers;

import java.util.ArrayList;

/**
 * A fast, but simple stack implementation. Sadly, java's own stack
 * implementation synchronizes and as such is a bit slower.
 * 
 * Note that this implementation does not perform error checking, however, the
 * original implementation would also have thrown an exception in case this
 * implementation throws an exception. It's just that the exception text
 * differs.
 * 
 * @param <T> The elements of the stack
 * @author Marc Miltenberger
 */
public class FastStack<T> extends ArrayList<T> {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new stack
	 */
	public FastStack() {
	}

	/**
	 * Creates a new stack with the given initial size
	 * 
	 * @param initialSize the initial size
	 */
	public FastStack(int initialSize) {
		super(initialSize);
	}

	/**
	 * Returns the last item on the stack or throws an exception of there is none.
	 * 
	 * @return the last item on the stack
	 */
	public T peek() {
		return get(size() - 1);
	}

	/**
	 * Pushes an item onto the stack
	 * 
	 * @param t the item
	 */
	public void push(T t) {
		add(t);
	}

	/**
	 * Returns and removes the last item from the stack. Throws an exception of
	 * there is none.
	 * 
	 * @return the last item on the stack, which got pop-ed.
	 */
	public T pop() {
		return remove(size() - 1);
	}
}
