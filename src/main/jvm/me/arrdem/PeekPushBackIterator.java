package me.arrdem;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An iterator which lets you peek, or push the next element for LL(1)-ish usage.
 */
public class PeekPushBackIterator extends PeekIterator {
    private static Object invalid = new Object();

    private Iterator backing;
    private Object peeked = invalid;
    private Object pushed = invalid;

    public PeekPushBackIterator(Iterator i) {
        super(i);
        this.backing = i;
    }

    public boolean hasNext() {
        if (this.pushed != this.invalid)
            return true;

        if (this.peeked != this.invalid)
            return true;

        return this.backing.hasNext();
    }

    /**
     * Peeking several times in succession returns the same element.
     */
    public Object peek() throws NoSuchElementException {
        if (this.pushed != this.invalid) {
            return this.pushed;
        } else if (this.peeked != this.invalid) {
            return this.peeked;
        } else if (this.backing.hasNext()) {
            this.peeked = this.backing.next();
            return this.peeked;
        } else {
            throw new NoSuchElementException();
        }
    }

    public Object next() throws NoSuchElementException {
        Object t = null;
        if (this.pushed != this.invalid) {
            t = this.pushed;
            this.pushed = this.invalid;
        } else if (this.peeked != this.invalid) {
            t = this.peeked;
            this.peeked = this.invalid;
        } else {
            t = this.backing.next();
        }

        return t;
    }

    /**
     * Remove is inherited
     */
}
