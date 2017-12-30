package me.arrdem;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An iterator which lets you peek the next element for LL(1)-ish usage.
 */
public class PeekIterator implements Iterator {
    private static Object invalid = new Object();

    private Iterator backing;
    private Object peeked = invalid;

    public PeekIterator(Iterator i) {
        this.backing = i;
    }

    public boolean hasNext() {
        if (this.peeked != this.invalid)
            return true;

        return this.backing.hasNext();
    }

    /**
     * Peeking several times in succession returns the same element.
     */
    public Object peek() throws NoSuchElementException {
        if (this.peeked != this.invalid) {
            return this.peeked;
        } else if (this.backing.hasNext()) {
            this.peeked = this.backing.next();
            return this.peeked;
        } else {
            throw new NoSuchElementException();
        }
    }

    public Object next() throws NoSuchElementException {
        try {
            if (this.peeked != this.invalid) {
                return this.peeked;
            } else {
                return this.backing.next();
            }
        } finally {
            this.peeked = this.invalid;
        }
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}
