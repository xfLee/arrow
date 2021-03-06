/*
 * The MIT License
 *
 * Copyright (c) 2016 Zhixun Tan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package arrow.runtime

import java.util
import java.util.concurrent.locks._

import arrow._

import scala.collection.mutable.ArrayBuffer

/**
  * Node[_, T] |> Node[T, _]
  *
  * +------------+  Push(T)  +------------+  Push(T)  +------------+
  * | Node[_, T] | --------> | Channel[T] | --------> | Node[T, _] |
  * +------------+           +------------+           +------------+
  *
  *
  * Node[_, R[T] ] |> Node[T, _]
  *
  * +----------------+  Push(R[T])  +------------+  Push(T)  +------------+
  * | Node[_, R[T] ] | -----------> | Channel[T] | --------> | Node[T, _] |
  * +----------------+              +------------+           +------------+
  *
  */

sealed trait ChannelIn[T] {
    def push(msg: R[T], id: Int): Unit
}

final case class ChannelInImpl[T](chan: Channel[T]) extends ChannelIn[T] {
    override def push(msg: R[T], id: Int): Unit = {
        chan.push(msg, id)
    }
}

final case class ChannelInRImpl[T, RT](chan: Channel[T])(implicit rt: RT <:< R[T]) extends ChannelIn[RT] {
    override def push(msg: R[RT], id: Int): Unit = {
        chan.push(msg match {
            case Value(value, _, _) =>
                rt(value)

            case Ignore() =>
                Ignore[T]()

            case Finish() =>
                Finish[T]()

            case Empty() =>
                Empty[T]()

            case Break() =>
                Break[T]()
        }, id)
    }
}

final class CircularDeque[T](val capacity: Int) {

    /**
      * <- [[popFront]] [[lastUsedSlot]]
      *                        |
      *                 +-----+----------+-----+
      *                 | ... | xxxxxxxx | ... |
      *                 +-----+----------+-----+
      *                                   |
      *                          [[nextAvailableSlot]] <- [[pushBack]]
      */

    private var _size: Int = 0
    private var nextAvailableSlot: Int = 0
    private var lastUsedSlot: Int = 0
    private val buf: ArrayBuffer[T] = new ArrayBuffer[T](capacity)

    def size: Int = this._size

    def isFull: Boolean = this.size == this.capacity

    def isEmpty: Boolean = this.size == 0

    def pushBack(value: T): Unit = {
        this.buf(nextAvailableSlot) = value

        this._size += 1
        this.nextAvailableSlot += 1
        this.nextAvailableSlot %= this.capacity
    }

    def pushFront(value: T): Unit = {
        this._size += 1
        lastUsedSlot += this.capacity - 1
        lastUsedSlot %= this.capacity

        this.buf(lastUsedSlot) = value
    }

    def popBack(): T = {
        this._size -= 1
        this.nextAvailableSlot += this.capacity - 1
        this.nextAvailableSlot %= this.capacity

        this.buf(this.nextAvailableSlot)
    }

    def popFront(): T = {
        val value = this.buf(this.lastUsedSlot)

        this._size -= 1
        lastUsedSlot += 1
        lastUsedSlot %= this.capacity

        value
    }

    def back: T = {
        val idx = (this.nextAvailableSlot + this.capacity - 1) % this.capacity
        this.buf(idx)
    }

    def front: T = {
        this.buf(this.lastUsedSlot)
    }
}

class Channel[T]
(private val recorder: Option[Recorder],
 private val replayer: Option[Replayer]) {

    val fuck = new util.ArrayDeque[Int](Runtime.BUF_SIZE)

    val deque = new util.ArrayDeque[Inputable[T]](Runtime.BUF_SIZE)
    private val lock = new ReentrantLock()
    private val notFull = this.lock.newCondition()
    private val notEmpty = this.lock.newCondition()

    def push(msg: R[T], id: Int): Unit = {
        // Before we do anything, acquire replayer lock.
        // In this way we can avoid deadlock.
        this.replayer.foreach(_.lock(id))
        try {
            msg match {
                case msg: Inputable[T] =>
                    this.lock.lock()
                    try {
                        while (this.deque.size() == Runtime.BUF_SIZE &&
                            !this.deque.peekLast().replaceable) {
                            this.notFull.await()
                        }

                        // When we can actually make progress,
                        // acquire recorder lock.
                        // In this way we can avoid deadlock.
                        this.recorder.foreach(_.lock(id))
                        try {
                            if (this.deque.size() != Runtime.BUF_SIZE) {
                                this.deque.offerLast(msg)
                            } else {
                                this.deque.removeLast()
                                this.deque.offerLast(msg)
                            }
                            Runtime.log.info(s"[$id] Pushed [$msg]")

                        } finally {
                            this.recorder.foreach(_.unlock(id))
                        }

                        this.notEmpty.signal()

                    } finally {
                        this.lock.unlock()
                    }

                case Ignore() =>
                // Ignore
            }



        } finally {
            this.replayer.foreach(_.unlock(id))
        }
    }

    def pull(id: Int): Inputable[T] = {
        // Before we do anything, acquire replayer lock
        // In this way we can avoid deadlock
        this.replayer.foreach(_.lock(id))
        try {
            this.lock.lock()
            try {
                while (this.deque.isEmpty) {
                    this.notEmpty.await()
                }

                val front = this.deque.peekFirst()
                // When we can actually make progress, acquire recorder lock.
                // In this way we can avoid deadlock.
                this.recorder.foreach(_.lock(id))
                try {
                    if (!front.reusable) {
                        this.deque.removeFirst()
                    }
                    Runtime.log.info(s"[$id] Pulled [$front]")
                } finally {
                    this.recorder.foreach(_.unlock(id))
                }

                this.notFull.signal()

                front

            } finally {
                this.lock.unlock()
            }

        } finally {
            this.replayer.foreach(_.unlock(id))
        }
    }
}
